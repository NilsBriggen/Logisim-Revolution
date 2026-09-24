#!/usr/bin/env python3
"""Gitea pull-request policy checks for Logisim Revolution."""

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path


CODE_PATHS = (
    "src/",
    "buildSrc/",
    "gradle/",
    "support/",
    "snap/",
    "scripts/",
    ".gitea/",
)
CODE_FILES = {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"}
DOC_ONLY_PATHS = ("src/main/resources/doc/", "src/main/resources/resources/logisim/strings/")
CLOSING_KEYWORD = re.compile(r"\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?[ \t]+", re.I)


def run_git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def changed_files(base):
    return set(run_git("diff", "--name-only", f"{base}...HEAD").splitlines())


def needs_changelog(path):
    if path.startswith(DOC_ONLY_PATHS):
        return False
    return path.startswith(CODE_PATHS) or path in CODE_FILES


def added_changelog_lines(base):
    diff = run_git("diff", "--unified=0", f"{base}...HEAD", "--", "CHANGES.md")
    line_number = 0
    for line in diff.splitlines():
        if line.startswith("@@"):
            match = re.search(r"\+(\d+)", line)
            if match:
                line_number = int(match.group(1))
        elif line.startswith("+") and not line.startswith("+++"):
            if line[1:].strip():
                yield line_number
            line_number += 1
        elif line.startswith(" "):
            line_number += 1


def check_changelog(base, author, body):
    if "NO_CHANGELOG_ENTRY" in body:
        print("Changelog check waived by PR description.")
        return
    if not any(map(needs_changelog, changed_files(base))):
        print("No code or build infrastructure changed; changelog entry not required.")
        return
    lines = Path("CHANGES.md").read_text(encoding="utf-8").splitlines()
    sections = [index for index, line in enumerate(lines, 1) if line.startswith("* ")]
    if not sections or not lines[sections[0] - 1].startswith("* @dev"):
        raise ValueError("CHANGES.md needs a topmost @dev section.")
    end = sections[1] if len(sections) > 1 else len(lines) + 1
    entries = [lines[number - 1] for number in added_changelog_lines(base) if sections[0] < number < end]
    if not entries:
        raise ValueError("Add a line to the topmost @dev section of CHANGES.md, or use NO_CHANGELOG_ENTRY.")
    if "NO_CHANGELOG_AUTHOR_CREDIT" in body or os.getenv("NO_CHANGELOG_AUTHOR_CREDIT", "").lower() not in ("", "false", "no", "0"):
        print("Changelog entry found; author credit waived.")
        return
    if not any(re.search(rf"@{re.escape(author)}(?![A-Za-z0-9-])", line, re.I) for line in entries):
        raise ValueError(f"New @dev lines must credit @{author}, or use NO_CHANGELOG_AUTHOR_CREDIT.")
    print(f"Changelog entry credits @{author}.")


def ticket_numbers(body, server, repo):
    prefix = re.escape(f"{server.rstrip('/')}/{repo}/issues/")
    refs = re.compile(rf"(?:#|{prefix})(\d+)", re.I)
    numbers = set()
    for keyword in CLOSING_KEYWORD.finditer(body):
        match = refs.match(body, keyword.end())
        if match:
            numbers.add(int(match.group(1)))
    return sorted(numbers)


def api_request(path, *, method="GET", data=None):
    token = os.environ["GITEA_TOKEN"]
    base = os.environ["GITEA_API_URL"].rstrip("/")
    request = urllib.request.Request(
        f"{base}/{path.lstrip('/')}",
        data=json.dumps(data).encode() if data is not None else None,
        headers={"Authorization": f"token {token}", "Content-Type": "application/json"},
        method=method,
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        content = response.read()
        return json.loads(content) if content else None


def check_tickets(body, author, server, repo):
    if "NO_TICKET" in body or author == "dependabot[bot]":
        print("Ticket check waived.")
        return
    numbers = ticket_numbers(body, server, repo)
    if not numbers:
        raise ValueError("PR description needs an open issue reference such as 'Closes #123', or NO_TICKET.")
    for number in numbers:
        try:
            issue = api_request(f"repos/{repo}/issues/{number}")
        except urllib.error.HTTPError as error:
            if error.code == 404:
                raise ValueError(f"Issue #{number} does not exist in {repo}.") from error
            raise
        if issue.get("pull_request"):
            raise ValueError(f"#{number} is a pull request, not an issue.")
        if issue.get("state") != "open":
            raise ValueError(f"Issue #{number} is not open.")
        print(f"#{number} is open: {issue.get('title', '')}")


def main():
    event = json.loads(Path(os.environ["GITEA_EVENT_PATH"]).read_text(encoding="utf-8"))
    request = event["pull_request"]
    base = request["base"]["sha"]
    author = request["user"]["login"]
    body = request.get("body") or ""
    check_changelog(base, author, body)
    check_tickets(body, author, os.environ["GITEA_SERVER_URL"], os.environ["GITEA_REPOSITORY"])


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, urllib.error.URLError, subprocess.CalledProcessError) as error:
        print(f"PR policy failed: {error}", file=sys.stderr)
        sys.exit(1)
