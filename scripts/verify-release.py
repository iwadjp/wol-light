"""Reject release APKs built before the tagged source was committed."""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile


def git(source, *args):
    return subprocess.check_output(
        ["git", "-C", str(source), *args], text=True
    ).strip()


def verify(apk, source, tag):
    expected = git(source, "rev-parse", "--verify", f"{tag}^{{commit}}")
    if git(source, "rev-parse", "HEAD") != expected:
        raise ValueError("Build source HEAD does not match the release tag")
    if git(source, "status", "--porcelain"):
        raise ValueError("Build source must be clean, including untracked files")
    with zipfile.ZipFile(apk) as archive:
        metadata = archive.read("META-INF/version-control-info.textproto").decode()
    revisions = re.findall(r'revision:\s*"([0-9a-f]+)"', metadata)
    if revisions != [expected]:
        raise ValueError(f"APK revision {revisions} does not match tag {expected}")
    return expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    try:
        revision = verify(args.apk, args.source, args.tag)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile,
            subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release provenance check failed: {error}\n")
    print(f"Release provenance verified: {revision}")


if __name__ == "__main__":
    main()
