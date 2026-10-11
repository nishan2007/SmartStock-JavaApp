# SmartStock 1.1.22 Windows

Build 101022 adds the Custom Inventory table with expandable variant rows,
inline editing and Enter to save, Sell in POS checkboxes, photo hover previews,
optional creation-date display and newest/oldest sorting. Parent rows summarize
distinct variant sizes, colours and brands, including pending edits.

Archive and Restore preserve stock, Active settings, variants and historical
orders. Archived items are hidden from normal custom-item, mobile, new-order,
POS, receiving and low-stock lists. The local server upgrade adds is_archived
and a partial index. Existing generic catalog recovery/mirror coverage retains
the new field; no cloud register-facing catalog table is introduced.

## Validation

- Maven packaging suite: 947 tests, no failures or errors, 55 skipped.
- Repository security and whitespace checks passed.
- Publishing/model/package JavaScript checks: 8 tests passed.
- Update ZIP passed layout, version, model-free and hosted model catalogue checks.
- Installer extraction and model-free checks passed; its app JAR is byte-identical
  to the updater ZIP's app JAR.
- Exact extracted installer launcher started with bundled Java 17.0.20 and an
  isolated user profile; startup diagnostics confirmed WelcomeFrame visible=true.

Interactive catalog editing/archiving against a live database, the installed
Windows service and setup wizard, hardware (printer/drawer/NFC), model downloads
and offline inference, update/rollback and macOS checks remain outstanding.
No live application, store database or Windows service was installed/restarted.

## Exact artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 149056973 | 14feb8e9047ab483bca5c11cb469402ac19802248ad6dc3ca6ae676bffcecb5c |
| All-in-one installer | 562431217 | 9fd1377d06c01e7910ddc01d3cd4a615f09d8d521dd202eb12e13ae360f590b2 |
| Model migration bridge | 3318 | 11909910946a2772567aa6674435c2410f86ee994844fef2a6c819944d61f612 |

## Publication

The Windows updater and all-in-one installer are published. Complete hosted downloads matched the exact sizes and SHA-256 hashes before release metadata and installer selection changed. The updater metadata readback matched version, build, platform, path, size, hash and published status. The public installer HEAD returned HTTP 200, the 1.1.22 filename and 562431217 bytes.

Download: https://downloads.deckers.gy/download/windows

Update the store server first, then registers. macOS publication remains outstanding.
