# Release APK checks

Commit and tag the source **before** building the published APK. AGP embeds the
build-time Git HEAD in `META-INF/version-control-info.textproto`; changing the
version name/code in an uncommitted tree does not update that revision.

1. Clone the repository into a new directory and check out the official tag.
   Confirm `git status --porcelain` is empty. Keep SDK configuration outside
   tracked source (for example, ignored `local.properties` or `ANDROID_HOME`).
2. Run `gradlew :app:assembleRelease --no-build-cache --no-configuration-cache`
   in that clone. Do not copy build outputs from a working development tree.
3. Run the guard from your tooling checkout, with Python 3 and Git installed:

   ```text
   python scripts/verify-release.py <unsigned-apk> --source <clean-clone> --tag v1.0.6
   ```

4. Sign externally with the existing release key. Preserve the unsigned APK's
   ZIP alignment (`apksigner sign --alignment-preserved true ...`). Never put
   signing credentials or private keys in the repository.
5. Run the guard again on the signed APK. Verify package, version name/code,
   signing certificate and SHA-256 using Android SDK tools.
6. Compare against an independent F-Droid build using F-Droid's signature-copy
   verification, not raw signed-versus-unsigned SHA equality. Publish only after
   this passes. Re-download the public asset, repeat the checks, and update any
   published checksum. Recheck the F-Droid MR pipeline.

The provenance guard does not prove reproducibility or validate the signature.
It is an additional publication check. Do not disable VCS metadata to hide a
revision mismatch, and do not move an existing release tag to match an APK.

Guard regression tests: `python -m unittest discover -s scripts -p 'test_*.py'`.
