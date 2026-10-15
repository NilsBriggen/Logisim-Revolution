#!/usr/bin/env python3
"""Lock a merged PR and any issues it closed in this repository."""

import json
import os
import time
from pathlib import Path

from pr_checks import api_request, ticket_numbers


def lock_if_closed(number):
    repo = os.environ["GITHUB_REPOSITORY"]
    for attempt in range(3):
        issue = api_request(f"repos/{repo}/issues/{number}")
        if issue["state"] == "closed":
            break
        if attempt < 2:
            time.sleep(5)
    if issue["state"] != "closed":
        print(f"#{number} is still open; leaving it unlocked.")
        return
    if issue["locked"]:
        print(f"#{number} is already locked.")
        return
    api_request(
        f"repos/{repo}/issues/{number}/lock",
        method="PUT",
        data={"lock_reason": "resolved"},
    )
    print(f"Locked #{number}.")


def main():
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    request = event["pull_request"]
    if not request.get("merged"):
        print("PR was closed without merging; nothing to lock.")
        return
    lock_if_closed(request["number"])
    body = request.get("body") or ""
    for number in ticket_numbers(body, os.environ["GITHUB_SERVER_URL"], os.environ["GITHUB_REPOSITORY"]):
        lock_if_closed(number)


if __name__ == "__main__":
    main()
