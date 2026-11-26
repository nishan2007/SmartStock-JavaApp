# Pilot and mirrored storage

## Current computer, checked 9 November 2026

Windows reports an AZW EQ computer, a 500 GB Crucial NVMe system disk and a
1 TB Toshiba USB disk. The Toshiba NTFS volume is D: with approximately
711 GB free. C: has approximately 25 GB free. Hardware inventory does not
establish whether this chassis has internal 3.5-inch SATA bays.

Use D: for the pilot VM and its separate data disk. The data VHDX is dynamically
allocated with a maximum of 300,000,000,000 bytes (about 279 GiB as Windows
displays capacity), before guest filesystem overhead. This limit covers staff
files, scans, local backup snapshots, recovery files and preview caches together.
The VM's separate 80 GiB Windows disk is additional host storage. Neither step
requires reformatting the Toshiba drive or deleting its existing files.

From an administrator PowerShell window, in SmartDrive:

```powershell
.\tools\Get-StorageReadiness.ps1
.\tools\Manage-Storage.ps1 -Action Plan
.\tools\Manage-Storage.ps1 -Action CreatePilot -WhatIf
.\tools\Manage-Storage.ps1 -Action CreatePilot
```

Then use Initialize-VM.ps1 with a licensed Windows ISO and an existing external
Hyper-V switch that retains the host's management adapter, as described in
README.md. The new Gen2 guest gets Microsoft Secure Boot, a new local key
protector and virtual TPM, with security readback before the data disk is
attached. An incomplete setup is retained for review and automatic start is
disabled when possible; no VM is booted by the setup tool. Retain protected
VM/key-protector recovery material and any guest BitLocker recovery keys with
independent recovery records, outside Git. Never replace an existing encrypted
guest's key protector merely to retry setup. Inside the guest, initialize only the
new data disk as GPT/NTFS and assign D:. Config paths such as
`D:\Shares\DeckersDrive` refer to the guest disk; the host D: is a different
volume. Keep the USB drive connected while the VM is running. Shut down the VM
before disconnecting it. Set up independent backup before storing live designs.

## Connecting the future DVR disks

For SATA DVR disks, use a powered, cooled two-bay enclosure supporting their
physical size and capacity. It must expose two independent disks with distinct
identities to Windows (individual-disk mode); confirm Storage Spaces support
with the enclosure manufacturer before purchasing. A 3.5-inch drive needs an
external power supply. Internal SATA is also an option if a physical inspection
confirms suitable bays, SATA ports and power connectors.

For this AZW EQ host, the external enclosure is the practical default until the
exact chassis and internal connectors have been physically checked. Do not buy
an internal SATA adapter on the assumption that an M.2 slot supports it. Check
the DVR drive labels first: this recommendation assumes ordinary SATA disks,
not SAS disks or a proprietary DVR interface.

The enclosure buying checklist is: two suitable drive bays, its own power
supply, active cooling, USB 3 connectivity, distinct persistent disk identities,
SMART/diagnostic pass-through, and explicit vendor confirmation of Windows
Storage Spaces compatibility. Use individual-disk/JBOD mode, with no built-in
RAID or combined-span mode. Connect directly to the computer rather than an
unpowered hub. A shared enclosure, power supply or USB cable is still a common
failure point for both mirror members; the independent backup must be elsewhere.

When connected, run Get-StorageReadiness.ps1 again. Check both disk model/serial
numbers, health and CanPool status. A CanPool value alone does not establish
drive health. Run each drive manufacturer's extended diagnostic and inspect
SMART results before reuse. Preserve any required DVR recordings first: creating
the new pool will erase the selected disks. Never select the Toshiba or system
disk. If Windows cannot pool the disks, resolve enclosure compatibility first.

Create a two-way Storage Spaces mirror using the two tested 4 TB disks on the
host, giving about 4 TB usable before formatting and reserved space. Assign a
stable host letter such as M:. This protects against one member disk failing;
keep the independent backup as well.

## Moving SmartDrive later

Stop file access and backup/scan jobs, verify the backup, then shut down the VM.
Run the existing migration helper:

```powershell
.\tools\Manage-Storage.ps1 -Action Migrate -MirrorPath 'M:\SmartDrive\DeckersDrive-data.vhdx' -WhatIf
.\tools\Manage-Storage.ps1 -Action Migrate -MirrorPath 'M:\SmartDrive\DeckersDrive-data.vhdx'
```

To move and increase the data disk in the same operation, add `-ExpandToBytes`.
For example, the following requests 3.5 TB, leaving some capacity on a nominal
4 TB mirror for the VM operating-system disk and host overhead. Choose the final
capacity from the actual formatted mirror's available space:

```powershell
.\tools\Manage-Storage.ps1 -Action Migrate -MirrorPath 'M:\SmartDrive\DeckersDrive-data.vhdx' -ExpandToBytes 3500000000000 -WhatIf
.\tools\Manage-Storage.ps1 -Action Migrate -MirrorPath 'M:\SmartDrive\DeckersDrive-data.vhdx' -ExpandToBytes 3500000000000
```

Expansion happens only on the verified copy, before switching the VM attachment.
The tool rejects shrinking, an unhealthy mirror, or insufficient room for the
entire requested capacity. Inside the guest, open Disk Management, identify the
existing SmartDrive data volume by its label and contents, and use **Extend
Volume** to consume the added unallocated space. Do not initialize or format
that existing disk. If Extend Volume is unavailable, stop and inspect its
partition layout before making changes.

The helper checks mirror health, copies the data disk, compares SHA-256 hashes,
checks VHDX validity, and switches its VM attachment. It retains the Toshiba
copy. The guest drive letter, application configuration, scanner destinations,
workstation credentials and network share paths stay the same. If the optional
expansion was omitted, increase the copied VHDX and extend the guest partition
later after checking mirror capacity. Move the OS VHDX using
Hyper-V's storage move facility if retiring the Toshiba completely. Verify
client access, previews, scans and a backup/restore before reopening normal use.

## Readiness verification, 9 November 2026

The read-only storage plan found the Toshiba D: volume to be NTFS, with
711,178,678,272 free bytes, and found Hyper-V tools. The CreatePilot dry run
selected `D:\SmartDrive\DeckersDrive-data.vhdx` with a 300 GB maximum. Synthetic
migration policy checks passed for expansion and rejection of shrinking,
insufficient capacity and a degraded mirror. These checks did not create a
virtual disk or VM, format a drive, or prove enclosure/physical-disk behavior.
The guest still needs a licensed Windows ISO and administrator-run deployment.

References: [Microsoft Storage Spaces guidance](https://support.microsoft.com/en-us/windows/experience/storage-filemanagement/storage-spaces-in-windows)
and [standalone deployment guidance](https://learn.microsoft.com/windows-server/storage/storage-spaces/deploy-standalone-storage-spaces).
