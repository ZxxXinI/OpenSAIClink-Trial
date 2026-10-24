# Regression Snapshot

This repository contains the reproducible E6 source, core tests, final APK and
small audit data. Large device videos, the emulator image, signing keys and
private evidence bundles remain outside Git.

## E6 validation summary

- User acceptance: E6 passed the final K80 plus target-car test on 2026-10-24.
- Host assertions: 5046 protocol checks on a clean checkout.
- Recorded touch regression: E5 2716 checks and E6 3621 checks passed when the
  local 2698-event and 1716-event captured logs were present.
- A7 firmware parser replay: 2711 real frames reached 2370 parser image
  callbacks and all reconstructed payloads were byte-identical. This was CPU
  parser replay only, not display or hardware decode.
- K80 Android 16 checks: accessibility gating, three independently saved app
  slots, two-finger input, suspended-pointer release, return overlay and stop
  cleanup were exercised. Recorded gestures yielded 1.27x expansion and 0.72x
  contraction after ending the gesture.
- Raw H.264 demux yielded 201 device packets, all preserved by the packetizer.
  A separate A7 parser replay accepted the first 177 packets as 177 images,
  including 12 keyframes, and compared bytes were equal.

The 177-frame sample was authored as temporary uncompressed frames and recorded
with the APK's non-fixed-rate low-latency encoder. That file is validation
evidence and is deliberately not shipped. It is not the standard 15-fps USB
path; no new hardware latency conclusion follows from it.

## Final files

The binary is `dist/OpenSAICLink-E6-0.6.0.apk`, 135,709 bytes, SHA-256
`3bfda669b76a65c46e8e1f4eb47e75cb8b3b17eef2d450654615fc89f33df7e5`.
Its signing key remains private. Rebuilding from source produces a functionally
equivalent APK with a different file hash because the signing timestamp changes;
the checked-in binary is a locally verified later signing build. It has the
same source as the accepted E6 release; it is not a byte-identical copy of the
APK that was first installed on the physical phone.

The original version 0.6.0 build installed for final physical-car use was a
different signed artifact. No copy of that exact binary is available for
archiving after local replacement; this limitation is recorded rather than
presented as verified identity.

## What is not included

- Original SAICLink, high-resolution navigation APK, car firmware and user media.
- Emulator filesystem, large H.264 diagnostics, evidence ZIPs and local device logs.
- Local signing keystore and passwords.
- Real car or K80 access automation credentials.

See `E5验证报告.md`, `E5安装与驻车验证.md`, `E6安装与使用.md` and the
small Markdown/JSON audits under `analysis/` for protocol boundaries and
historical results. A physical-passing summary does not prove untested edge
cases such as more than two pointers, nonzero WL extension lengths or hardware
decoder behavior.
