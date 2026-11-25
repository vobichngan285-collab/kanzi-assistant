# Windows PowerShell + adb. Old app and source weights remain intact.
$ErrorActionPreference = "Stop"
$old = "com.vobich.bluestar.offlinelab.speed"
$new = "com.vobich.bluestar.offlinelab.gpu"
function Shell([string]$cmd) {
    $out = & adb shell $cmd
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $cmd" }
    return ($out -join "`n").Trim()
}
try { $null = Shell "run-as $old pwd" } catch { $old = "com.vobich.bluestar.offlinelab"; $null = Shell "run-as $old pwd" }
$null = Shell "run-as $new pwd"
$null = Shell "am force-stop $new"
$null = Shell "am force-stop $old"
$folder = "files/models/minicpm-v-4_7-1b"
$names = @("MiniCPM-V-4_7-1B-Q4_K_M.gguf","mmproj-model-f16.gguf")
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
