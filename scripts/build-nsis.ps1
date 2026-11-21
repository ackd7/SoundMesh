param(
    [string]$Version = "1.2.0"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $root

if (-not (Test-Path "build\compose\binaries\main\app\SoundMesh\SoundMesh.exe")) {
    Write-Host "Building standalone app bundle first..."
    & .\gradlew.bat createDistributable --no-daemon --console=plain
}

$makensis = @(
    (Get-Command makensis -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source),
    "C:\Program Files (x86)\NSIS\makensis.exe",
    "C:\Program Files\NSIS\makensis.exe"
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1

if (-not $makensis) {
    throw "NSIS (makensis.exe) not found. Install it via: winget install NSIS.NSIS"
}

Write-Host "Building NSIS Setup installer..."
& $makensis /INPUTCHARSET UTF8 "/DAPP_VERSION=$Version" "packaging\installer.nsi"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Building Single-EXE Portable edition..."
& $makensis /INPUTCHARSET UTF8 "/DAPP_VERSION=$Version" "packaging\portable.nsi"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$setup = Join-Path $root "build\SoundMesh-$Version-Setup.exe"
$portable = Join-Path $root "build\SoundMesh-$Version-Portable.exe"
Write-Host "NSIS Setup created:    $setup ($([math]::Round((Get-Item $setup).Length / 1MB, 1)) MB)"
Write-Host "Portable EXE created:  $portable ($([math]::Round((Get-Item $portable).Length / 1MB, 1)) MB)"
