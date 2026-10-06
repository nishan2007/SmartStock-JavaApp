# SmartStock 1.0.249 — custom order attachments

Windows update containing the current application source and resources, including the changes shipped in 1.0.248.

Custom-order attachment uploads now accept offset zero for the first chunk. Saved attachment and design-preview downloads use the same nonnegative integer validation. Negative, fractional, malformed, missing, and overflowing offsets remain invalid; identifiers still require positive values.

Files & Design Approval is beside Order Details. The media window initially shows files and proofs from all order lines. Adding files, submitting previews, and printing an approved preview require a specific line selection. Images from earlier failed uploads must be attached again after the server is updated; this release does not recover missing file contents.

Validation: the Maven packaging suite passed (849 tests, zero failures, zero errors, 47 skipped). Repository security and whitespace checks passed. The update ZIP contains the tested JAR with matching SHA-256. The existing Windows native launcher and runtime successfully launched the extracted update in an isolated temporary app copy and user home; startup evidence confirmed the welcome window was visible. The packaged server validator accepted offset zero and a subsequent chunk offset. Archive validation confirmed portable paths, runtime dependencies, pinned cloudflared, and offline image-processing models.

Artifact: `smartstock-windows-1.0.249.zip`, 1,114,356,270 bytes; SHA-256 `307c66ae6b9c24978c767632125ba3e1e7b9699ae66e47b4d90fcbc91621ff09`.

Publication uses the protected verified-release publisher, which downloads the complete stored artifact and verifies size and SHA-256 before inserting update metadata.

This is a Windows update ZIP. No new installer or macOS artifact was built. Applying the update through the installed application's updater, production attachment upload/download, backup/restore, Windows services, printers, cash drawers, and NFC hardware remain separate live verification steps. Update the store server to enable the server-side attachment fix. Publication does not update running installations automatically.

Publication completed: the publisher downloaded and verified the entire stored artifact before metadata insertion. Independent readback confirmed Windows version 1.0.249, build 100249, published status, size, SHA-256, and artifact path. The isolated smoke app was stopped; automated policy blocked cleanup of its temporary copy, which remains under the Windows temporary directory.
