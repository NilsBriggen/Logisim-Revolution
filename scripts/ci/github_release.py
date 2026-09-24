#!/usr/bin/env python3
"""Dispatch hosted native builds and copy their artifacts into a Gitea draft."""

import json
import shutil
import uuid
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path


GITHUB_REPOSITORY = "NilsBriggen/Logisim-Revolution"
GITHUB_WORKFLOW = "platforms.yml"
ARTIFACTS = {
    "windows-amd64": {".msi", ".zip"},
    "macos-amd64": {"-x86_64.dmg"},
    "macos-arm64": {"-aarch64.dmg"},
}


def github_api(path, method="GET", data=None):
    request = urllib.request.Request(
        f"https://api.github.com/repos/{GITHUB_REPOSITORY}/{path}",
        data=json.dumps(data).encode() if data is not None else None,
        headers={
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
            "User-Agent": "logisim-revolution-release",
        },
        method=method,
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        content = response.read()
        return json.loads(content) if content else None


def git(*args, env=None):
    subprocess.run(["git", *args], check=True, env=env)


def git_output(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def mirror_and_dispatch():
    sha = os.environ["GITEA_SHA"]
    if git_output("rev-parse", "HEAD") != sha:
        raise ValueError("Gitea checkout does not match the dispatched commit.")
    source_tree = git_output("rev-parse", "HEAD^{tree}")
    tag = os.environ["RELEASE_TAG"]
    with tempfile.TemporaryDirectory() as temporary:
        # GitHub contains source snapshots, never the inherited Gitea history.
        mirror = Path(temporary) / "mirror"
        git("clone", "--depth=1", f"https://github.com/{GITHUB_REPOSITORY}.git", str(mirror))
        for path in mirror.iterdir():
            if path.name != ".git":
                if path.is_dir() and not path.is_symlink():
                    shutil.rmtree(path)
                else:
                    path.unlink()
        archive = Path(temporary) / "source.tar"
        with archive.open("wb") as output:
            subprocess.run(["git", "archive", "--format=tar", sha], stdout=output, check=True)
        subprocess.run(["tar", "-xf", str(archive), "-C", str(mirror)], check=True)
        git("-C", str(mirror), "switch", "-C", "main")
        git("-C", str(mirror), "add", "-A")
        if git_output("-C", str(mirror), "write-tree") != source_tree:
            raise ValueError("GitHub snapshot does not match the Gitea source tree.")
        if subprocess.run(["git", "-C", str(mirror), "diff", "--cached", "--quiet"]).returncode:
            git("-C", str(mirror), "-c", "user.name=Logisim Revolution CI",
                "-c", "user.email=ci@users.noreply.github.com", "commit",
                "-m", f"Build snapshot of Gitea {sha}")
        mirror_sha = git_output("-C", str(mirror), "rev-parse", "HEAD")
        askpass = Path(temporary) / "askpass.sh"
        askpass.write_text(
            '#!/bin/sh\ncase "$1" in *Username*) printf "x-access-token";; '
            '*Password*) printf "%s" "$GH_TOKEN";; *) exit 1;; esac\n',
            encoding="utf-8",
        )
        askpass.chmod(0o700)
        env = os.environ.copy()
        env.update({"GIT_ASKPASS": str(askpass), "GIT_TERMINAL_PROMPT": "0"})
        git(
            "-C", str(mirror), "-c", "credential.helper=", "push",
            f"https://github.com/{GITHUB_REPOSITORY}.git",
            "HEAD:refs/heads/main", env=env,
        )
    if github_api("git/ref/heads/main")["object"]["sha"] != mirror_sha:
        raise ValueError("GitHub mirror did not advance to the source snapshot.")
    if github_api(f"git/commits/{mirror_sha}")["tree"]["sha"] != source_tree:
        raise ValueError("GitHub mirror tree differs from the Gitea source tree.")
    github_api(
        f"actions/workflows/{GITHUB_WORKFLOW}/dispatches", "POST",
        {"ref": "main", "inputs": {
            "source_sha": sha, "source_tree": source_tree, "release_tag": tag,
        }},
    )
    print(f"Dispatched GitHub Windows and macOS builds for {tag} (source {sha}).")


def matching_run(tag):
    result = github_api(
        f"actions/workflows/{GITHUB_WORKFLOW}/runs?event=workflow_dispatch&per_page=50"
    )
    matches = [
        run for run in result["workflow_runs"]
        if run["display_title"] == f"Gitea build {tag}"
    ]
    if not matches:
        return None
    run = max(matches, key=lambda candidate: candidate["id"])
    source_tree = git_output("rev-parse", "HEAD^{tree}")
    mirror_tree = github_api(f"git/commits/{run['head_sha']}")["tree"]["sha"]
    if mirror_tree != source_tree:
        raise ValueError("GitHub workflow source tree differs from the Gitea release commit.")
    return run


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        return None


def download_artifact(url, destination):
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "logisim-revolution-release",
        },
    )
    try:
        urllib.request.build_opener(NoRedirect).open(request, timeout=60)
    except urllib.error.HTTPError as response:
        if response.code not in (301, 302, 303, 307, 308):
            raise
        location = response.headers["Location"]
    else:
        raise ValueError("GitHub artifact endpoint did not redirect to a download.")
    if not location.startswith("https://"):
        raise ValueError("Artifact download URL is not HTTPS.")
    with urllib.request.urlopen(location, timeout=180) as response:
        destination.write_bytes(response.read())


def valid_names(group, names):
    if group == "windows-amd64":
        return len(names) == 2 and {Path(name).suffix for name in names} == ARTIFACTS[group]
    suffix = next(iter(ARTIFACTS[group]))
    return len(names) == 1 and names[0].endswith(suffix)


def gitea_upload(release_id, path):
    boundary = f"logisim-{uuid.uuid4().hex}"
    start = (
        f"--{boundary}\r\nContent-Disposition: form-data; "
        f' name="attachment"; filename="{path.name}"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode()
    body = start + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    api = os.environ["GITEA_API_URL"].rstrip("/")
    repo = os.environ["GITEA_REPOSITORY"]
    request = urllib.request.Request(
        f"{api}/repos/{repo}/releases/{release_id}/assets",
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


def collect():
    tag = os.environ["RELEASE_TAG"]
    deadline = time.monotonic() + 3 * 60 * 60
    last_status = None
    while time.monotonic() < deadline:
        run = matching_run(tag)
        if run and run["status"] == "completed":
            if run["conclusion"] != "success":
                raise ValueError(f"GitHub build failed: {run['html_url']}")
            break
        status = run["status"] if run else "waiting for dispatch"
        if status != last_status:
            print(f"GitHub build: {status}", flush=True)
            last_status = status
        time.sleep(30)
    else:
        raise ValueError(f"Timed out waiting for GitHub build {tag}.")
    result = github_api(f"actions/runs/{run['id']}/artifacts?per_page=100")
    artifacts = result["artifacts"]
    by_name = {artifact["name"]: artifact for artifact in artifacts}
    if len(artifacts) != len(ARTIFACTS) or set(by_name) != set(ARTIFACTS):
        raise ValueError(f"Expected three GitHub artifacts, got {[a['name'] for a in artifacts]}.")
    with tempfile.TemporaryDirectory() as temporary:
        for group, artifact in by_name.items():
            if artifact["expired"] or artifact["size_in_bytes"] <= 0:
                raise ValueError(f"GitHub artifact {group} is empty or expired.")
            archive = Path(temporary) / f"{group}.zip"
            download_artifact(artifact["archive_download_url"], archive)
            with zipfile.ZipFile(archive) as bundle:
                names = bundle.namelist()
                if not valid_names(group, names) or any(
                    Path(name).name != name or not name for name in names
                ):
                    raise ValueError(f"Unexpected files in GitHub artifact {group}: {names}")
                for name in names:
                    path = Path(temporary) / name
                    with bundle.open(name) as source, path.open("wb") as output:
                        shutil.copyfileobj(source, output)
                    if path.stat().st_size <= 0:
                        raise ValueError(f"Empty release file: {name}")
                    gitea_upload(os.environ["RELEASE_ID"], path)
    print(f"Collected all Windows and macOS files from {run['html_url']}.")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["dispatch"]:
            mirror_and_dispatch()
        elif sys.argv[1:] == ["collect"]:
            collect()
        else:
            raise ValueError("usage: github_release.py dispatch|collect")
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError, urllib.error.URLError) as error:
        print(f"GitHub build handoff failed: {error}", file=sys.stderr)
        sys.exit(1)
