# Permanent fix for corrupted C:\Users\busin\.gradle\caches\...\transforms\metadata.bin
# Moves Gradle cache to D: and clears the broken transforms folder.

$ErrorActionPreference = "Continue"
$gradleHome = "D:\gradle-home"

Write-Host "Stopping Gradle daemons..."
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match 'GradleDaemon|gradle' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

$transforms = Join-Path $env:USERPROFILE ".gradle\caches\8.9\transforms"
if (Test-Path $transforms) {
    Write-Host "Removing $transforms"
    Remove-Item -Recurse -Force $transforms -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Force -Path $gradleHome | Out-Null
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", $gradleHome, "User")
$env:GRADLE_USER_HOME = $gradleHome
Write-Host "GRADLE_USER_HOME set to $gradleHome (User)"
Write-Host ""
Write-Host "IMPORTANT: Fully quit Android Studio (File -> Exit), then reopen the project and Sync."
Write-Host "A running Studio process keeps the old GRADLE_USER_HOME until restart."
