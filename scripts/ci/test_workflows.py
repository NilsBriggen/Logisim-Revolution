"""Checks for release completeness and Gitea PR references."""

import os
import unittest
from unittest.mock import patch

import github_release
import pr_checks
import release


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
            {"RELEASE_ID": "12", "GITEA_RUN_ID": "34", "GITEA_RUN_ATTEMPT": "1"},
        )
        self.env.start()
        self.addCleanup(self.env.stop)

    def test_expected_assets_cover_all_five_native_targets(self):
        assets = release.expected_assets()
        self.assertEqual(len(assets), 10)
        name, version, _ = release.properties()
        self.assertIn(f"{name}_{version}_arm64.deb", assets)
        self.assertIn(f"{name}-{version}-aarch64.dmg", assets)

    @patch("release.api")
    def test_incomplete_draft_cannot_be_published(self, api):
        api.return_value = {
            "draft": True,
            "tag_name": release.release_tag(),
            "assets": [],
        }
        with self.assertRaisesRegex(ValueError, "Release incomplete"):
            release.publish()
        api.assert_called_once_with("releases/12")

    @patch("release.api")
    def test_complete_draft_is_published(self, api):
        api.side_effect = [
            {
                "draft": True,
                "tag_name": release.release_tag(),
                "assets": [{"name": name, "size": 1} for name in release.expected_assets()],
                "html_url": "https://git.example/release",
            },
            {},
        ]
        release.publish()
        api.assert_any_call("releases/12", "PATCH", {"draft": False})


if __name__ == "__main__":
    unittest.main()
