# Gitea Actions and release setup

Gitea runs CI, pull request checks, translation reports, and the manual release workflow from
`.gitea/workflows`. Gitea reads that directory before `.github/workflows`. The one workflow in
`.github/workflows` runs hosted Windows and macOS packaging jobs on the public
[GitHub mirror](https://github.com/NilsBriggen/Logisim-Revolution). The inherited upstream
GitHub workflows are removed. The Gitea runner fetches the standard helper actions from `github.com/actions`.

## Gitea runner

The Ubuntu server's existing `act_runner` keeps its `ubuntu-latest` container label for CI.
The manual release additionally needs these two labels:

| Label | Execution image | Purpose |
| --- | --- | --- |
| `linux-amd64` | `catthehacker/ubuntu:act-latest` | JDK 21 build, tests, JAR, sources, DEB and RPM |
| `linux-arm64` | `arm64v8/ubuntu:24.04` | ARM64 DEB and RPM under host QEMU binfmt |

The runner configuration uses `docker://` labels and `container.docker_host: "-"`. Jobs have no
access to the host Docker socket. On the current Ubuntu 26.04 host, install `qemu-user-binfmt`
and check ARM64 execution before starting a release:

```bash
sudo apt-get install qemu-user-binfmt
docker pull --platform linux/arm64 arm64v8/ubuntu:24.04
docker run --rm arm64v8/ubuntu:24.04 uname -m
```

The check must print `aarch64` (Docker may warn about the host architecture). Keep this
ARM64 image on the server: act_runner cannot pull an ARM-only image using the host's default
x86_64 platform. The ARM64 job installs an ARM64 JDK 21 inside its container, so
`jpackage` embeds an ARM64 runtime. It uses the public Gitea source repository at the exact
workflow commit. Both Linux jobs install `fakeroot`, RPM tools, and `binutils` for
`jpackage` (which calls `objcopy`). ARM64 emulation is slower than a native ARM machine;
the job timeout is four hours.

## GitHub hosted Windows and Mac jobs

The public mirror's `.github/workflows/platforms.yml` uses standard `windows-2022`,
`macos-15-intel`, and `macos-15` runners. Windows Server 2022 includes WiX Toolset 3; the
workflow adds it to `PATH` for `jpackage`. Each job checks that the snapshot's Git tree matches the
Gitea manual run, builds its native package, and uploads a one-day GitHub Actions artifact.
The Gitea workflow downloads these artifacts into a draft Gitea release. The macOS DMGs are
development packages without Apple notarization.

The `prepare` job uses the `logisim-revolution-github-mirror` concurrency group with
`cancel-in-progress: false`. Use Gitea 1.26 or newer with job concurrency support. Only preparation
and dispatch are serialized; native builds can overlap. Gitea retains only one pending job in a
concurrency group, so avoid queuing batches of manual releases.

The mirror keeps its own snapshot history and never receives the inherited Gitea Git history.
An atomic, non-forced push advances GitHub `main` and creates the immutable lightweight tag
`gitea-build-<run-id>-attempt-<preparation-attempt>`. Dispatch uses that tag, and hosted jobs check
out the resolved workflow commit. Collection verifies both the tag's source tree and the hosted
run's exact snapshot commit. Existing tags are reused only when their tree matches; they are never
moved. An external conflicting push fails preparation instead of overwriting remote history.

All native builds and the portable JAR receive `LOGISIM_SOURCE_SHA` and `LOGISIM_SOURCE_TREE`.
`genBuildInfo` requires both full hashes and checks the tree against `HEAD^{tree}` before recording
the canonical Gitea commit in `BuildInfo.sourceCommit`, `branchLastCommitHash`, and `buildId`.
`BuildInfo.buildCommit` retains the actual checkout commit (the independent snapshot SHA on
GitHub), and `sourceTree` records the shared tree. Normal local builds use their own Git identity.

[GitHub's billing documentation](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
says standard hosted runner time is free for public repositories. Do not switch the mirror to
private or a larger runner without checking the billing settings.

## Tokens and manual release

In Gitea **Repository Settings → Actions → General**, enable Actions and permit the job token
**Code: read** and **Releases: write**. The workflow uses its built-in `GITEA_TOKEN` for draft
creation and attachment uploads. Store a GitHub token in the Gitea repository Actions secret
`GH_TOKEN`. It needs access only to the public `NilsBriggen/Logisim-Revolution` mirror with
**Contents: read and write**, **Actions: read and write**, and **Workflows: write**.
The workflow permission is needed when the snapshot updates `.github/workflows`; a classic token
needs the corresponding `workflow` scope. The token pushes source snapshots and immutable tags,
dispatches the hosted workflow, checks its result, and downloads its artifacts.
The GitHub workflow receives no Gitea credential.

In Gitea's **Actions** tab, select **Build all platforms and publish release** and run it on
`main`. It creates a draft prerelease tagged `build-<run-id>-attempt-<attempt>`, builds Linux
x86_64 and emulated ARM64 locally, and dispatches the Windows and Mac jobs. It publishes the
Gitea release only when every job succeeds and all ten expected files are present and nonempty.
A failure leaves the draft unpublished for inspection. Preparation exports the release ID, release
tag, source SHA, and source tree as job outputs. Every consumer uses this prepared identity.
A retry of only a failed native build, collection, or publication job reuses the original draft and
GitHub snapshot, even when the workflow attempt number increases. Uploads replace only their own
filenames in that verified draft. A full workflow rerun (or a rerun including `prepare`) creates a
new draft and snapshot tag using the new preparation attempt.

If the hosted GitHub build itself failed, rerun its failed jobs before retrying Gitea collection,
or rerun the entire Gitea workflow. GitHub artifacts expire after one day; after expiry, use a new
full release run. Retrying an already published release fails without modifying it.

The ten files are a portable application JAR and source JAR, DEB and RPM for each Linux
architecture, a Windows x86_64 MSI and portable ZIP, and Intel and Apple Silicon DMGs.
Package names are derived from `gradle.properties` and checked before publication. Publication
also checks the prepared tag, the canonical Gitea checkout SHA, the draft's target commit, and any
existing Gitea tag's commit. Missing, empty, duplicate, or unexpected attachments block publication.
There is no automatic nightly release or Snap Store upload.

### Trigger and verify a release

1. Record the intended Gitea `main` commit and its tree (`git rev-parse HEAD HEAD^{tree}`), and
   confirm the normal CI build passes for that commit.
2. Run **Build all platforms and publish release** on `main` in Gitea Actions. Record the
   prepared tag and draft release ID from `prepare`; retries may have a newer attempt number.
3. Check the GitHub run titled **Gitea build `<prepared-tag>`**. All three hosted jobs must succeed,
   its commit must equal the GitHub `gitea-<prepared-tag>` tag, and its tree must equal the Gitea
   tree. The two repositories' commit SHAs intentionally differ.
4. Check that all five Gitea jobs succeeded. The final release must have `draft=false`,
   `prerelease=true`, and a tag resolving to the recorded Gitea commit. Compare its ten nonempty,
   downloadable attachments with `release.expected_assets()` for that checkout.
5. When checking a packaged application's build identity, use the canonical `buildId`/`sourceCommit`.
   The separate `buildCommit` identifies the checkout used to build that platform.

### Focused release checks

Run the Python unit and workflow policy tests without contacting either service:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/ci -p 'test_*.py'
```

With JDK 21 and Gradle dependencies already cached, check generated provenance and compile the
generated class in a temporary build directory (does not run the full Gradle test/build suite):

```bash
python3 scripts/ci/check_build_info.py
```

## Other CI checks

`CI` runs the full Gradle build, Java Checkstyle, changed maintained Markdown lint, and PR
changelog and issue checks. A PR may use `NO_CHANGELOG_ENTRY`,
`NO_CHANGELOG_AUTHOR_CREDIT`, or `NO_TICKET` in its description when the corresponding
requirement does not apply. The post-merge workflow locks merged PRs and closed linked issues
using the Gitea API. Translation checks remain advisory because the inherited bundles contain
known issues. Gitea's built-in issue dependencies replace the old GitHub-only action. Gitea
does not support the `security-events` token scope required by the old CodeQL upload.
