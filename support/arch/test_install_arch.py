# Logisim-evolution - digital logic design tool and simulator
# Copyright by the Logisim-evolution developers
# https://github.com/logisim-evolution/
# This is free software released under GNU GPLv3 license
"""Focused installer checks; opt-in real packaging and isolated native launch."""

import copy
import hashlib
import importlib.util
import io
import os
import shutil
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/install-arch.py"
spec = importlib.util.spec_from_file_location("install_arch", SCRIPT)
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


def release(tag="build-83-attempt-1", published="2026-09-28T12:00:00Z", **fields):
    result = {"id": 83, "tag_name": tag, "published_at": published,
              "draft": False, "prerelease": True, "assets": []}
    for architecture in ("amd64", "arm64"):
        name = f"logisim-revolution_5.1.0dev_{architecture}.deb"
        result["assets"].append({"name": name, "size": 123,
                                 "browser_download_url": f"{installer.PROJECT_URL}/releases/download/{tag}/{name}"})
    result.update(fields)
    return result


class InstallerTest(unittest.TestCase):
    def test_paginated_publication_order_includes_prereleases(self):
        stable = release("build-81-attempt-1", "2026-09-26T12:00:00Z", prerelease=False)
        newest = release()
        # API order and draft timestamps must not determine the result. Even short
        # pages may be server-capped, so continue to the actual end of the list.
        pages = [[release(draft=True), stable], [newest], []]
        with patch.object(installer, "fetch_page", side_effect=pages) as fetch:
            self.assertEqual(installer.select_release(), newest)
            self.assertEqual([call.args[0] for call in fetch.call_args_list], [1, 2, 3])

    def test_explicit_tag_still_requires_publication(self):
        for fields in ({"draft": True}, {"published_at": None}):
            with self.subTest(fields=fields), patch.object(
                    installer, "fetch_page", side_effect=[[release(**fields)], []]):
                with self.assertRaisesRegex(ValueError, "No published release"):
                    installer.select_release("build-83-attempt-1")
        old = release("build-78-attempt-1", "2026-09-24T12:00:00Z")
        with patch.object(installer, "fetch_page", side_effect=[[release(), old], []]):
            self.assertEqual(installer.select_release(old["tag_name"]), old)

    def test_no_published_release_is_actionable(self):
        with patch.object(installer, "fetch_page", return_value=[]):
            with self.assertRaisesRegex(ValueError, "drafts are excluded"):
                installer.select_release()

    def test_exact_asset_for_each_architecture(self):
        for arch, deb_arch in installer.ARCHITECTURES.items():
            asset, version = installer.select_asset(release(), arch)
            self.assertEqual(asset["name"], f"logisim-revolution_5.1.0dev_{deb_arch}.deb")
            self.assertEqual(version, "5.1.0dev.r83.a1")
        with self.assertRaisesRegex(ValueError, "Unsupported architecture"):
            installer.select_asset(release(), "i686")

    def test_missing_or_ambiguous_deb_is_rejected(self):
        asset = release()["assets"][0]
        for assets in ([], [release()["assets"][1]], [asset, asset],
                       [{**asset, "name": "other_5.1.0dev_amd64.deb"}],
                       [{**asset, "name": "logisim-revolution_5.1.0dev_amd64.deb.sig"}]):
            with self.subTest(assets=assets), self.assertRaisesRegex(ValueError, "exactly one"):
                installer.select_asset(release(assets=assets), "x86_64")

    def test_remote_fields_must_be_safe_and_canonical(self):
        original = release()
        changes = [
            ("tag_name", "build-83-attempt-1;echo bad"),
            ("name", "../logisim-revolution_5.1.0dev_amd64.deb"),
            ("name", "logisim-revolution_5.1.0$(echo bad)_amd64.deb"),
            ("browser_download_url", "https://example.com/other.deb"),
            ("browser_download_url", original["assets"][0]["browser_download_url"].replace("https:", "http:")),
            ("size", 0),
        ]
        for field, value in changes:
            candidate = copy.deepcopy(original)
            target = candidate if field == "tag_name" else candidate["assets"][0]
            target[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                installer.select_asset(candidate, "x86_64")

    def test_canonical_attachment_url_matches_asset_uuid(self):
        candidate = release()
        asset = candidate["assets"][0]
        asset["uuid"] = "15a5d6e8-29f4-4b7c-9cd7-31d7bf730aa7"
        asset["browser_download_url"] = f"https://git.briggen.dev/attachments/{asset['uuid']}"
        self.assertEqual(installer.select_asset(candidate, "x86_64")[0], asset)
        asset["uuid"] = "00cda4fa-eadd-4774-acce-d32dba6c25b4"
        with self.assertRaisesRegex(ValueError, "unexpected download URL"):
            installer.select_asset(candidate, "x86_64")

    @unittest.skipUnless(shutil.which("vercmp"), "pacman vercmp is needed")
    def test_versions_upgrade_numerically(self):
        pairs = [("5.1.0dev", "build-9-attempt-1", "5.1.0dev", "build-10-attempt-1"),
                 ("5.1.0dev", "build-83-attempt-9", "5.1.0dev", "build-83-attempt-10"),
                 ("5.1.0dev", "build-83-attempt-10", "5.1.0dev", "build-84-attempt-1"),
                 ("5.1.0dev", "build-83-attempt-1", "5.1.0", "build-84-attempt-1"),
                 ("5.1.0", "build-84-attempt-1", "5.2.0dev", "build-85-attempt-1")]
        for old, old_tag, new, new_tag in pairs:
            comparison = subprocess.check_output([
                "vercmp", installer.package_version(new, new_tag),
                installer.package_version(old, old_tag),
            ], text=True).strip()
            self.assertEqual(comparison, "1")

    def test_root_and_missing_tools_fail_before_work(self):
        with patch.object(installer.os, "geteuid", return_value=0):
            with self.assertRaisesRegex(ValueError, "without sudo"):
                installer.check_dependencies(True)
        with patch.object(installer.os, "geteuid", return_value=1000), patch.object(
                installer.shutil, "which", side_effect=lambda name: None if name == "fakeroot" else name):
            with self.assertRaisesRegex(ValueError, "sudo pacman -S --needed fakeroot$"):
                installer.check_dependencies(True)

    def test_build_only_missing_dependencies_never_installs(self):
        with patch.object(installer.os, "geteuid", return_value=1000), patch.object(
                installer.shutil, "which", return_value="tool"), patch.object(
                installer.subprocess, "run", return_value=subprocess.CompletedProcess(
                    [], 127, "libxi\nlibxtst\n", "")) as run:
            with self.assertRaisesRegex(ValueError, "sudo pacman -S --needed libxi libxtst$"):
                installer.check_dependencies(True)
            self.assertEqual(run.call_count, 1)
            self.assertEqual(run.call_args.args[0][:2], ["pacman", "-T"])

    def test_makepkg_modes_and_isolated_destinations(self):
        directory = Path("/tmp/arch package")
        for build_only in (True, False):
            with patch.object(installer.subprocess, "run") as run:
                installer.run_makepkg(directory, build_only)
                command = run.call_args.args[0]
                self.assertEqual(command[:2], ["makepkg", "--clean"])
                self.assertEqual("-si" in command, not build_only)
                self.assertNotIn("--syncdeps", command)
                self.assertNotIn("--rmdeps", command)
                self.assertEqual(run.call_args.kwargs["cwd"], directory)
                for key in ("BUILDDIR", "SRCDEST", "PKGDEST"):
                    self.assertIn(f"{key}={directory}", command)

    def test_truncated_download_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(
                installer.urllib.request, "urlopen", return_value=io.BytesIO(b"short")):
            with self.assertRaisesRegex(ValueError, "size differs"):
                installer.download(release()["assets"][0], Path(directory) / "download.deb")

    def test_recipe_pins_checksum_and_only_data_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            asset, pkgver = installer.select_asset(release(), "x86_64")
            (root / asset["name"]).write_bytes(b"fixture")
            with patch.object(installer.subprocess, "check_output", return_value=
                              "debian-binary\ncontrol.tar.zst\ndata.tar.zst\n"):
                installer.write_pkgbuild(root, asset, pkgver, "x86_64")
            recipe = (root / "PKGBUILD").read_text()
            self.assertIn(hashlib.sha256(b"fixture").hexdigest(), recipe)
            self.assertIn("'data.tar.zst'", recipe)
            self.assertNotIn("control.tar", recipe)
            self.assertNotIn("xdg-mime default", recipe)
            self.assertNotIn("@PKGVER@", recipe)
            with patch.object(installer.subprocess, "check_output", return_value=
                              "debian-binary\ncontrol.tar.zst\ndata.tar.zst\ndata.tar.gz\n"):
                with self.assertRaisesRegex(ValueError, "exactly one supported data"):
                    installer.write_pkgbuild(root, asset, pkgver, "x86_64")


@unittest.skipUnless(os.environ.get("LOGISIM_ARCH_TEST_DEB"), "optional authentic DEB fixture not supplied")
class NativePackageTest(unittest.TestCase):
    def test_build_only_and_native_launcher(self):
        self.assertEqual(installer.platform.machine(), "x86_64")
        deb = Path(os.environ["LOGISIM_ARCH_TEST_DEB"]).resolve()
        checksum = os.environ["LOGISIM_ARCH_TEST_SHA256"]
        with deb.open("rb") as source:
            self.assertEqual(hashlib.file_digest(source, "sha256").hexdigest(), checksum)
        fixture = release(tag=os.environ["LOGISIM_ARCH_TEST_TAG"])
        fixture["assets"][0]["name"] = deb.name
        fixture["assets"][0]["size"] = deb.stat().st_size
        fixture["assets"][0]["browser_download_url"] = (
            f"{installer.PROJECT_URL}/releases/download/{fixture['tag_name']}/{deb.name}")
        asset, pkgver = installer.select_asset(fixture, "x86_64")
        scratch = Path(tempfile.mkdtemp(prefix="logisim-arch-test-", dir="/tmp"))
        print(f"\nNative integration evidence: {scratch}", flush=True)

        # Load a copy by itself, proving no checkout/template is required at runtime.
        standalone = scratch / "install-arch.py"
        shutil.copyfile(SCRIPT, standalone)
        spec = importlib.util.spec_from_file_location("standalone_install_arch", standalone)
        standalone_module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(standalone_module)

        # Catch accidental package mutations even inside the real makepkg subprocess.
        guards = scratch / "guards"
        guards.mkdir()
        pacman = guards / "pacman"
        pacman.write_text('#!/bin/sh\ncase "$1" in\n-T|-Q*) exec /usr/bin/pacman "$@";;\n'
                          '*) echo "Unexpected pacman mutation: $*" >&2; exit 99;;\nesac\n')
        sudo = guards / "sudo"
        sudo.write_text('#!/bin/sh\necho "Unexpected sudo invocation" >&2\nexit 99\n')
        pacman.chmod(0o755)
        sudo.chmod(0o755)
        associations = [Path.home() / relative for relative in (
            ".config/mimeapps.list", ".config/kde-mimeapps.list",
            ".local/share/applications/mimeapps.list", ".local/share/applications/defaults.list")]
        before = {path: path.read_bytes() if path.exists() else None for path in associations}
        output = io.StringIO()
        with patch.object(standalone_module, "fetch_page", side_effect=[[fixture], []]), patch.object(
                standalone_module, "download", side_effect=lambda _, dest: shutil.copyfile(deb, dest)), patch.dict(
                os.environ, {"PATH": f"{guards}:{os.environ['PATH']}", "PACMAN": str(pacman)}), redirect_stdout(output):
            standalone_module.main(["--build-only", "--tag", fixture["tag_name"]])
        (scratch / "installer.log").write_text(output.getvalue())
        print(output.getvalue(), end="")
        directory = Path(next(line.split(": ", 1)[1] for line in output.getvalue().splitlines()
                              if line.startswith("Build directory (retained): ")))
        package = directory / f"logisim-revolution-bin-{pkgver}-1-x86_64.pkg.tar.zst"
        listing = subprocess.check_output(["bsdtar", "--numeric-owner", "-tvf", str(package)], text=True)
        self.assertTrue(all(line.split()[2:4] == ["0", "0"] for line in listing.splitlines()))
        root = scratch / "package"
        root.mkdir()
        subprocess.run(["bsdtar", "-xf", str(package), "-C", str(root)], check=True)
        info = (root / ".PKGINFO").read_text()
        for field in ("pkgname = logisim-revolution-bin", "arch = x86_64", f"pkgver = {pkgver}-1",
                      f"provides = logisim-revolution={pkgver}", "conflict = logisim-revolution"):
            self.assertIn(field, info)
        for dependency in installer.DEPENDENCIES:
            self.assertIn(f"depend = {dependency}\n", info)
        self.assertFalse((root / ".INSTALL").exists())
        self.assertEqual(os.readlink(root / "usr/bin/logisim-revolution"),
                         "/opt/logisim-revolution/bin/logisim-revolution")
        self.assertIn(checksum, (directory / "PKGBUILD").read_text())
        for relative in ("applications/logisim-revolution.desktop", "mime/packages/logisim-revolution.xml",
                         "icons/hicolor/128x128/apps/logisim-revolution.png",
                         "icons/hicolor/128x128/mimetypes/application-x-logisim-circuit.png"):
            self.assertTrue((root / "usr/share" / relative).is_file())
        subprocess.run(["desktop-file-validate", str(root / "usr/share/applications/logisim-revolution.desktop")],
                       check=True)
        self.assertIn(" %f", (root / "usr/share/applications/logisim-revolution.desktop").read_text())
        self.assertIn('pattern="*.circ"', (root / "usr/share/mime/packages/logisim-revolution.xml").read_text())

        # Compare the whole shipped /opt payload, including JVM files and symlink targets.
        original = scratch / "original"
        original.mkdir()
        with (scratch / "data.tar").open("wb") as data:
            subprocess.run(["bsdtar", "-xOf", str(deb), "data.tar.zst"], stdout=data, check=True)
        subprocess.run(["bsdtar", "-xf", str(scratch / "data.tar"), "-C", str(original)], check=True)
        def manifest(base):
            entries = {}
            for path in (base / "opt").rglob("*"):
                if path.is_symlink():
                    entries[str(path.relative_to(base))] = ("symlink", os.readlink(path))
                elif path.is_file():
                    with path.open("rb") as content:
                        entries[str(path.relative_to(base))] = (
                            path.stat().st_mode & 0o777, hashlib.file_digest(content, "sha256").hexdigest())
            return entries
        self.assertEqual(manifest(root), manifest(original))

        app = root / "opt/logisim-revolution"
        for binary in (app / "bin/logisim-revolution", app / "lib/runtime/lib/server/libjvm.so"):
            with binary.open("rb") as source:
                elf = source.read(20)
            self.assertEqual(elf[:6], b"\x7fELF\x02\x01")
            self.assertEqual(int.from_bytes(elf[18:20], "little"), 62)  # ELF EM_X86_64
        java_home = scratch / "java-home"
        java_home.mkdir()
        environment = {**os.environ, "JAVA_TOOL_OPTIONS": (
            f"-Duser.home={java_home} -Djava.util.prefs.userRoot={java_home}/prefs "
            f"-Djava.util.prefs.systemRoot={java_home}/system-prefs -Djava.awt.headless=true"),
            "LC_ALL": "C", "_JAVA_OPTIONS": "", "JDK_JAVA_OPTIONS": ""}
        for argument, expected in (("--version", "5.1.0dev"), ("--help", "--test-vector")):
            result = subprocess.run([str(app / "bin/logisim-revolution"), argument], cwd=scratch,
                                    env=environment, capture_output=True, text=True, timeout=30)
            (scratch / f"launcher-{argument[2:]}.log").write_text(result.stdout + result.stderr)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn(expected, result.stdout)
        after = {path: path.read_bytes() if path.exists() else None for path in associations}
        self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main()
