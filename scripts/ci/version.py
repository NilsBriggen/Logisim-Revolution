#!/usr/bin/env python3
"""Work out the next release version (X.Y.Z) from the commits since the last vX.Y.Z tag.

X (major) when a commit message says "BREAKING CHANGE"; Z (patch) when every commit subject
starts with a fix verb; Y (minor) otherwise. The version in gradle.properties is a floor, so
raising it there (say to 6.0.0-dev) forces at least that release.
"""

import re
import subprocess
import sys
from pathlib import Path

TAG = re.compile(r"v(\d+)\.(\d+)\.(\d+)")
FIX = re.compile(r"(fix|fixes|fixed|correct|corrects|repair|repairs|revert|reverts)\b", re.I)
BUMPS = ("major", "minor", "patch")


def git(*args):
    return subprocess.check_output(["git", *args], text=True)


def last_tag():
    tags = [t for t in git("tag", "--merged", "HEAD").split() if TAG.fullmatch(t)]
    return max(tags, key=lambda t: tuple(map(int, TAG.fullmatch(t).groups())), default=None)


def floor_version():
    content = Path("gradle.properties").read_text(encoding="utf-8")
    version = re.search(r"^\s*version\s*=\s*(\d+)\.(\d+)\.(\d+)", content, re.M)
    return tuple(map(int, version.groups()))


def bump_for(messages):
    """messages: (subject, body) pairs of the first-parent commits since the last release."""
    if not messages:
        raise ValueError("No commits since the last release.")
    if any("BREAKING CHANGE" in subject + body for subject, body in messages):
        return "major"
    return "patch" if all(FIX.match(subject) for subject, _ in messages) else "minor"


def apply(version, bump):
    major, minor, patch = version
    return {"major": (major + 1, 0, 0), "minor": (major, minor + 1, 0), "patch": (major, minor, patch + 1)}[bump]


def next_version(bump="auto"):
    tag = last_tag()
    floor = floor_version()
    if tag is None:
        return floor
    log = git("log", "--first-parent", "--format=%s%x00%b%x1e", f"{tag}..HEAD")
    messages = [tuple(entry.strip("\n").split("\0", 1)) for entry in log.split("\x1e") if entry.strip()]
    version = apply(tuple(map(int, TAG.fullmatch(tag).groups())), bump_for(messages) if bump == "auto" else bump)
    return max(version, floor)


if __name__ == "__main__":
    try:
        bump = sys.argv[1] if len(sys.argv) > 1 else "auto"
        if bump not in ("auto", *BUMPS):
            raise ValueError("usage: version.py [auto|major|minor|patch]")
        print(".".join(map(str, next_version(bump))))
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"Version failed: {error}", file=sys.stderr)
        sys.exit(1)
