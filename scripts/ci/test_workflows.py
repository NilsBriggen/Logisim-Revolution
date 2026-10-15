"""Checks for release completeness and pull request references."""

import tempfile
import unittest
from pathlib import Path

import pr_checks
import release


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
        assets = release.expected_assets()
        self.assertEqual(len(assets), 10)
        name, version, _ = release.properties()
        self.assertIn(f"{name}_{version}_arm64.deb", assets)
        self.assertIn(f"{name}-{version}-aarch64.dmg", assets)

    def write(self, directory, names, size=1):
        for name in names:
            (Path(directory) / name).write_bytes(b"x" * size)

    def test_complete_directory_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            self.write(directory, release.expected_assets())
            release.verify(directory)

    def test_missing_empty_or_unexpected_files_fail(self):
        expected = sorted(release.expected_assets())
        for mutate in (
            lambda d: (Path(d) / expected[0]).unlink(),
            lambda d: (Path(d) / expected[0]).write_bytes(b""),
            lambda d: self.write(d, ["stray.txt"]),
        ):
            with tempfile.TemporaryDirectory() as directory:
                self.write(directory, expected)
                mutate(directory)
                with self.assertRaises(ValueError):
                    release.verify(directory)


if __name__ == "__main__":
    unittest.main()
