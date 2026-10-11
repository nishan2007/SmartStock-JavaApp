# POS-00 setup: ChatGPT, SmartStudio and Asure ID conversion

## 1. Install ChatGPT on the computer running Asure ID

Do this on **POS-00**, where Asure ID 7 and its existing template collection are
available. Open the [official Windows installation page](https://learn.chatgpt.com/docs/windows/windows-app),
use its download link, install the ChatGPT desktop app, and sign in with your
ChatGPT account. If you prefer a command, the official page provides:

```powershell
winget install --id 9PLM9XGG6VKS -s msstore
```

Choose a local Work or Codex chat and open the local conversion folder described
below. This gives the assistant a specific folder to work with. See the
[official desktop quickstart](https://learn.chatgpt.com/docs/app).

In the app's plugin settings, install/enable **Computer Use** if it is available
for this computer and your account. On Windows, keep Asure ID visible while it
runs. Start the export request with `@Computer`. These steps come from the
[official Computer Use guide](https://learn.chatgpt.com/use-cases/use-your-computer-with-codex).
If the plugin cannot control native Windows apps, use the manual PDF fallback in
step 4; installing ChatGPT alone does not guarantee access to Asure ID.

## 2. Install the SmartStudio build containing Badge Cards

Copy the setup ZIP to POS-00 and extract it into a local folder. Ask ChatGPT to
run `Verify-SetupKit.ps1` there before installation; it checks the kit files
against their recorded sizes and SHA-256 checksums.

Use the **SmartStudio-setup-0.1.5.exe** prepared with this guide, not the older
installers already in DECKERS_SK. Close SmartStudio and run the installer as the
Windows user who will use it. It includes its Java runtime and installs under
that user's Local AppData Programs folder. It does not install a store server.

Open SmartStudio. It can reuse this computer's saved SmartStock register pairing.
Otherwise choose the store server and use the normal pairing process. In
SmartStock Device Management, enable **Allow Studio** for POS-00. Then choose
**Connect / check approval** and sign in with your own SmartStock staff account.
You should see **Background removal** and **Badge Cards** tabs.

The existing server must already support SmartStudio pairing and staff login.
If it does not, have the server upgraded through the normal store procedure.
Do not paste database credentials or server keys into ChatGPT or SmartStudio.

## 3. Choose the ONE badge-template folder

Create **SmartStudio\Badge Templates** in the shared OneDrive location all stores
will use. For example, it may appear on this computer as:

```text
OneDrive - DECKERS\SmartStudio\Badge Templates\
    Skeldon Primary School.sstbadge
    Another School.sstbadge
    Company Staff - Front.sstbadge
    Company Staff - Back.sstbadge
```

Use the same shared OneDrive folder at every store, even when its local path
differs. In OneDrive, make this folder available locally on each computer.
In **SmartStudio > Badge Cards > Template folder**, select that folder. SmartStudio
remembers the selection for this Windows user. **Save template** saves directly
there, and the **Templates** list shows all `.sstbadge` files directly in it.
Click **Refresh** after the assistant adds files or OneDrive receives changes.

Copy the setup kit's `Badge Templates\Skeldon Primary School.sstbadge` into your
chosen folder as the first example. Every template carries its artwork inside
the file: no separate school/logo folders are required. Exports made for other
people can go elsewhere, but the working library remains this single folder.
Issued card PNG/PDF exports start in local **Documents\SmartStudio\Badge Output**,
separate from the reusable template library.

OneDrive handles file transfer between stores. This is file sharing, not the
unfinished SmartStock server-managed template sync. Edit a shared template on one
computer at a time and let OneDrive finish syncing before opening it elsewhere.
SmartStudio detects a changed local file before saving and asks you to preserve
your draft by exporting it rather than silently overwriting that change. It
cannot detect a remote edit that OneDrive has not delivered yet.

## 4. Prepare a separate LOCAL conversion workspace

Create **Asure ID Conversion** in local Documents, outside the shared template
folder. Keep private source material and production badge data out of the shared
library. Put these setup-kit files into the workspace: this guide,
`Conversion instructions for ChatGPT.txt`, `badge-template-format.md`, and
`Invoke-BadgeTemplateTool.ps1`.

```text
Documents\SmartStudio\Asure ID Conversion\
    Sources\         original XTP copies and reference PDFs
    Candidates\      editable conversions awaiting comparison
    Previews\        rendered review images
    Reports\         inventory and conversion status
```

Tell the assistant the full paths to this workspace and the one Badge Templates
folder. Read `Conversion instructions for ChatGPT.txt`, copy its request into a
local chat, and replace the two path placeholders. The assistant should first
inventory **all templates visible inside Asure ID**, not just exported files on
disk, then convert one template to prove the workflow before continuing through
the collection. It can create JSON template files directly, using the format
guide and installed validation tool, instead of entering every layer by hand.

Manual fallback: for each Asure ID design, open it, retain an exported `.xtp`
copy, and print its template preview with **Microsoft Print to PDF**. Save a PDF
with the design's name in `Sources`, including both sides where applicable.
Prefer empty/synthetic sample values. Do not send a job to the physical badge
printer during conversion. The assistant can then inspect those PDFs and rebuild
the editable designs. `.xtp` files alone are not yet a supported direct import.

## 5. Review and finish the collection

For each template, compare the complete rendered preview to the Asure ID source:
card size, orientation, border, headings, fonts, logo, photo position and variable
fields. Enter fictitious names and details in SmartStudio to test the fields.
Move a conversion into the one Badge Templates folder only after this comparison
passes. The assistant should keep an inventory report so missing or unsupported
templates are visible rather than silently omitted.

SmartStudio now generates entry boxes for every named text field. This initial
version uses one photograph per card and one side per `.sstbadge` file. Preserve
front/back designs as separate named files. Variable barcodes, encoding and other
unsupported features need review; ordinary visual reconstruction does not restore
those capabilities. Native XTP import, automated duplex output and batch badge
production are not implemented in this build.

Finally export a test card and check it at **actual size** on the intended printer.
Confirm another store receives and opens the same template after OneDrive sync.
Neither native Asure ID automation on POS-00 nor live printing/synchronization has
been verified by preparing this kit. No installation or release publication has
been performed on POS-00.
