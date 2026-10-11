# SmartStock 1.1.21 Windows

Build 101021 includes pending custom-order pricing, optional deposits, server-side
deferral and delivery approvals, price finalization, excess-deposit refunds, and
pending-price labels on order screens, customer history and slips. It includes
the current workspace's custom-order inventory, quantity, details, attachment,
submission, area pricing and orientation changes, together with the current
catalog cache, grouped-product, price-tag, hardware, web-status, mobile image,
updater and Studio processing changes.

## Validation

- SmartStock Maven packaging suite: 943 tests, no failures or errors, 55 skipped.
- SmartStudio Maven suite: 28 tests, no failures, errors or skips.
- Publishing/model/installer JavaScript checks: 16 tests passed.
- Repository security and whitespace checks passed.
- Pending-pricing disposable PostgreSQL integration checks passed before release
  preparation, including permissions, invalid/expired/consumed approvals,
  production, delivery, refunds, idempotency and concurrent payments.
- Update ZIP passed layout, version, model-free and hosted model catalogue checks.
- Installer extracted successfully; its application JAR is byte-identical to the
  update ZIP's JAR. Every JAR entry also matches the Maven artifact; archive
  timestamps differ after the local dependency installation.
- Exact installer launcher started with bundled Java 17.0.20 and an isolated user
  home; its startup log confirmed the welcome window was visible.
- Both application packages contain no ONNX model weights. Model catalogue and
  hosting remain separate.

Interactive pending-order entry/finalization/approval acceptance, the full setup
wizard, installed Windows service, live-store schema upgrade, printer/drawer/NFC,
model downloads/offline inference, update/rollback and macOS checks remain
outstanding. No live store application or service was installed or restarted.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 149051023 | 4fd8f08584ec7d841bdaff13e6bd79f8778609f7618f5f10c59091bbf4826780 |
| All-in-one installer | 562422199 | 2a500d9e28ca3b24c15b9fa901481394ac77f792b482d86fa3757e4dbcb0ffeb |
| Model migration bridge | 3318 | f3d6ec11031c29bdec4bbced9b015098db68c6c8f049661a813555649b627003 |

## Publication

The Windows updater and all-in-one installer are published. Full hosted downloads
matched the exact local byte sizes and SHA-256 hashes before metadata and the
public installer selection changed. The update release row was read back and
verified against version, build, platform, size, hash and published status.
The public download HEAD returned HTTP 200, the 1.1.21 filename and 562422199 bytes.

Download: https://downloads.deckers.gy/download/windows

Update the store server first, then registers. macOS publication is outstanding.
