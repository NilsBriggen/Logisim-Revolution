#!/usr/bin/env python3
"""Lint Markdown changed by the current Gitea push or pull request."""

import json
import os
import subprocess
from pathlib import Path


def main():
    event = json.loads(Path(os.environ["GITEA_EVENT_PATH"]).read_text(encoding="utf-8"))
    if os.environ["GITEA_EVENT_NAME"] == "pull_request":
        base = event["pull_request"]["base"]["sha"]
        revision_range = f"{base}...HEAD"
    else:
        base = event.get("before", "")
        revision_range = f"{base}..HEAD" if base and set(base) != {"0"} else "HEAD^..HEAD"
    files = subprocess.check_output(
        ["git", "diff", "--name-only", revision_range], text=True
    ).splitlines()
    markdown = [
        name for name in files
        if name.endswith(".md") and name != "LICENSE.md"
        and not name.startswith("docs/qa/") and Path(name).is_file()
    ]
    if not markdown:
        print("No maintained Markdown files changed.")
        return
    print("Linting:", ", ".join(markdown), flush=True)
    subprocess.run(
        ["npx", "--yes", "markdownlint-cli@0.49.1", "--config", ".markdownlint.yaml", *markdown],
        check=True,
    )


if __name__ == "__main__":
    main()
