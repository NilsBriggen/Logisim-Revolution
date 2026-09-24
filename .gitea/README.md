# Gitea Actions and release setup

Gitea runs CI, pull request checks, translation reports, and the manual release workflow from
`.gitea/workflows`. Gitea reads that directory before `.github/workflows`. The one workflow in
`.github/workflows` runs hosted Windows and macOS packaging jobs on the public
[GitHub mirror](https://github.com/NilsBriggen/Logisim-Revolution). The inherited upstream
GitHub workflows are removed. Gitea actions are fetched from `gitea.com/actions`.

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
docker run --rm --platform linux/arm64 ubuntu:24.04 uname -m
```

The check must print `aarch64`. The ARM64 job installs an ARM64 JDK 21 inside its container, so
`jpackage` embeds an ARM64 runtime. It uses the public Gitea source repository at the exact
workflow commit. The x86_64 container installs `fakeroot` and RPM tools during its job. ARM64
emulation is slower than a native ARM machine; the job timeout is four hours.

## GitHub hosted Windows and Mac jobs

The public mirror's `.github/workflows/platforms.yml` uses standard `windows-2022`,
`macos-15-intel`, and `macos-15` runners. Windows Server 2022 includes WiX Toolset 3; the
workflow adds it to `PATH` for `jpackage`. Each job checks that the snapshot's Git tree matches the
Gitea manual run, builds its native package, and uploads a one-day GitHub Actions artifact.
The Gitea workflow downloads these artifacts into a draft Gitea release. The macOS DMGs are
development packages without Apple notarization.

[GitHub's billing documentation](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
says standard hosted runner time is free for public repositories. Do not switch the mirror to
private or a larger runner without checking the billing settings.

## Tokens and manual release

In Gitea **Repository Settings → Actions → General**, enable Actions and permit the job token
**Code: read** and **Releases: write**. The workflow uses its built-in `GITEA_TOKEN` for draft
creation and attachment uploads. Store a GitHub token in the Gitea repository Actions secret
`GH_TOKEN`. It needs access only to the public `NilsBriggen/Logisim-Revolution` mirror with
**Contents: read and write** and **Actions: read and write**. It pushes a history-free snapshot of the Gitea source tree
to GitHub `main`, dispatches the hosted workflow, checks its result, and downloads its artifacts.
The GitHub workflow receives no Gitea credential.

In Gitea's **Actions** tab, select **Build all platforms and publish release** and run it on
`main`. It creates a draft prerelease tagged `build-<run-id>-attempt-<attempt>`, builds Linux
x86_64 and emulated ARM64 locally, and dispatches the Windows and Mac jobs. It publishes the
Gitea release only when every job succeeds and all ten expected files are present and nonempty.
A failure leaves the draft unpublished for inspection. A rerun uses a new attempt tag.

The ten files are a portable application JAR and source JAR, DEB and RPM for each Linux
architecture, a Windows x86_64 MSI and portable ZIP, and Intel and Apple Silicon DMGs.
Package names are derived from `gradle.properties` and checked before publication. There is
no automatic nightly release or Snap Store upload.

## Other CI checks

`CI` runs the full Gradle build, Java Checkstyle, changed maintained Markdown lint, and PR
changelog and issue checks. A PR may use `NO_CHANGELOG_ENTRY`,
`NO_CHANGELOG_AUTHOR_CREDIT`, or `NO_TICKET` in its description when the corresponding
requirement does not apply. The post-merge workflow locks merged PRs and closed linked issues
using the Gitea API. Translation checks remain advisory because the inherited bundles contain
known issues. Gitea's built-in issue dependencies replace the old GitHub-only action. Gitea
does not support the `security-events` token scope required by the old CodeQL upload.
