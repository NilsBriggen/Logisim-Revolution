# Arch binary packaging

The standalone [`scripts/install-arch.py`](../../scripts/install-arch.py) embeds the PKGBUILD
template. It needs Python 3.11+ and Arch packaging tools, but neither Gradle nor a system JVM.
See the [installation instructions](../../README.md#arch-linux--cachyos) for normal use.

If tools are missing, install only the required packages (no full `base-devel` installation):

```bash
sudo pacman -S --needed python pacman libarchive fakeroot zstd
```

The script reports missing tools with a specific command. Normal installation allows
`makepkg` to request elevation for pacman. Build a package without installing anything,
or choose a specific published release, with:

```bash
python3 install-arch.py --build-only
python3 install-arch.py --tag v5.1.0
```

Use `scripts/install-arch.py` for these options in a checkout. Options can be combined.
Build-only mode reports missing runtime dependencies without installing them. Every run
prints its private `/tmp` build directory and retains the downloaded DEB, generated PKGBUILD,
and `.pkg.tar.zst`; remove that directory when finished. Rerun the installer to update;
`pacman -Syu` alone does not update this local package. `logisim-revolution-bin` provides and
conflicts with `logisim-revolution`.

The installer reads all pages of GitHub's `/releases` API and chooses by publication time,
including prereleases. `--tag` uses the same published-only filter. The `vX.Y.Z` tag and the
exact `logisim-revolution_<version>_<amd64|arm64>.deb` filename must match strict patterns;
the download URL must point to that tag and filename in the canonical repository. It fails
on missing or ambiguous DEBs. Each private `/tmp` directory holds its own download, so
releases with identical DEB filenames never share stale sources.

Release `v5.1.0` must contain `logisim-revolution_5.1.0_<arch>.deb` and becomes package
version `5.1.0-1`, so pacman orders releases by their version numbers. SHA256 is calculated
from the HTTPS download and pinned in the generated recipe; this detects changes between download and packaging, not an independent upstream signature.

The recipe extracts only the DEB's data archive, retaining `/opt/logisim-revolution` and its
bundled JVM. It adds a launcher symlink and installs the native desktop entry, icons, and MIME
definition at standard `/usr/share` paths. The desktop entry gets a readable name, valid
categories, and `%f` for opening circuit files. Arch transaction hooks update caches. There
is no install script and no call to `xdg-mime default` or Debian maintainer scripts.

Dependency names map the native DEB requirements to Arch, with Xi, Xtst, ALSA, and fontconfig
for the bundled JVM and desktop/MIME cache packages for integration. `--build-only` runs only
read-only dependency queries and `makepkg --clean`, without `-s`, `-i`, or `-r`. Normal use
runs `makepkg --clean -si`, allowing pacman to install dependencies and the local package.
It does not request a full system upgrade. The recipe disables binary stripping/debug
splitting to preserve the released native binaries.

Focused tests (no network, package installation, or application build):

```bash
python3 -B -m unittest discover -s support/arch -p 'test_*.py' -v
```

An optional integration test uses an already downloaded authentic DEB. Only the release
lookup and download are substituted; it runs the actual `--build-only` path, `makepkg`,
package inspection, and the extracted native launcher on the host. It verifies x86_64 ELF
identity, payload preservation, metadata, and desktop/MIME assets. Java user home and
preferences are isolated under `/tmp`; nothing is installed globally. Supply the expected
tag and SHA256 so the local fixture cannot silently change:

```bash
LOGISIM_ARCH_TEST_DEB=/absolute/path/logisim-revolution_5.1.0_amd64.deb \
LOGISIM_ARCH_TEST_TAG=v5.1.0 \
LOGISIM_ARCH_TEST_SHA256=a149006a647bdf03da2c604c10e14401ad955aa1f4a70178d5326c25e76309e8 \
python3 -B -m unittest discover -s support/arch -p 'test_*.py' -v
```

The optional test retains and prints its temporary directory for review. ARM64 asset
selection is covered by unit tests; executing an ARM64 package requires an ARM64 host.

Packaging follows the [Arch PKGBUILD manual](https://man.archlinux.org/man/PKGBUILD.5)
and [makepkg manual](https://man.archlinux.org/man/makepkg.8).
