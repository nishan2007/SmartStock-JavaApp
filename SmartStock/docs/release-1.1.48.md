# SmartStock 1.1.48 (build 101048)

Windows updater and all-in-one installer for the Best background-removal DirectML
fix. The isolated GPU runtime is pinned to ONNX Runtime DirectML 1.24.4 with a
verified package SHA-256 and retained notices. Removal disables oversized fused
graph allocations; enhancement keeps its existing fusion configuration. The
Best model, full input resolution, normalization, alpha conversion and CPU
fallback remain unchanged. SmartStudio retains its separate release channel.

On the GEEKOM A7 / Radeon 780M, using the operator-authorized queue photo, actual
GPU processing took 15.045 seconds cold and 5.228 / 5.139 seconds warm. CPU took
27.419 seconds cold and 19.818 / 19.630 seconds warm. Maximum CPU/GPU alpha
difference was 1/255; mean difference was 0.00003 on the 0–255 scale. These are
one-photo hardware measurements, not a universal latency guarantee.

Before release packaging, all five actual DirectML removal protocol tests passed
on the A7, including full verified Best-model inference without fallback. A real
enhancement inference smoke test passed with the new runtime. The full Maven
suite passed with 1,111 tests, zero failures/errors and 79 skips. Repository
security and whitespace checks passed. See
[background-removal performance](background-removal-performance.md).

The standard Windows packager reran the full test suite successfully. Final ZIP
layout and hosted model catalogue verification passed. All five actual DirectML
removal protocol tests passed again on the A7 using the exact packaged JAR and
native runtime, including full Best inference. The installer was extracted into
an isolated validation folder; its JAR matched the updater exactly, no model
weights were present, and the native launcher opened the welcome window with an
isolated user profile. This validates the packaged application layout and launch,
not a production installation or the installer wizard's Windows registration.

Artifacts under `D:/SmartStockReleases/release-windows-1.1.48`:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.1.48.zip | 166501890 | `8dd4e2e72dfa04d7b97afc8badbdceb33e3404be55e6c2783aee5ad3af920af3` |
| smartstock-windows-setup-1.1.48.exe | 580066639 | `8337cf7a7a9d4db5eef2767a3e1557675c373a831a3b875aba35032f1f2447b6` |

The updater was published on 18 October 2026 after a full hosted download matched
the exact size and SHA-256. Release metadata was read back and matched version
1.1.48, build 101048, size, hash and immutable artifact path:
`updates/windows/1.1.48/8dd4e2e72dfa04d7b97afc8badbdceb33e3404be55e6c2783aee5ad3af920af3/smartstock-windows-1.1.48.zip`.

The full installer was also published on 18 October 2026 after the protected
publisher fully downloaded the hosted object and verified all 580,066,639 bytes
and its SHA-256. The permanent public Windows download was read back successfully
and selected `smartstock-windows-setup-1.1.48.exe` with the expected size. The
migration bridge and checksums accompany the local release artifacts. Neither
published artifact may be rebuilt under this version with different bytes.
Models remain separate from the installer and updater. Update the store server
before registers. This release does not install itself or restart live services.
macOS packaging, live installation, portrait/print acceptance, database/service
upgrade, printers, drawers and NFC remain separate deployment checks.
