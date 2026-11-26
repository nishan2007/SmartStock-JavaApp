[CmdletBinding(SupportsShouldProcess)]
param(
    [ValidateSet('Plan', 'CreatePilot', 'Migrate')]
    [string]$Action = 'Plan',
    [string]$VMName = 'DeckersDrive',
    [string]$PilotPath = 'D:\SmartDrive\DeckersDrive-data.vhdx',
    [string]$MirrorPath,
    # Optional decimal-byte capacity after moving to the mirror. Guest partition
    # expansion remains a separate, explicit operation inside Windows.
    [ValidateRange(0, 64000000000000L)][long]$ExpandToBytes = 0
)

$ErrorActionPreference = 'Stop'
$pilotBytes = 300000000000L

function Get-LocalDiskPath([string]$Path) {
    if ($Path -notmatch '^[A-Za-z]:\\' -or [IO.Path]::GetExtension($Path) -ne '.vhdx') {
        throw 'Specify an absolute local .vhdx path.'
    }
    [IO.Path]::GetFullPath($Path)
}

$PilotPath = Get-LocalDiskPath $PilotPath
if ($ExpandToBytes -and $Action -ne 'Migrate') {
    throw 'ExpandToBytes is only supported when migrating to the mirror.'
}

if ($Action -eq 'Plan') {
    $volume = Get-Volume -DriveLetter $PilotPath[0]
    [pscustomobject]@{
        VMName = $VMName
        DataDisk = $PilotPath
        MaximumBytes = $pilotBytes
        HostFreeBytes = $volume.SizeRemaining
        HostFileSystem = $volume.FileSystem
        HyperVAvailable = [bool](Get-Command New-VHD -ErrorAction SilentlyContinue)
    }
    return
}

if (-not (Get-Command Get-VM -ErrorAction SilentlyContinue)) {
    throw 'Enable Hyper-V management tools before changing VM storage.'
}

if ($Action -eq 'CreatePilot') {
    $volume = Get-Volume -DriveLetter $PilotPath[0]
    if ($volume.FileSystem -notin 'NTFS', 'ReFS' -or $volume.SizeRemaining -lt ($pilotBytes + 5GB)) {
        throw 'The pilot needs an NTFS or ReFS host volume with room for the full 300 GB disk and 5 GiB headroom.'
    }
    if (Test-Path -LiteralPath $PilotPath) {
        throw "A file already exists at $PilotPath."
    }
    if ($PSCmdlet.ShouldProcess($PilotPath, 'Create dynamic pilot data disk capped at 300 GB')) {
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($PilotPath))
        New-VHD -Path $PilotPath -Dynamic -SizeBytes $pilotBytes
    }
    return
}

$MirrorPath = Get-LocalDiskPath $MirrorPath
if ([IO.Path]::GetPathRoot($PilotPath) -eq [IO.Path]::GetPathRoot($MirrorPath)) {
    throw 'Choose a destination on a different host volume.'
}
$vm = Get-VM -Name $VMName
if ($vm.State -ne 'Off' -or (Get-VMSnapshot -VM $vm)) {
    throw 'Shut down the guest and finish checkpoint merges before migrating the data disk.'
}
$attachment = @(Get-VMHardDiskDrive -VM $vm | Where-Object Path -EQ $PilotPath)
if ($attachment.Count -ne 1) {
    throw 'The selected pilot disk must be attached exactly once to the named VM.'
}
$source = Get-VHD -Path $PilotPath
if ($source.ParentPath -or $source.Attached -or -not (Test-VHD -Path $PilotPath)) {
    throw 'The source must be a healthy standalone data disk with no active mounts.'
}
if (Test-Path -LiteralPath $MirrorPath) {
    throw "A file already exists at $MirrorPath."
}
$volume = Get-Volume -DriveLetter $MirrorPath[0]
$disk = Get-Partition -DriveLetter $MirrorPath[0] | Get-Disk
$virtualDisk = @(Get-VirtualDisk | Where-Object {
    $backingDisk = $_ | Get-Disk
    $backingDisk.Number -eq $disk.Number
})
if ($virtualDisk.Count -ne 1 -or $virtualDisk[0].ResiliencySettingName -ne 'Mirror' -or
    $virtualDisk[0].HealthStatus -ne 'Healthy' -or $virtualDisk[0].NumberOfDataCopies -lt 2) {
    throw 'The destination must be on a healthy Windows Storage Spaces mirror.'
}
$targetBytes = [long]$source.Size
if ($ExpandToBytes) {
    if ($ExpandToBytes -lt $source.Size) {
        throw 'Migration cannot shrink the data disk. Choose a capacity at least as large as the existing disk.'
    }
    $targetBytes = $ExpandToBytes
}
if ($volume.FileSystem -notin 'NTFS', 'ReFS' -or $volume.SizeRemaining -lt ($targetBytes + 5GB)) {
    throw 'The mirror needs room for the complete target virtual disk capacity plus 5 GiB headroom.'
}
if ($PSCmdlet.ShouldProcess($VMName, "Copy and verify the data disk on $MirrorPath, set capacity to $targetBytes bytes, then switch the VM attachment")) {
    $destinationFolder = [IO.Path]::GetDirectoryName($MirrorPath)
    [void][IO.Directory]::CreateDirectory($destinationFolder)
    $staged = Join-Path $destinationFolder ("smartdrive-" + [guid]::NewGuid() + ".vhdx")
    try {
        [IO.File]::Copy($PilotPath, $staged)
        if ((Get-FileHash -LiteralPath $PilotPath).Hash -ne (Get-FileHash -LiteralPath $staged).Hash -or
            -not (Test-VHD -Path $staged)) {
            throw 'The copied data disk failed verification.'
        }
        if ($targetBytes -gt $source.Size) {
            Resize-VHD -Path $staged -SizeBytes $targetBytes
            if (-not (Test-VHD -Path $staged) -or (Get-VHD -Path $staged).Size -ne $targetBytes) {
                throw 'The copied data disk failed verification after expansion.'
            }
        }
        [IO.File]::Move($staged, $MirrorPath)
        if ((Get-VM -Name $VMName).State -ne 'Off') {
            throw 'The VM started during the copy. The verified destination is retained for review.'
        }
        Set-VMHardDiskDrive -VMHardDiskDrive $attachment[0] -Path $MirrorPath
        Write-Output "Data disk switched to $MirrorPath. The original remains at $PilotPath."
        if ($targetBytes -gt $source.Size) {
            Write-Output 'Start the guest and extend its existing data partition in Disk Management to use the added capacity.'
        }
    } finally {
        if (Test-Path -LiteralPath $staged) {
            Remove-Item -LiteralPath $staged
        }
    }
}
