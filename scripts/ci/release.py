#!/usr/bin/env python3
"""Create and publish a complete manual Gitea release."""

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path


def api(path, method="GET", data=None):
    base = os.environ["GITEA_API_URL"].rstrip("/")
    request = urllib.request.Request(
        f"{base}/repos/{os.environ['GITEA_REPOSITORY']}/{path}",
        data=json.dumps(data).encode() if data is not None else None,
        headers={
            "Authorization": f"token {os.environ['GITEA_TOKEN']}",
            "Content-Type": "application/json",
        },
        method=method,
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        content = response.read()
        return json.loads(content) if content else None


def release_tag():
    return f"build-{os.environ['GITEA_RUN_ID']}-attempt-{os.environ['GITEA_RUN_ATTEMPT']}"


def prepare():
    tag = release_tag()
    sha = os.environ["GITEA_SHA"]
    body = (
        f"Manual build of `{sha}`. Native packages: Linux x86_64 and ARM64, "
        "Windows x86_64, macOS Intel and Apple Silicon. The portable JAR needs Java 21+. "
        "This release is published only when every target succeeds."
    )
    try:
        release = api(f"releases/tags/{tag}")
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        release = api(
            "releases",
            "POST",
            {
                "tag_name": tag,
                "target_commitish": sha,
                "name": f"Logisim Revolution build {sha[:8]}",
                "body": body,
                "draft": True,
                "prerelease": True,
            },
        )
    if not release["draft"]:
        raise ValueError(f"Release {tag} is already published.")
    if release["target_commitish"] != sha:
        raise ValueError(f"Release {tag} points to a different commit.")
    release_id = release["id"]
    with open(os.environ["GITEA_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"release_id={release_id}\n")
    print(f"Prepared draft release {tag} (ID {release_id}) for {sha}.")


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


def publish():
    release_id = os.environ["RELEASE_ID"]
    release = api(f"releases/{release_id}")
    if not release["draft"]:
        raise ValueError("The release is already public.")
    if release["tag_name"] != release_tag():
        raise ValueError("Release ID does not belong to this workflow run.")
    assets = {asset["name"]: asset["size"] for asset in release["assets"]}
    missing = sorted(expected_assets() - assets.keys())
    empty = sorted(name for name in expected_assets() if name in assets and assets[name] <= 0)
    if missing or empty:
        raise ValueError(f"Release incomplete; missing: {missing}; empty: {empty}")
    api(f"releases/{release_id}", "PATCH", {"draft": False})
    print(f"Published {release['html_url']} with {len(expected_assets())} verified attachments.")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["prepare"]:
            prepare()
        elif sys.argv[1:] == ["publish"]:
            publish()
        else:
            raise ValueError("usage: release.py prepare|publish")
    except (ValueError, KeyError, urllib.error.URLError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        sys.exit(1)
