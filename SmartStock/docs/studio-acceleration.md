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
worker; CPU cancellation is checked between tiles. Background removal remains CPU
processing and its cancellation takes effect after that inference returns.

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
headroom choose 48, 96, or 160-pixel source tile cores. CPU inference uses at most
An ONNX memory allocation failure gets one retry on CPU with 48-pixel cores;
Java heap exhaustion and unrelated model errors are not silently retried.
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

Release publication and live installation are separate tasks. Validate the exact
Windows package, large real photos, low-memory operation and customer-facing
progress/cancellation against a live authorized store before broad rollout.
macOS remains CPU and requires its own packaging validation.
