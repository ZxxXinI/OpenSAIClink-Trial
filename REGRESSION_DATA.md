# Regression Data

The repository keeps the protocol/touch regression logs that are small enough
for normal source history. Large videos, emulator images and private device
evidence are intentionally not included.

## Available without external input

- `analysis/car-20261024` contains the recorded touch logs used by the protocol
  test and the E2 diagnostic summary.
- `analysis/e3-input-audio-20261024` documents the paused USB-audio design.
- The Markdown audit reports and concise JSON fingerprints in the `analysis/`
  tree preserve the protocol findings.

## Local-only large data

The full E4/E5 touch sessions are kept in the local working copy because their
accompanying videos and raw exports are large. The exact file names consumed by
`build.py` are:

- `analysis/e4-car-20261024/session-20261024-125141-touch.txt`
- `analysis/e5-car-20261024/session-20261024-143311-touch.txt`

The touch logs themselves are shipped in this repository. The corresponding
large videos are local-only, so the Java touch replay can run from a normal Git
clone. If the logs are absent, the build prints an explicit `SKIP` for each
replay. The checked-in test result records the assertions that passed with the
complete corpus before the repository was assembled.

## Original inputs

The user-supplied SAICLink APK, `saiclink_welink` package, navigation APK and
car system firmware are not redistributed. Only their hashes, extracted
disassembly, string inventory and reports are included. This keeps the archive
reviewable without republishing third-party binaries.

## Reproducing a limited build

Set `ANDROID_HOME` to an Android SDK containing platform 36 and build-tools
36.0.0, and `JAVA_HOME` to JDK 17. Then run:

```powershell
python build.py
```

The build must end with the protocol test and the deterministic E6 test. If the
recorded touch corpus is absent, the output must include the matching `SKIP`
line. A skipped corpus is not an unverified declaration of a pass.
