$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
python (Join-Path $root "Scripts\build_xshoot_dictionary.py")
$publish = Join-Path $root "Desktop.XShoot\publish"
Get-Process AETXShoot -ErrorAction SilentlyContinue | Stop-Process -Force
dotnet publish (Join-Path $root "Desktop.XShoot\Desktop.XShoot.csproj") -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o $publish
if ($LASTEXITCODE -ne 0) { throw "XShoot publish failed: $LASTEXITCODE" }
$exe = Join-Path $publish "AETXShoot.exe"
Start-Process $exe
Get-Item $exe | Select-Object FullName, Length, LastWriteTime
