param(
    [string]$Version = "1.1.1"
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

& $makensis /INPUTCHARSET UTF8 "/DAPP_VERSION=$Version" "packaging\installer.nsi"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$out = Join-Path $root "build\SoundMesh-$Version-Setup.exe"
Write-Host "NSIS installer created: $out ($([math]::Round((Get-Item $out).Length / 1MB, 1)) MB)"
