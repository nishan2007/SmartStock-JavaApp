# File tab proposal

A design-only interactive HTML mockup; no application code changed for this proposal.

File precedes Home. Home stays selected when a workspace opens. File opens a separate file-management view; Back to Home, Home or Escape returns to editing. The Home ribbon remains unchanged.

Commands: File info, New, Open, Recent files, Save, Save As, Rename, Import, Export, Print, Close file. Account/settings remain under Account.

Save As has a new filename, extension, destination (local folder/shared template library), folder/library, and native format. It creates a separate copy and switches editing to it. Existing-name collisions require replacement confirmation. Format-appropriate print setup, page/layer state, fields and records follow existing serialization and privacy rules. PDF/PNG remain exports.

Rename has a new name and read-only extension/current location. It renames the active local file or shared label without changing content, identity or location. An unsaved draft goes through Save As. Name validation, writable access, online shared availability and conflicts must be enforced in implementation. Preserve existing verified-save requirements.

File info shows name, location, workspace, format, dimensions, pages, last-saved time and unsaved status. New/Open/Close guard unsaved changes with Save/Discard/Cancel. Recent items are store/profile scoped. Imports/exports/printing expose only supported workspace operations. Proposed shortcuts: Ctrl+S Save, Ctrl+Shift+S Save As.

Preview links: file-tab-mockup.html#home, #save-as, #rename, #info. All action confirmations in the mockup show a preview-only toast and never write files. Desktop two-column content becomes one column on smaller displays, with action buttons outside the scrolling content. Synthetic document content only.

Validation: static JavaScript compilation, HTML IDs/navigation targets, icon font resolution. Browser rendering/interaction validation was blocked by local-file URL security policy; no workaround was attempted. Native Swing implementation, rename persistence, saving, and live shared permissions are not implemented or verified by this mockup.

## Native implementation

Implemented in the shared Swing File view. Native preview: `SmartStudio/target/file-view.png`. Component/navigation checks cover all template editor variants and confirmation-button bounds at laptop, monitor and compact logical sizes. Local badge/publication Save As and rename tests verify independent copies, content retention, conflict checks and fingerprints. Full Studio regression, focused shared-library tests, security and whitespace checks passed. Shared live writes and physical printing remain separate acceptance; this change is not published. Recent items currently cover local files opened/saved during the current signed-in session and are cleared on sign-out.
