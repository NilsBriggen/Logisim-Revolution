#!/usr/bin/env python3
"""Summarize the inherited translation warnings without flooding Actions logs."""

import re
import subprocess
from pathlib import Path


def main():
    root = Path("src/main/resources/resources/logisim")
    settings = (root / "settings.properties").read_text(encoding="utf-8")
    locales = next(line.split("=", 1)[1].strip() for line in settings.splitlines() if line.startswith("locales"))
    bundles = sorted(
        base for directory in (root / "strings").iterdir()
        if directory.is_dir() and (base := directory / f"{directory.name}.properties").is_file()
    )
    failures = []
    for base in bundles:
        result = subprocess.run(
            ["trans-tool", "-l", locales, "-ls", "en", "-b", str(base)],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode:
            failures.append((base, result.stdout + result.stderr))
    print(f"Checked {len(bundles)} localization bundles; {len(failures)} reported existing issues.")
    for base, report in failures:
        first = re.sub(r"\x1b\[[0-9;]*m", "", report).splitlines()[:3]
        print(f"{base}: {' | '.join(first)}")
    if failures:
        print("Translation lint remains advisory until inherited bundle issues are resolved.")


if __name__ == "__main__":
    main()
