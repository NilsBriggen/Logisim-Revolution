#!/usr/bin/env python3
"""Create and publish a complete manual Gitea release."""

import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
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


def source_identity():
    sha = os.environ["GITEA_SHA"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("GITEA_SHA must be a full commit SHA.")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if head != sha:
        raise ValueError("Checkout does not match the canonical Gitea commit.")
    tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], text=True).strip()
    return sha, tree


def validate_target(release, sha):
    if release["target_commitish"] != sha:
        raise ValueError("Release points to a different Gitea commit.")
    # Gitea may defer creating a new tag until the draft is published. If a tag
    # already exists, target_commitish alone does not establish its destination.
    try:
        tag = api(f"tags/{release['tag_name']}")
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        error.close()
    else:
        if tag["commit"]["sha"] != sha:
            raise ValueError("Release tag points to a different Gitea commit.")


def prepared_release():
    sha, _ = source_identity()
    release = api(f"releases/{os.environ['RELEASE_ID']}")
    if not release["draft"]:
        raise ValueError("The release is already public.")
    if release["tag_name"] != os.environ["RELEASE_TAG"]:
        raise ValueError("Release ID does not match the prepared release tag.")
    validate_target(release, sha)
    return release


def prepare():
    tag = release_tag()
    sha, tree = source_identity()
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
        error.close()
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
    if release["tag_name"] != tag:
        raise ValueError("Draft does not match this preparation attempt.")
    validate_target(release, sha)
    release_id = release["id"]
    with open(os.environ["GITEA_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"release_id={release_id}\n")
        output.write(f"release_tag={tag}\nsource_sha={sha}\nsource_tree={tree}\n")
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
    release = prepared_release()
    assets = {asset["name"]: asset["size"] for asset in release["assets"]}
    missing = sorted(expected_assets() - assets.keys())
    empty = sorted(name for name in expected_assets() if name in assets and assets[name] <= 0)
    if missing or empty:
        raise ValueError(f"Release incomplete; missing: {missing}; empty: {empty}")
    if len(release["assets"]) != len(expected_assets()) or set(assets) != expected_assets():
        raise ValueError("Release has duplicate or unexpected attachments.")
    api(f"releases/{release_id}", "PATCH", {"draft": False})
    print(f"Published {release['html_url']} with {len(expected_assets())} verified attachments.")


def upload(path):
    path = Path(path)
    if path.name not in expected_assets() or path.stat().st_size <= 0:
        raise ValueError(f"Unexpected or empty release file: {path.name}")
    release = prepared_release()
    release_id = os.environ["RELEASE_ID"]
    # Partial retries may rebuild or recollect an attachment already uploaded.
    # Replace only this filename in the verified draft; never modify public assets.
    for asset in release["assets"]:
        if asset["name"] == path.name:
            api(f"releases/{release_id}/assets/{asset['id']}", "DELETE")
    boundary = f"logisim-{uuid.uuid4().hex}"
    start = (
        f"--{boundary}\r\nContent-Disposition: form-data; "
        f'name="attachment"; filename="{path.name}"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode()
    body = start + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    base = os.environ["GITEA_API_URL"].rstrip("/")
    repo = os.environ["GITEA_REPOSITORY"]
    request = urllib.request.Request(
        f"{base}/repos/{repo}/releases/{release_id}/assets",
        data=body,
        headers={
            "Authorization": f"token {os.environ['GITEA_TOKEN']}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        uploaded = json.load(response)
    if uploaded["name"] != path.name or uploaded["size"] != path.stat().st_size:
        raise ValueError(f"Gitea attachment verification failed for {path.name}.")
    print(f"Attached {path.name} ({uploaded['size']} bytes).")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["prepare"]:
            prepare()
        elif sys.argv[1:] == ["publish"]:
            publish()
        elif len(sys.argv) > 2 and sys.argv[1] == "upload":
            for filename in sys.argv[2:]:
                upload(filename)
        else:
            raise ValueError("usage: release.py prepare|publish|upload FILE...")
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"Release failed: {error}", file=sys.stderr)
        sys.exit(1)
