# A7 NPU background-removal feasibility — 2026-10-19

The operator requested a CPU/GPU/NPU comparison using the authorized queued
portrait. This experiment was isolated under the A7 user's
`SmartStockBenchmarks/npu-comparison`, outside the application and active model
profile. No driver, service, production model or live application was changed.
Do not commit the photo, calibration tensor, converted weights or runtime files.

## Setup and firmware

Hardware: Ryzen 9 7940HS, Radeon 780M, Phoenix NPU. Installed NPU driver was
32.0.203.240. AMD's newer driver was staged but not installed; the documented
Ryzen AI 1.8 driver minimum remains an outstanding deployment prerequisite.

The official AMD-signed 1.8 installer was transferred with matching SHA-256
`14ae3b53c6592f5c1eb6339afa273234c1e6adbd400bf9f6791b512619a27760`.
MSI administrative extraction avoided product custom actions. An isolated Python
3.12.10 environment loads AMD's onnxruntime-vitisai 1.27.0 and voe 1.8.0.

The 1.8 package lacked the documented Phoenix firmware. The signed official
1.7.1 installer, SHA-256
`4a7281b5ce1dc5f4784b5635449fd4f36e9f06d417190027781a36fe64ed0496`,
contained `voe-4.0-win_amd64/xclbins/phoenix/4x4.xclbin` (4,149,726 bytes).
Using that file eliminated the missing-firmware failure. This matches the
workaround reported in [AMD issue #400](https://github.com/amd/RyzenAI-SW/issues/400).

The small AMD quick test completed and its ORT profile recorded three
VitisAIExecutionProvider kernel events. This is provider-execution evidence,
not proof that every operation ran physically on the NPU, and not a full-model
background-removal benchmark. The initial test with missing firmware also
reported success, so provider availability and a zero exit alone are insufficient.

## Full-model conversion attempts

The Phoenix route needs INT8 conversion. A separate experimental model was to be
calibrated using the one authorized portrait, with exactly the Java 1024-square
bicubic resize and ImageNet normalization. One-image calibration is exploratory;
it cannot certify general portrait/hair quality. The production FP32 model and
its persistent profile file remained unchanged.

1. AMD Quark 0.11.1 XINT8/MinMSE conversion reached calibration but failed allocating
   an 822,083,584-byte GatherND buffer. Quark reported 98.2% host memory usage.
   No converted model was produced; the process exited and memory recovered.
2. A retry used NonOverflow calibration, `CalibOptimizeMem`, one calibration
   worker/sample, a dedicated temporary folder, and disabled optional CLE.
   A supervisor stopped only the experimental process when free physical memory
   fell below the 5 GiB reserve. No converted model was produced.

No NPU inference timing, output cutout or CPU/NPU quality comparison is available.
Do not label a CPU fallback as NPU execution or reduce resolution silently to
claim a like-for-like result.

| Route | Warm mean | Status |
| --- | ---: | --- |
| CPU | 19.724 s | Existing verified Best-model portrait benchmark |
| Radeon GPU | 5.184 s | Existing verified Best-model portrait benchmark |
| Phoenix NPU | unavailable | Full-model preparation failed memory admission |

See [verified CPU/GPU measurements](background-removal-performance.md). These are
the earlier one-photo measurements, not a new three-way benchmark. Keep the GPU
route for current use. Further NPU work needs offline conversion with a larger
memory budget or a demonstrably lower-peak calibration pipeline, followed by
driver compatibility, actual offload, latency and portrait-quality verification.
Evidence and logs remain on the A7, including `comparison-status.json`.
