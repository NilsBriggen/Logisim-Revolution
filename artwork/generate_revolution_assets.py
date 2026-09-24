#!/usr/bin/env python3
"""Build runtime and packaging icons from the three editable SVG masters.

Requires rsvg-convert and ImageMagick. ICNS is assembled from PNG icon chunks,
so macOS package assets can be generated on Linux too.
"""
from __future__ import annotations

import shutil
import struct
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "artwork"
IMG = ROOT / "src/main/resources/resources/logisim/img"
SUPPORT = ROOT / "support/jpackage"
SIZES = (16, 32, 48, 64, 128, 256, 512)


def render(name: str, destination: Path, width: int) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["rsvg-convert", "-w", str(width), "-o", str(destination), str(ART / name)],
        check=True,
    )


def icns(pngs: dict[int, Path], destination: Path) -> None:
    chunks = []
    for size, kind in ((16, b"icp4"), (32, b"icp5"), (64, b"icp6"),
                       (128, b"ic07"), (256, b"ic08"), (512, b"ic09")):
        image = pngs[size].read_bytes()
        chunks.append(kind + struct.pack(">I", len(image) + 8) + image)
    body = b"".join(chunks)
    destination.write_bytes(b"icns" + struct.pack(">I", len(body) + 8) + body)


def main() -> None:
    app = {}
    document = {}
    for size in SIZES:
        app[size] = IMG / f"logisim-revolution-icon-{size}.png"
        document[size] = IMG / f"logisim-revolution-document-{size}.png"
        render("logisim-revolution-mark.svg", app[size], size)
        render("logisim-revolution-document.svg", document[size], size)

    render("logisim-revolution-wordmark.svg", IMG / "logisim-revolution-logo.png", 900)
    render("logisim-revolution-mark.svg",
           ROOT / "src/main/resources/doc/img-guide/revolution-mark-24.png", 24)
    brand = ROOT / "src/main/resources/resources/logisim/brand"
    brand.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ART / "logisim-revolution-mark.svg", brand / "logisim-revolution-mark.svg")
    shutil.copyfile(app[128], SUPPORT / "linux/logisim-revolution-icon-128.png")
    subprocess.run(
        ["magick", *map(str, (app[size] for size in (16, 32, 48, 64, 128, 256))),
         str(SUPPORT / "windows/Logisim-Revolution.ico")],
        check=True,
    )
    subprocess.run(
        ["magick", *map(str, (document[size] for size in (16, 32, 48, 64, 128, 256))),
         str(SUPPORT / "windows/Logisim-Revolution-circ.ico")],
        check=True,
    )
    icns(app, SUPPORT / "macos/Logisim-Revolution.icns")
    icns(document, SUPPORT / "macos/Logisim-Revolution-circ.icns")
    icns(app, IMG / "Logisim-Revolution.icns")
    shutil.copyfile(app[128], ROOT / "snap/gui/logisim-revolution-icon-128.png")
    shutil.copyfile(app[128], ROOT / "support/Flatpak/dev.briggen.LogisimRevolution.png")


if __name__ == "__main__":
    main()
