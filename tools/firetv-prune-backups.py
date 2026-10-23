#!/usr/bin/env python3
"""Remove APK installers from previously created private Fire TV backup snapshots.

Usage: python3 tools/firetv-prune-backups.py
Only touches files matching FireTV-FamilyEdition-Final-Backup-*.tar.gz and
firetv-max-backup/final-* under ~/tv-tools. Does not touch the installed apps,
original downloaded APKs outside those snapshots, or Sony/TCL backup archives.
"""
from __future__ import annotations

import hashlib
import io
import os
from pathlib import Path
import shutil
import tarfile
import tempfile

ROOT = Path.home() / "tv-tools"
ARCHIVES = sorted(ROOT.glob("FireTV-FamilyEdition-Final-Backup-*.tar.gz"))
SNAPSHOTS = ROOT / "firetv-max-backup"
INSTALLER_EXTENSIONS = (".apk", ".apks", ".xapk")


def is_installer_member(name: str) -> bool:
    parts = name.strip("/").split("/")
    return any(part.lower() == "apks" for part in parts) or (
        parts[-1].lower().endswith(INSTALLER_EXTENSIONS)
    )


def readme_without_installers(source: bytes) -> bytes:
    text = source.decode("utf-8")
    text = text.replace(
        "It captures the package and debloat lists, accessibility/HOME state, only the\n"
        "permanently signed Family Edition APK (if present), and the private theme/weather configuration.",
        "It captures package and debloat lists, accessibility/HOME state and the private\n"
        "theme/weather configuration. No APK installers or protected app data are included.",
    )
    text = text.replace(
        "3. Install the permanently signed Family Edition APK from apks/ (not an ephemeral CI preview).",
        "3. Obtain the trusted, permanently signed Family Edition APK separately.\n"
        "   Do not use an ephemeral CI preview.",
    )
    text = text.replace(
        "7. Restore other apps from apks/ as required, and sign back in to them.",
        "7. Re-download third-party apps from official sources and sign back in.",
    )
    return text.encode("utf-8")


def scrub_archive(archive: Path) -> bool:
    with tarfile.open(archive, "r:gz") as src:
        if not any(is_installer_member(item.name) for item in src.getmembers()):
            return False
        fd, temp_name = tempfile.mkstemp(prefix=".firetv-sanitised-", suffix=".tar.gz", dir=archive.parent)
        os.close(fd)
        temp = Path(temp_name)
        try:
            with tarfile.open(temp, "w:gz") as dst:
                for item in src.getmembers():
                    if is_installer_member(item.name):
                        continue
                    stream = src.extractfile(item) if item.isfile() else None
                    if stream is not None and item.name.endswith("/README-RESTORE.md"):
                        data = readme_without_installers(stream.read())
                        item.size = len(data)
                        stream = io.BytesIO(data)
                    dst.addfile(item, stream)
            with tarfile.open(temp, "r:gz") as verify:
                names = verify.getnames()
                if not names or any(is_installer_member(name) for name in names):
                    raise RuntimeError("Sanitised archive failed verification")
            os.chmod(temp, 0o600)
            os.replace(temp, archive)
        finally:
            temp.unlink(missing_ok=True)
    sha = hashlib.sha256()
    with archive.open("rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            sha.update(block)
    checksum_file = archive.with_name(archive.name + ".sha256")
    checksum_file.write_text(f"{sha.hexdigest()}  {archive.name}\n", encoding="ascii")
    os.chmod(checksum_file, 0o600)
    print(f"Removed installers from archive: {archive}")
    return True


def scrub_snapshot(directory: Path) -> bool:
    changed = False
    installer_dir = directory / "apks"
    if installer_dir.is_dir():
        shutil.rmtree(installer_dir)
        changed = True
    for path in directory.rglob("*"):
        if path.is_file() and path.name.lower().endswith(INSTALLER_EXTENSIONS):
            path.unlink()
            changed = True
    readme = directory / "README-RESTORE.md"
    if readme.is_file():
        original = readme.read_bytes()
        fixed = readme_without_installers(original)
        if fixed != original:
            readme.write_bytes(fixed)
            changed = True
    if changed:
        print(f"Removed installers from extracted snapshot: {directory}")
    return changed


def main() -> None:
    # Work on archives first: retain the original archive if rebuilding fails.
    archives_changed = sum(scrub_archive(path) for path in ARCHIVES)
    folders_changed = sum(
        scrub_snapshot(path)
        for path in sorted(SNAPSHOTS.glob("final-*"))
        if path.is_dir()
    )
    print(f"Complete: {archives_changed} archive(s), {folders_changed} folder(s) cleaned.")
    print("Original APK downloads outside the backups are unchanged.")
    print("Previously copied backups on other machines must be replaced or cleaned separately.")


if __name__ == "__main__":
    main()
