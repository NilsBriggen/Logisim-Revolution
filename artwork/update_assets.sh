#!/usr/bin/env bash
# Regenerate all Logisim Revolution runtime and package artwork from the SVG masters.
# Requires Python 3, rsvg-convert, and ImageMagick.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$project_root/artwork/generate_revolution_assets.py"
