# Background removal performance

The existing Best BiRefNet model, 1024 × 1024 input, ImageNet normalization,
sigmoid/alpha conversion, original RGB pixels, and optional edge refinement are
preserved. No smaller model, reduced resolution, or quantized weights are used.

## Runtime reuse and GPU removal

Foreground removal and queued preparation share one serialized removal session.
The verified session stays loaded between jobs. Model path, size, file identity,
modified time, quality or effective CPU/GPU mode changes replace and close the
old session. Managed models still go through the catalogue integrity verifier;
unchanged model files do not need a full hash on every warm job. One native
resource is retained at a time and closed after 15 minutes idle or on shutdown.
Failures and cancellation discard cached resources rather than reusing a broken
worker. CPU-only requests remain CPU-only; Auto and GPU-preferred share a cache.

The isolated DirectML worker now accepts either existing enhancement tiles or a
fixed 1024-square RGB removal tensor. The removal response must contain exactly
one 1024-square float mask. DLL verification and the stripped child environment
remain unchanged; staff credentials and database configuration are never sent
to the child. GPU startup/inference failures close the worker and fall back to
the existing CPU model. Device progress reports completion only after GPU work
actually succeeds; the benchmark cannot count a fallback as GPU verification.

Logs include model verification/loading time, native session initialization,
mask time (including cold initialization), and total removal/PNG time. No image,
staff name, upload token, or personal filename is logged.

## Queue preparation

New uploads enter `pending`; older unprocessed entries are also eligible. A
single low-priority background worker starts after 30 seconds and checks every
three seconds. It prepares the oldest unprinted photo using Best without edge
cleanup. The shared image semaphore prevents overlapping native jobs. An idle
server can warm Best once after startup when memory permits. CPU capacity remains
limited to leave room for store operations.

Cold background work requires at least 3 GiB of free physical RAM and 256 MiB
Java heap headroom. A warm Best session requires 768 MiB free physical RAM and
the same heap headroom. These checks are separate because model/native GPU
allocations are outside Java's heap. Under pressure, preparation stays pending;
staff can still view or use the original. Foreground cold loads fail clearly if
native RAM/heap reserves are inadequate. Missing models are not downloaded or
installed automatically.

Queue states show pending, processing, Best cutout ready, or failed. Default GET
and Passport Pictures selection use a ready cutout; **Use original photo** bypasses
it, and **Retry Best cutout** explicitly retries failures. The original is retained.
Both images share the existing first-print 24-hour deadline. A deletion/expiry
while processing prevents the result from recreating that photo. Cleanup runs
independently of the processing thread, including while a slow inference runs.

## Validation and benchmark

Tests cover warm reuse, cache invalidation, native resource cleanup, idle eviction,
memory admission, queue readiness/original preservation, busy retries, failure
retry, and deletion during inference. A tiny self-authored ONNX graph exercises
the actual CPU 1024-square mask protocol, repeated calls and original RGB/size
preservation. It is a protocol fixture, not a quality or Best-model speed test.
An opt-in DirectML test compares the same small graph against CPU with a maximum
alpha difference of one level; enable with `-Dsmartstock.test.removalGpu=true` on
a suitable Windows test machine. This real DirectML protocol test passed on the
development Radeon GPU with the tiny graph. Routine tests skip hardware-dependent
checks. Passing it verifies the GPU transport/session path, not full-model speed
or BiRefNet quality equivalence.

`tools/StudioRemovalBenchmark.java` runs one cold and two warm jobs for CPU and GPU
with the pinned Best model and an operator-selected representative photo. It
writes CPU/GPU PNGs, reports wall time and alpha differences, and refuses a cold
benchmark without 3 GiB physical RAM/256 MiB heap headroom. Compile it against
the current application classes and runtime dependencies, then run the packaged
classpath with these arguments:

```
services.StudioRemovalBenchmark <verified-best.onnx> <photo.png-or-jpg> <output-directory>
```

On 2026-10-18 the development computer had approximately 0.15–1.3 GiB free
physical RAM during development, including after SmartStudio closed. A full Best
CPU/GPU benchmark was therefore deferred;
no 2–5 second result or full-model GPU quality equivalence is claimed. The earlier
192-square acceleration numbers concern enhancement only. Full Best timing,
GPU fallback behavior on target hardware, and representative hair/edge review
remain required before claiming a latency target. The original runtime-reuse and
GPU-removal implementation was subsequently packaged in SmartStock 1.1.46 and
carried into 1.1.47; the benchmark and correction below supersede the initial
deferral for the tested Windows hardware.

## A7 full-model benchmark and DirectML correction — 2026-10-18

An operator-authorized queued portrait was tested on a GEEKOM A7 with Radeon
780M graphics (driver 32.0.21030.2001), using the installed SmartStock 1.1.47 Java
runtime and isolated benchmark classes/resources outside the application folder.
The Best model was 972,666,916 bytes and matched catalogue SHA-256
`58f621f00f5d756097615970a88a791584600dcf7c45b18a0a6267535a1ebd3c`.

The original DirectML 1.23.0 runtime failed to load BiRefNet with the upstream
[constant-tensor regression](https://github.com/microsoft/onnxruntime/issues/26261).
Basic graph optimization avoided loading failure but a fused DirectML partition
then failed inference with `8007000E` (insufficient memory resources).

The correction pins the official Microsoft DirectML ONNX Runtime package to
1.24.4 with SHA-256 verification and retained notices. Background removal sets
`ep.dml.disable_graph_fusion=1`, a supported provider option, to avoid the large
fused allocation. Enhancement retains its existing fusion setting. The model,
1024-square input, normalization and alpha conversion are unchanged. The existing
CPU fallback remains available. Java's CPU runtime remains 1.23.2; the isolated
DirectML worker successfully used its JNI with the newer native runtime.

| Execution | Cold | Warm 1 | Warm 2 |
| --- | ---: | ---: | ---: |
| CPU | 27.419 s | 19.818 s | 19.630 s |
| DirectML, confirmed GPU completion | 15.045 s | 5.228 s | 5.139 s |

Warm GPU mean was 5.184 seconds versus CPU 19.724 seconds (about 3.8 times faster).
Maximum alpha difference was 1/255; mean absolute difference was 0.00003 on the
0–255 scale. These are one-photo hardware measurements, not a universal latency
or portrait-quality guarantee. Fine-hair/print acceptance remains an operator
check. No personal photo, output cutout, or queue metadata belongs in Git.

Validation: full Maven suite 1,111 tests, zero failures/errors, 79 skips;
repository security and whitespace checks passed. All five removal protocol tests
passed on the A7 with real DirectML enabled, including the new opt-in full pinned
Best-model inference regression (no CPU fallback accepted). A real DirectML
enhancement smoke test also passed with the new runtime. Enable the full-model regression with
`-Dsmartstock.test.removalGpu=true` and
`-Dsmartstock.test.bestRemovalModel=<verified-best.onnx>` on suitably provisioned
Windows hardware. The full benchmark exited successfully. Isolated results are in
the A7's `SmartStockBenchmarks/removal/verified-20261018-213048` folder.

This correction was packaged and published as SmartStock 1.1.48; see
[release verification](release-1.1.48.md). It has not been installed into the live app.
No live application/service restart or database/hardware transaction was needed
for the isolated test; macOS and installed-app acceptance remain outstanding.
