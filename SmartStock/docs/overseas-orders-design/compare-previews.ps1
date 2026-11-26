param([string]$CaptureDirectory = (Join-Path $PSScriptRoot '../../target/overseas-design'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
foreach ($screen in @('list', 'detail', 'quote', 'item')) {
    $reference = [Drawing.Image]::FromFile((Join-Path $PSScriptRoot "reference-$screen.png"))
    $actual = [Drawing.Image]::FromFile((Join-Path $CaptureDirectory "$screen.png"))
    $canvas = [Drawing.Bitmap]::new(2976, 1062)
    $graphics = [Drawing.Graphics]::FromImage($canvas)
    $font = [Drawing.Font]::new('Segoe UI', 20)
    try {
        $graphics.Clear([Drawing.Color]::White)
        $graphics.DrawString('Selected mockup', $font, [Drawing.Brushes]::Black, 20, 7)
        $graphics.DrawString('Native SmartStock', $font, [Drawing.Brushes]::Black, 1508, 7)
        $graphics.DrawImage($reference, [Drawing.Rectangle]::new(0, 48, 1488, 1014),
            [Drawing.Rectangle]::new(0, 44, $reference.Width, $reference.Height - 44), [Drawing.GraphicsUnit]::Pixel)
        $graphics.DrawImage($actual, [Drawing.Rectangle]::new(1488, 48, 1488, 1014),
            [Drawing.Rectangle]::new(0, 0, $actual.Width, $actual.Height), [Drawing.GraphicsUnit]::Pixel)
        $canvas.Save((Join-Path $CaptureDirectory "compare-$screen.png"), [Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose(); $font.Dispose(); $canvas.Dispose(); $reference.Dispose(); $actual.Dispose()
    }
}
