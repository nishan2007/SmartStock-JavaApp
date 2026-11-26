[CmdletBinding()]
param([ValidatePattern('^[A-Za-z]$')][string]$PilotDrive = 'D')
$ErrorActionPreference = 'Stop'
# Inventory only: never initializes, formats, or adds disks to a pool.
$computer = Get-CimInstance Win32_ComputerSystem
$volume = Get-Volume -DriveLetter $PilotDrive
$physical = @(Get-PhysicalDisk | Select-Object FriendlyName, SerialNumber, BusType,
    MediaType, Size, HealthStatus, OperationalStatus, CanPool, CannotPoolReason)
[pscustomobject]@{
    Computer = "$($computer.Manufacturer) $($computer.Model)"
    PilotDrive = "$($PilotDrive):"
    FileSystem = $volume.FileSystem
    FreeBytes = $volume.SizeRemaining
    DataMaximumBytes = 300000000000L
    # Reserve the complete data disk plus 80 GiB OS disk and 10 GiB headroom.
    RequiredFreeBytes = 300000000000L + 90GB
    PilotCapacityReady = ($volume.FileSystem -in @('NTFS', 'ReFS') -and
        $volume.SizeRemaining -ge (300000000000L + 90GB))
    HyperVToolsPresent = [bool](Get-Command New-VHD -ErrorAction SilentlyContinue)
    PhysicalDisks = $physical
} | ConvertTo-Json -Depth 5
