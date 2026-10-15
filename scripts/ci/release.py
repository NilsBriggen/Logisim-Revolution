#!/usr/bin/env python3
"""Check that a directory holds exactly the files of a complete release."""

import re
import sys
from pathlib import Path


def properties():
    content = Path("gradle.properties").read_text(encoding="utf-8")
    values = dict(re.findall(r"^\s*(name|version)\s*=\s*(\S+)\s*$", content, re.M))
    return values["name"], values["version"].replace("-", ""), values["version"].split("-")[0]


def expected_assets():
    name, version, short_version = properties()
    return {
        f"{name}-{version}-all.jar",
        f"{name}-{version}-src.jar",
        f"{name}_{version}_amd64.deb",
        f"{name}_{version}_arm64.deb",
        f"{name}-{version}-1.x86_64.rpm",
        f"{name}-{version}-1.aarch64.rpm",
        f"{name}-{short_version}-amd64.msi",
        f"{name}-{version}-windows-amd64.zip",
        f"{name}-{version}-x86_64.dmg",
        f"{name}-{version}-aarch64.dmg",
    }


def verify(directory):
    files = {path.name: path.stat().st_size for path in Path(directory).iterdir() if path.is_file()}
    expected = expected_assets()
    missing = sorted(expected - files.keys())
    unexpected = sorted(files.keys() - expected)
    empty = sorted(name for name in expected & files.keys() if files[name] <= 0)
    if missing or unexpected or empty:
        raise ValueError(f"Release incomplete; missing: {missing}; unexpected: {unexpected}; empty: {empty}")
    print(f"All {len(expected)} release files present.")


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3 or sys.argv[1] != "verify":
            raise ValueError("usage: release.py verify DIRECTORY")
        verify(sys.argv[2])
    except (ValueError, KeyError, OSError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        sys.exit(1)
