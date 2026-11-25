# Windows PowerShell + adb. Old app and source weights remain intact.
$ErrorActionPreference = "Stop"
$new = "com.vobich.bluestar.offlinelab.gpu.recovery"
$old = "com.vobich.bluestar.offlinelab.gpu"
$modeFile = Join-Path $PSScriptRoot "INSTALL_MODE.txt"
if (Test-Path $modeFile) {
    $pkg = (Get-Content $modeFile | Where-Object { $_ -match "^package=" } | Select-Object -First 1) -replace "^package=", ""
    if ($pkg -notin @("com.vobich.bluestar.offlinelab.gpu", "com.vobich.bluestar.offlinelab.gpu.recovery")) { throw "Unknown target package" }
    $new = $pkg
}
if ($old -eq $new) { Write-Host "Upgrade mode; installed model is retained. No migration required."; exit 0 }
function Shell([string]$cmd) {
    $out = & adb shell $cmd
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $cmd" }
    return ($out -join "`n").Trim()
}
$folder = "files/models/minicpm-v-4_7-1b"
$names = @("MiniCPM-V-4_7-1B-Q4_K_M.gguf","mmproj-model-f16.gguf")
$old = $null
foreach ($candidate in @("com.vobich.bluestar.offlinelab.gpu", "com.vobich.bluestar.offlinelab.speed", "com.vobich.bluestar.offlinelab")) {
    try {
        $null = Shell "run-as $candidate pwd"
        foreach ($name in $names) { $null = Shell "run-as $candidate test -s $folder/$name" }
        $old = $candidate; break
    } catch { continue }
}
if (-not $old) { throw "No complete source model found; keep all old apps." }
$null = Shell "run-as $new pwd"
$null = Shell "am force-stop $new"
$null = Shell "am force-stop $old"
$null = Shell "run-as $new mkdir -p $folder"
foreach ($name in $names) {
    $path = "$folder/$name"
    $source = (Shell "run-as $old sha256sum $path").Split(" ")[0]
    if ($source -notmatch "^[a-f0-9]{64}$") { throw "Cannot verify source $name" }
    Write-Host "Copying $name locally on phone..."
    $null = Shell "run-as $old cat $path | run-as $new sh -c 'cat > $path.part'"
    $dest = (Shell "run-as $new sha256sum $path.part").Split(" ")[0]
    if ($dest -ne $source) { throw "Checksum mismatch; old model unchanged" }
    $null = Shell "run-as $new mv $path.part $path"
}
$null = Shell "run-as $new mkdir -p shared_prefs"
try {
    $src = (Shell "run-as $old sha256sum shared_prefs/lab_log.xml").Split(" ")[0]
    if ($src -match "^[a-f0-9]{64}$") {
        $present = Shell "run-as $new sh -c 'if [ -e shared_prefs/lab_log.xml ]; then echo yes; else echo no; fi'"
        if ($present -eq "no") {
            $null = Shell "run-as $old cat shared_prefs/lab_log.xml | run-as $new sh -c 'cat > shared_prefs/lab_log.xml.part'"
            $dst = (Shell "run-as $new sha256sum shared_prefs/lab_log.xml.part").Split(" ")[0]
            if ($src -eq $dst) { $null = Shell "run-as $new mv shared_prefs/lab_log.xml.part shared_prefs/lab_log.xml" }
        }
    }
} catch { Write-Warning "Optional numeric log transfer skipped: $_" }
Write-Host "Complete. Keep old app installed. Open the new lab and run a 1B test without downloading."
