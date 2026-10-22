# Image Studio acceleration

The store server can run the existing Real-ESRGAN model through DirectML on
Windows x64. SmartStudio and browser Image Studio offer Auto, CPU only, and GPU
preferred, with authenticated progress polling and cancellation. Older clients
retain the synchronous image API. Older servers do not advertise progress and
the new client leaves the processing controls disabled.

GPU inference runs in an isolated child JVM with a binary, bounded tile protocol.
It receives the existing verified model path and RGB tiles, without staff tokens,
database credentials, or server configuration. GPU initialization failure, native
process exit, and tile timeouts fall back to CPU. Cancellation kills an active GPU
worker; CPU enhancement cancellation is checked between tiles. Background removal
now has a full-resolution mask mode in that isolated worker. GPU removal can be
cancelled by terminating the worker; CPU removal cancellation takes effect after
inference returns. These removal changes require a new application release.

The Windows Maven profile stages ONNX Runtime DirectML 1.23.0 and DirectML 1.15.4
from SHA-256-pinned NuGet archives, plus their licences and third-party notices,
into application resources. No ONNX weights are packaged. The existing Java
1.23.2 JNI binding uses the compatible C API in the isolated worker; JNA calls the
DirectML factory because the Maven JNI binary was compiled without USE_DML.
The pinned SessionOptions native handle is accessed only inside that worker.
The main store process retains its existing CPU runtime.

Runtime DLLs are verified when extracted to a unique temporary directory, removed
after worker termination, and never placed in model or rollback folders. GPU
startup has a 120-second deadline and each tile has a 180-second deadline.
Failure is recorded in the server logger. Available physical RAM and Java heap
headroom choose 48, 96, or 160-pixel source tile cores. An ONNX memory allocation
failure gets one retry on CPU with 48-pixel cores; Java heap exhaustion and
unrelated model errors are not silently retried. CPU inference uses at most
four threads, reduces to two under memory pressure, and reserves CPU capacity for
store operations. Outputs retain the existing 24-megapixel limit. Integrated GPU
memory shares system RAM; acceleration does not replace sufficient RAM.

Progress contains stage, device, completed/total tiles, percentage and a remaining
time estimate after two tiles. It contains no image content. LAN progress requires
the approved device and staff permission and is scoped to device/user. Browser
progress requires the authenticated session, staff permission and CSRF token and
is scoped to that browser session. Other owners receive only an unknown state.
Completed progress expires after ten minutes when a subsequent job is created;
the registry is bounded to 32 entries. Image processing remains one job at a time.

Benchmark on the development AMD Radeon integrated GPU: a synthetic 192×192 image
at 2× took 13.359 seconds CPU and 3.802 seconds GPU, including setup. Alpha matched
exactly; maximum RGB channel difference was 1/255 and mean difference 0.0002.
This is a small synthetic benchmark, not a prediction for large customer photos.
`tools/StudioAccelerationBenchmark.java` reproduces it with the verified local model.

The exact 1.1.23 update JAR and its dependencies were also exercised using the
Java runtime extracted from its Windows installer: the same 192×192 comparison
took 9.327 seconds CPU and 4.610 seconds GPU, with identical alpha and the same
maximum 1/255 colour difference. GPU speed varies with memory pressure and other
workloads. Source validation after the idempotent session-cleanup correction:
952 SmartStock tests, zero failures/errors, 51 skipped, with both real pinned
models supplied; all 28 SmartStudio tests pass. Repository security, JavaScript
syntax and whitespace checks pass. Source UI controls were visually checked at
820 pixels. The final cleanup correction still requires inclusion in the next
packaged release; this task did not install or publish that correction.

Release publication and live installation are separate tasks. Validate the exact
Windows package, large real photos, low-memory operation and customer-facing
progress/cancellation against a live authorized store before broad rollout.
macOS remains CPU and requires its own packaging validation.


2026-10-19 enhancement benchmark on the development AMD Radeon Graphics PC:
192 × 192 synthetic RGBA input, 2× output (384 × 384), Java 17.0.20,
768 MB maximum Java heap, approximately 1.2 GB free physical RAM before starting.
One fresh enhancer per backend, including model verification and session/worker
setup: CPU 16.540 s; DirectML GPU 4.372 s (3.78× faster). All four GPU tiles
completed on DirectML. Alpha matched exactly; maximum RGB difference 1/255,
mean 0.0002/255. Outputs are local ignored build artifacts in
`target/enhancer-benchmark/run-192`. These are synthetic development timings,
not measurements of a customer photo or the installed store application.
The benchmark now requires GPU completion, so a mid-run CPU fallback cannot be
reported as a successful GPU timing.
