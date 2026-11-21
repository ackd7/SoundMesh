Add-Type -AssemblyName System.Drawing
$out = Join-Path $PSScriptRoot "..\packaging"
New-Item -ItemType Directory -Force $out | Out-Null

function Render([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.Clear([System.Drawing.Color]::Transparent)
    $s = $size / 256.0
    # rounded square background with gradient
    $r = 56 * $s
    $rect = New-Object System.Drawing.RectangleF (8*$s), (8*$s), (240*$s), (240*$s)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
    $path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
    $path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.Color]::FromArgb(255, 54, 132, 255)), ([System.Drawing.Color]::FromArgb(255, 124, 77, 255)), 45.0
    $g.FillPath($brush, $path)
    # speaker body
    $white = [System.Drawing.Brushes]::White
    $pts = @(
        (New-Object System.Drawing.PointF (52*$s), (104*$s)),
        (New-Object System.Drawing.PointF (84*$s), (104*$s)),
        (New-Object System.Drawing.PointF (124*$s), (68*$s)),
        (New-Object System.Drawing.PointF (124*$s), (188*$s)),
        (New-Object System.Drawing.PointF (84*$s), (152*$s)),
        (New-Object System.Drawing.PointF (52*$s), (152*$s))
    )
    $g.FillPolygon($white, $pts)
    # sound waves
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White), (14*$s)
    $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
    foreach ($w in @(28, 58, 88)) {
        $g.DrawArc($pen, (128*$s - $w*$s), (128*$s - $w*$s), (2*$w*$s), (2*$w*$s), -40, 80)
    }
    $g.Dispose()
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    if ($size -eq 256) { $bmp.Save((Join-Path $out "icon.png"), [System.Drawing.Imaging.ImageFormat]::Png) }
    $bmp.Dispose()
    return ,$ms.ToArray()
}

$sizes = @(256, 64, 48, 32, 16)
$images = @()
foreach ($sz in $sizes) { $images += ,(Render $sz) }

$fs = [System.IO.File]::Create((Join-Path $out "icon.ico"))
$bw = New-Object System.IO.BinaryWriter $fs
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $sz = $sizes[$i]; $data = $images[$i]
    $dim = if ($sz -ge 256) { 0 } else { $sz }
    $bw.Write([byte]$dim); $bw.Write([byte]$dim); $bw.Write([byte]0); $bw.Write([byte]0)
    $bw.Write([UInt16]1); $bw.Write([UInt16]32)
    $bw.Write([UInt32]$data.Length); $bw.Write([UInt32]$offset)
    $offset += $data.Length
}
foreach ($data in $images) { $bw.Write($data) }
$bw.Close()
Write-Output "icon written to $out"
