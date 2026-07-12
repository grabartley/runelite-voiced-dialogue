param(
  [switch]$DebugLogging
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$source = Get-ChildItem -LiteralPath (Join-Path $root "build\libs") -Filter "*-all.jar" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if ($null -eq $source) {
  throw "No shadow jar found. Run .\gradlew.bat shadowJar first."
}

$runDirectory = Join-Path $root "build\dev-runs"
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss-fff"
$runJar = Join-Path $runDirectory "voicedDialogue-$timestamp.jar"
Copy-Item -LiteralPath $source.FullName -Destination $runJar

$java = (Get-Command java.exe -ErrorAction Stop).Source
$arguments = @(
  "-ea",
  "--add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED",
  "-jar",
  $runJar,
  "--developer-mode"
)
if ($DebugLogging) {
  $arguments += "--debug"
}

try {
  & $java @arguments
} finally {
  Remove-Item -LiteralPath $runJar -Force -ErrorAction SilentlyContinue
}
