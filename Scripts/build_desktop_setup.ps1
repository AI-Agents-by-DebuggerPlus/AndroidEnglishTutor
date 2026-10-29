$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$publish = Join-Path $root "Desktop.Wpf\publish"
$stage = Join-Path $publish "setup-staging"
$setupExe = Join-Path $publish "AETDesktopSetup.exe"

Get-Process AETDesktop, iexpress -ErrorAction SilentlyContinue | Stop-Process -Force

dotnet publish (Join-Path $root "Desktop.Wpf\Desktop.Wpf.csproj") -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o $publish
if ($LASTEXITCODE -ne 0) { throw "dotnet publish failed: $LASTEXITCODE" }

New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item (Join-Path $root "app\src\main\assets\default_settings.json") (Join-Path $stage "default_settings.json") -Force
Copy-Item (Join-Path $publish "AETDesktop.exe") (Join-Path $stage "AETDesktop.exe") -Force

$localSettings = Join-Path $env:APPDATA "AETDesktop\desktop-settings.json"
if (-not (Test-Path $localSettings)) { throw "Missing saved Desktop settings: $localSettings" }
Copy-Item $localSettings (Join-Path $stage "desktop-settings.json") -Force

dotnet publish (Join-Path $root "Desktop.Setup\Desktop.Setup.csproj") -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o $publish
if ($LASTEXITCODE -ne 0) { throw "setup publish failed: $LASTEXITCODE" }
if (-not (Test-Path $setupExe)) { throw "Installer was not created: $setupExe" }
Get-Item $setupExe | Select-Object FullName, Length, LastWriteTime
