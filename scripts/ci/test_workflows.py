"""Checks for release completeness and Gitea PR references."""

import os
import io
import json
import re
import subprocess
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import call, patch

import github_release
import pr_checks
import release

SOURCE_SHA = "a" * 40
SOURCE_TREE = "b" * 40
MIRROR_SHA = "c" * 40
TAG = "build-34-attempt-1"


class PullRequestChecksTest(unittest.TestCase):
    def test_ticket_references_are_scoped_to_this_gitea_repository(self):
        body = (
            "Closes #42\nFixes https://git.briggen.dev/NilsBriggen/Logisim-Revolution/issues/7\n"
            "Closes https://other.example/other/repo/issues/99"
        )
        self.assertEqual(
            pr_checks.ticket_numbers(
                body,
                "https://git.briggen.dev",
                "NilsBriggen/Logisim-Revolution",
            ),
            [7, 42],
        )

    def test_documentation_does_not_require_changelog(self):
        self.assertFalse(pr_checks.needs_changelog("src/main/resources/doc/en/guide.html"))
        self.assertTrue(pr_checks.needs_changelog("src/main/java/com/cburch/logisim/Main.java"))


class GitHubArtifactChecksTest(unittest.TestCase):
    def test_artifact_contents_match_each_architecture(self):
        self.assertTrue(github_release.valid_names("windows-amd64", ["app.msi", "app.zip"]))
        self.assertTrue(github_release.valid_names("macos-amd64", ["app-x86_64.dmg"]))
        self.assertTrue(github_release.valid_names("macos-arm64", ["app-aarch64.dmg"]))
        self.assertFalse(github_release.valid_names("macos-amd64", ["app-aarch64.dmg"]))
        self.assertFalse(github_release.valid_names("windows-amd64", ["app.msi"]))


class ReleaseChecksTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(
            os.environ,
            {"RELEASE_ID": "12", "RELEASE_TAG": TAG, "GITEA_SHA": SOURCE_SHA,
             "GITEA_RUN_ID": "34", "GITEA_RUN_ATTEMPT": "1"},
        )
        self.env.start()
        self.addCleanup(self.env.stop)
        identity = patch("release.source_identity", return_value=(SOURCE_SHA, SOURCE_TREE))
        identity.start()
        self.addCleanup(identity.stop)

    def draft(self, **changes):
        draft = {
            "id": 12,
            "draft": True,
            "tag_name": TAG,
            "target_commitish": SOURCE_SHA,
            "assets": [{"name": name, "size": 1} for name in release.expected_assets()],
            "html_url": "https://git.example/release",
        }
        draft.update(changes)
        return draft

    def api_responses(self, draft=None, tag_sha=SOURCE_SHA):
        return [draft or self.draft(), {"commit": {"sha": tag_sha}}, {}]

    def test_expected_assets_cover_all_five_native_targets(self):
        assets = release.expected_assets()
        self.assertEqual(len(assets), 10)
        name, version, _ = release.properties()
        self.assertIn(f"{name}_{version}_arm64.deb", assets)
        self.assertIn(f"{name}-{version}-aarch64.dmg", assets)

    @patch("release.api")
    def test_incomplete_draft_cannot_be_published(self, api):
        api.side_effect = self.api_responses(self.draft(assets=[]))
        with self.assertRaisesRegex(ValueError, "Release incomplete"):
            release.publish()
        self.assertFalse(any(c.args[1:2] == ("PATCH",) for c in api.call_args_list))

    @patch("release.api")
    def test_complete_draft_is_published(self, api):
        api.side_effect = self.api_responses()
        release.publish()
        api.assert_any_call("releases/12", "PATCH", {"draft": False})


    @patch("release.api")
    def test_partial_retry_uses_prepared_tag_despite_new_attempt(self, api):
        os.environ["GITEA_RUN_ATTEMPT"] = "2"
        api.side_effect = self.api_responses()
        release.publish()
        api.assert_any_call("releases/12", "PATCH", {"draft": False})

    @patch("release.api")
    def test_prepare_emits_the_draft_and_source_identity(self, api):
        api.side_effect = self.api_responses()
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "output"
            with patch.dict(os.environ, {"GITEA_OUTPUT": str(output)}):
                release.prepare()
            self.assertEqual(dict(line.split("=", 1) for line in output.read_text().splitlines()), {
                "release_id": "12", "release_tag": TAG,
                "source_sha": SOURCE_SHA, "source_tree": SOURCE_TREE,
            })

    @patch("release.api")
    def test_publish_rejects_wrong_target_or_prepared_tag_or_public_release(self, api):
        for changes in ({"target_commitish": MIRROR_SHA}, {"tag_name": "other"}, {"draft": False}):
            with self.subTest(changes=changes):
                api.reset_mock()
                api.side_effect = self.api_responses(self.draft(**changes))
                with self.assertRaises(ValueError):
                    release.publish()
                api.assert_called_once_with("releases/12")

    @patch("release.api")
    def test_publish_rejects_existing_tag_at_a_different_commit(self, api):
        api.side_effect = self.api_responses(tag_sha=MIRROR_SHA)
        with self.assertRaisesRegex(ValueError, "tag points to a different"):
            release.publish()
        self.assertEqual(api.call_count, 2)

    @patch("release.api")
    def test_draft_can_publish_before_gitea_creates_the_tag(self, api):
        api.side_effect = [self.draft(), urllib.error.HTTPError("", 404, "missing", {}, None), {}]
        release.publish()
        api.assert_any_call("releases/12", "PATCH", {"draft": False})

    @patch("release.api")
    def test_empty_duplicate_and_unexpected_assets_block_publication(self, api):
        assets = self.draft()["assets"]
        for invalid in ([{**asset, "size": 0} for asset in assets], assets + [assets[0]],
                        assets + [{"name": "unexpected.txt", "size": 1}]):
            with self.subTest(assets=invalid):
                api.reset_mock()
                api.side_effect = self.api_responses(self.draft(assets=invalid))
                with self.assertRaises(ValueError):
                    release.publish()
                self.assertEqual(api.call_count, 2)

    @patch("release.urllib.request.urlopen")
    @patch("release.api")
    def test_retry_replaces_only_its_attachment_in_a_verified_draft(self, api, urlopen):
        name = sorted(release.expected_assets())[0]
        api.side_effect = self.api_responses(self.draft(assets=[
            {"id": 7, "name": name, "size": 1},
            {"id": 8, "name": "another-platform", "size": 1},
        ]))
        urlopen.return_value = io.BytesIO(json.dumps({"name": name, "size": 3}).encode())
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {
            "GITEA_API_URL": "https://git.example/api/v1", "GITEA_REPOSITORY": "owner/repo",
            "GITEA_TOKEN": "test-token",
        }):
            path = Path(temporary) / name
            path.write_bytes(b"jar")
            release.upload(path)
        api.assert_has_calls([call("releases/12"), call(f"tags/{TAG}"),
                              call("releases/12/assets/7", "DELETE")])
        self.assertEqual(api.call_count, 3)
        self.assertEqual(urlopen.call_count, 1)


class SourceIdentityTest(unittest.TestCase):
    @patch("release.subprocess.check_output")
    def test_canonical_commit_must_match_checkout(self, check_output):
        with patch.dict(os.environ, {"GITEA_SHA": SOURCE_SHA}):
            check_output.side_effect = [SOURCE_SHA + "\n", SOURCE_TREE + "\n"]
            self.assertEqual(release.source_identity(), (SOURCE_SHA, SOURCE_TREE))
            check_output.side_effect = [MIRROR_SHA]
            with self.assertRaisesRegex(ValueError, "Checkout does not match"):
                release.source_identity()
        with patch.dict(os.environ, {"GITEA_SHA": "main"}):
            with self.assertRaisesRegex(ValueError, "full commit SHA"):
                release.source_identity()


class GitHubSnapshotTest(unittest.TestCase):
    def setUp(self):
        env = patch.dict(os.environ, {"RELEASE_TAG": TAG}, clear=True)
        env.start()
        self.addCleanup(env.stop)
        identity = patch("release.source_identity", return_value=(SOURCE_SHA, SOURCE_TREE))
        identity.start()
        self.addCleanup(identity.stop)

    @patch("github_release.github_api")
    def test_snapshot_tree_is_verified(self, api):
        api.side_effect = [{"object": {"type": "commit", "sha": MIRROR_SHA}},
                           {"tree": {"sha": SOURCE_TREE}}]
        self.assertEqual(github_release.snapshot_commit(TAG, SOURCE_TREE), MIRROR_SHA)
        api.side_effect = [{"object": {"type": "commit", "sha": MIRROR_SHA}},
                           {"tree": {"sha": "d" * 40}}]
        with self.assertRaisesRegex(ValueError, "source tree"):
            github_release.snapshot_commit(TAG, SOURCE_TREE)

    @patch("github_release.github_api")
    def test_collection_requires_the_immutable_snapshot_commit(self, api):
        run = {"id": 1, "display_title": f"Gitea build {TAG}", "head_sha": MIRROR_SHA}
        api.return_value = {"workflow_runs": [run]}
        self.assertEqual(github_release.matching_run(TAG, MIRROR_SHA), run)
        api.return_value = {"workflow_runs": [{**run, "head_sha": SOURCE_SHA}]}
        with self.assertRaisesRegex(ValueError, "immutable snapshot"):
            github_release.matching_run(TAG, MIRROR_SHA)

    @patch("github_release.github_api")
    @patch("github_release.matching_run", return_value=None)
    @patch("github_release.mirror_snapshot", return_value=MIRROR_SHA)
    @patch("github_release.snapshot_commit")
    def test_new_dispatch_uses_immutable_tag_and_canonical_source(self, snapshot, mirror, matching, api):
        snapshot.side_effect = [urllib.error.HTTPError("", 404, "missing", {}, None), MIRROR_SHA]
        github_release.mirror_and_dispatch()
        mirror.assert_called_once_with(SOURCE_SHA, SOURCE_TREE, f"gitea-{TAG}")
        api.assert_called_once_with("actions/workflows/platforms.yml/dispatches", "POST", {
            "ref": f"gitea-{TAG}",
            "inputs": {"source_sha": SOURCE_SHA, "source_tree": SOURCE_TREE, "release_tag": TAG},
        })

    @patch("github_release.github_api")
    @patch("github_release.matching_run", return_value={"id": 7})
    @patch("github_release.mirror_snapshot")
    @patch("github_release.snapshot_commit", return_value=MIRROR_SHA)
    def test_dispatch_retry_reuses_existing_snapshot_and_run(self, snapshot, mirror, matching, api):
        github_release.mirror_and_dispatch()
        mirror.assert_not_called()
        api.assert_not_called()

    @patch("github_release.github_api")
    @patch("github_release.mirror_snapshot")
    @patch("github_release.snapshot_commit", side_effect=ValueError("wrong source tree"))
    def test_conflicting_snapshot_is_never_overwritten(self, snapshot, mirror, api):
        with self.assertRaisesRegex(ValueError, "wrong source tree"):
            github_release.mirror_and_dispatch()
        mirror.assert_not_called()
        api.assert_not_called()

    @patch("github_release.subprocess.run")
    @patch("github_release.git_output")
    @patch("github_release.git")
    def test_mirror_keeps_independent_history_and_pushes_atomically_without_force(self, git, output, run):
        def fake_git(*args, **kwargs):
            if args[0] == "clone":
                (Path(args[-1]) / ".git").mkdir(parents=True)
        git.side_effect = fake_git
        output.side_effect = [SOURCE_TREE, "Contributor", "author@example.test", MIRROR_SHA]
        run.return_value = subprocess.CompletedProcess([], 1)
        self.assertEqual(github_release.mirror_snapshot(SOURCE_SHA, SOURCE_TREE, f"gitea-{TAG}"),
                         MIRROR_SHA)
        commands = [c.args for c in git.call_args_list]
        clone = commands[0]
        self.assertEqual(clone[0], "clone")
        self.assertIn("--depth=1", clone)
        self.assertIn("--branch=main", clone)
        self.assertIn(f"https://github.com/{github_release.GITHUB_REPOSITORY}.git", clone)
        push = next(args for args in commands if "push" in args)
        self.assertIn("--atomic", push)
        self.assertEqual(push[-2:], ("HEAD:refs/heads/main", f"HEAD:refs/tags/gitea-{TAG}"))
        self.assertFalse(any("force" in arg or arg.startswith("+") for arg in push))
        archive = run.call_args_list[0].args[0]
        self.assertEqual(archive, ["git", "archive", "--format=tar", SOURCE_SHA])


class WorkflowPolicyTest(unittest.TestCase):
    @staticmethod
    def job(path, name):
        text = Path(path).read_text()
        match = re.search(rf"^  {name}:\n(.*?)(?=^  \w+:\n|\Z)", text, re.M | re.S)
        if match is None:
            raise AssertionError(f"Missing job {name} in {path}")
        return match.group(1)

    def test_preparation_is_serialized_and_exports_identity(self):
        prepare = self.job(".gitea/workflows/release.yml", "prepare")
        self.assertIn("concurrency:\n      group: logisim-revolution-github-mirror\n"
                      "      cancel-in-progress: false", prepare)
        for name in ("release_id", "release_tag", "source_sha", "source_tree"):
            self.assertIn(f"{name}: ${{{{ steps.draft.outputs.{name} }}}}", prepare)
        self.assertIn("RELEASE_TAG: ${{ steps.draft.outputs.release_tag }}", prepare)

    def test_all_consumers_use_prepared_identity_and_publication_needs_all_jobs(self):
        for name in ("linux_amd64", "linux_arm64", "collect_github", "publish"):
            job = self.job(".gitea/workflows/release.yml", name)
            self.assertIn("RELEASE_TAG: ${{ needs.prepare.outputs.release_tag }}", job)
            self.assertIn("RELEASE_ID: ${{ needs.prepare.outputs.release_id }}", job)
            self.assertIn("GITEA_SHA: ${{ gitea.sha }}", job)
            self.assertNotIn("run_attempt", job)
        publish = self.job(".gitea/workflows/release.yml", "publish")
        self.assertIn("needs: [prepare, linux_amd64, linux_arm64, collect_github]", publish)
        self.assertNotIn("always()", publish)

    def test_every_native_build_receives_canonical_provenance(self):
        for name in ("linux_amd64", "linux_arm64"):
            job = self.job(".gitea/workflows/release.yml", name)
            for suffix, output in (("SHA", "source_sha"), ("TREE", "source_tree")):
                self.assertIn(f"LOGISIM_SOURCE_{suffix}: ${{{{ needs.prepare.outputs.{output} }}}}", job)
        for name in ("windows_amd64", "macos_amd64", "macos_arm64"):
            job = self.job(".github/workflows/platforms.yml", name)
            self.assertIn("ref: ${{ github.sha }}", job)
            self.assertIn("HEAD^{tree}", job)
            for suffix, input_name in (("SHA", "source_sha"), ("TREE", "source_tree")):
                self.assertIn(f"LOGISIM_SOURCE_{suffix}: ${{{{ inputs.{input_name} }}}}", job)


if __name__ == "__main__":
    unittest.main()
