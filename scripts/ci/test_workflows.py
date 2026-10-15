"""Checks for release completeness and pull request references."""

import tempfile
import unittest
from pathlib import Path

import pr_checks
import release
import version


class PullRequestChecksTest(unittest.TestCase):
    def test_ticket_references_are_scoped_to_this_repository(self):
        body = (
            "Closes #42\nFixes https://github.com/NilsBriggen/Logisim-Revolution/issues/7\n"
            "Closes https://other.example/other/repo/issues/99"
        )
        self.assertEqual(
            pr_checks.ticket_numbers(body, "https://github.com", "NilsBriggen/Logisim-Revolution"),
            [7, 42],
        )

    def test_documentation_does_not_require_changelog(self):
        self.assertFalse(pr_checks.needs_changelog("src/main/resources/doc/en/guide.html"))
        self.assertTrue(pr_checks.needs_changelog("src/main/java/com/cburch/logisim/Main.java"))


class ReleaseChecksTest(unittest.TestCase):
    def test_expected_assets_cover_all_five_native_targets(self):
        assets = release.expected_assets("5.1.0")
        self.assertEqual(len(assets), 10)
        self.assertIn("logisim-revolution_5.1.0_arm64.deb", assets)
        self.assertIn("logisim-revolution-5.1.0-aarch64.dmg", assets)
        self.assertIn("logisim-revolution-5.1.0-amd64.msi", assets)

    def write(self, directory, names, size=1):
        for name in names:
            (Path(directory) / name).write_bytes(b"x" * size)

    def test_complete_directory_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            self.write(directory, release.expected_assets("5.1.0"))
            release.verify(directory, "5.1.0")

    def test_missing_empty_or_unexpected_files_fail(self):
        expected = sorted(release.expected_assets("5.1.0"))
        for mutate in (
            lambda d: (Path(d) / expected[0]).unlink(),
            lambda d: (Path(d) / expected[0]).write_bytes(b""),
            lambda d: self.write(d, ["stray.txt"]),
        ):
            with tempfile.TemporaryDirectory() as directory:
                self.write(directory, expected)
                mutate(directory)
                with self.assertRaises(ValueError):
                    release.verify(directory, "5.1.0")


class VersionTest(unittest.TestCase):
    def test_fix_only_commits_are_a_patch(self):
        self.assertEqual(version.bump_for([("Fix the clock", ""), ("Fixed a typo", "")]), "patch")

    def test_any_other_commit_is_a_minor_update(self):
        self.assertEqual(version.bump_for([("Fix the clock", ""), ("Add a TTL chip", "")]), "minor")
        self.assertEqual(version.bump_for([("Prefix the labels", "")]), "minor")

    def test_breaking_change_is_a_major_revision(self):
        self.assertEqual(version.bump_for([("Fix x", "BREAKING CHANGE: new file format")]), "major")

    def test_bumps_reset_lower_parts(self):
        self.assertEqual(version.apply((5, 2, 3), "major"), (6, 0, 0))
        self.assertEqual(version.apply((5, 2, 3), "minor"), (5, 3, 0))
        self.assertEqual(version.apply((5, 2, 3), "patch"), (5, 2, 4))

    def test_nothing_to_release_fails(self):
        with self.assertRaises(ValueError):
            version.bump_for([])


if __name__ == "__main__":
    unittest.main()
