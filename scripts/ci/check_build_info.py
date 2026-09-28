#!/usr/bin/env python3
"""Check release provenance generation in isolation; requires cached Gradle dependencies and JDK 21+."""

import json
import os
import subprocess
import tempfile
from pathlib import Path


def main():
    root = Path(__file__).resolve().parents[2]
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=root, text=True).strip()
    env = os.environ.copy()
    env.pop("LOGISIM_SOURCE_SHA", None)
    env.pop("LOGISIM_SOURCE_TREE", None)
    with tempfile.TemporaryDirectory(prefix="logisim-build-info-") as temporary:
        directory = Path(temporary)
        init = directory / "isolated-build.gradle"
        init.write_text(
            "gradle.projectsLoaded { rootProject.layout.buildDirectory.set(new File("
            + json.dumps(str(directory / "build")) + ")) }\n", encoding="utf-8",
        )
        wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
        command = [str(root / wrapper), "genBuildInfo", "--offline", "--no-daemon",
                   "--console=plain", "--project-cache-dir", str(directory / "cache"),
                   "--init-script", str(init)]
        generated = directory / "build/generated/logisim/java/com/cburch/logisim/generated/BuildInfo.java"

        def generate(label, extra=None, error=None):
            print(f"BuildInfo: {label}", flush=True)
            result = subprocess.run(command, cwd=root, env=env | (extra or {}),
                                    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if error:
                if result.returncode == 0 or error not in result.stdout:
                    raise AssertionError(f"Expected rejection: {error}\n{result.stdout}")
                return ""
            if result.returncode:
                raise AssertionError(result.stdout)
            return generated.read_text(encoding="utf-8")

        def field(content, name, expected):
            if f'public static final String {name} = "{expected}";' not in content:
                raise AssertionError(f"Incorrect BuildInfo.{name}; expected {expected}")

        local = generate("local checkout")
        field(local, "sourceCommit", sha)
        field(local, "buildCommit", sha)
        field(local, "sourceTree", tree)
        # Distinct source/checkout SHAs model the independent GitHub snapshot history.
        for canonical in ("1" * 40, "2" * 40):
            content = generate("canonical source and changed task inputs", {
                "LOGISIM_SOURCE_SHA": canonical, "LOGISIM_SOURCE_TREE": tree,
            })
            field(content, "sourceCommit", canonical)
            field(content, "branchLastCommitHash", canonical[:8])
            field(content, "buildId", f"main/{canonical[:8]}")
            field(content, "buildCommit", sha)
            field(content, "sourceTree", tree)

        java_home = os.environ.get("JAVA_HOME")
        javac = str(Path(java_home) / "bin" / "javac") if java_home else "javac"
        subprocess.run([javac, "--release", "21", "-d", str(directory / "classes"),
                        str(root / "src/main/java/com/cburch/logisim/LogisimVersion.java"),
                        str(generated)], cwd=root, check=True)
        generate("reject wrong tree", {
            "LOGISIM_SOURCE_SHA": sha, "LOGISIM_SOURCE_TREE": "f" * 40,
        }, "Release source tree does not match the checkout")
        generate("reject incomplete identity", {
            "LOGISIM_SOURCE_SHA": sha,
        }, "Release provenance requires full LOGISIM_SOURCE_SHA and LOGISIM_SOURCE_TREE")
        restored = generate("local identity after removing release inputs")
        field(restored, "sourceCommit", sha)
        field(restored, "buildCommit", sha)
    print("BuildInfo checks passed; generated Java compiled with --release 21.")


if __name__ == "__main__":
    main()
