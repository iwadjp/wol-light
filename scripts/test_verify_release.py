import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location(
    "verify_release", Path(__file__).with_name("verify-release.py")
)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class ReleaseProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.source.mkdir()
        self.run_git("init", "-q")
        self.run_git("-c", "user.name=Test", "-c", "user.email=test@localhost",
                     "commit", "--allow-empty", "-qm", "previous release")
        self.old = guard.git(self.source, "rev-parse", "HEAD")
        self.run_git("-c", "user.name=Test", "-c", "user.email=test@localhost",
                     "commit", "--allow-empty", "-qm", "release")
        self.current = guard.git(self.source, "rev-parse", "HEAD")
        self.run_git("tag", "v1.0.6")
        self.apk = self.root / "release.apk"

    def run_git(self, *args):
        subprocess.run(["git", "-C", str(self.source), *args], check=True)

    def write_apk(self, revision):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("META-INF/version-control-info.textproto",
                             f'repositories {{ revision: "{revision}" }}')

    def test_official_tag_passes(self):
        self.write_apk(self.current)
        self.assertEqual(guard.verify(self.apk, self.source, "v1.0.6"), self.current)

    def test_previous_release_revision_fails(self):
        self.write_apk(self.old)
        with self.assertRaisesRegex(ValueError, "APK revision"):
            guard.verify(self.apk, self.source, "v1.0.6")

    def test_different_head_fails(self):
        self.write_apk(self.current)
        self.run_git("checkout", "--detach", "-q", self.old)
        with self.assertRaisesRegex(ValueError, "HEAD"):
            guard.verify(self.apk, self.source, "v1.0.6")

    def test_dirty_source_fails(self):
        self.write_apk(self.current)
        (self.source / "uncommitted.txt").write_text("changed")
        with self.assertRaisesRegex(ValueError, "clean"):
            guard.verify(self.apk, self.source, "v1.0.6")

    def test_missing_metadata_fails(self):
        with zipfile.ZipFile(self.apk, "w"):
            pass
        with self.assertRaises(KeyError):
            guard.verify(self.apk, self.source, "v1.0.6")


if __name__ == "__main__":
    unittest.main()
