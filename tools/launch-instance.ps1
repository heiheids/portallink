# Launch a Minecraft instance straight from its JSON, without a launcher GUI.
#
# Why: the mods live in the instances under E:\@26.3\.minecraft\versions\<instance>\mods. A dev
# client (MDG runClient) proves the code compiles and starts, but it does NOT prove the deployed jar
# works in the real instance with the real mod set. This script builds the same command line the
# launcher would, so the deliverable itself can be verified.
#
# Usage:
#   .\tools\launch-instance.ps1 -Instance 26.3-NeoForge
#   .\tools\launch-instance.ps1 -Instance 26.3-NeoForge -QuickPlayWorld '新的世界'
#   .\tools\launch-instance.ps1 -Instance 1.21.1-NeoForge -DryRun
#
# -QuickPlayWorld jumps straight into a world (vanilla --quickPlaySingleplayer); useful for
# automated verification, since the client then writes its log without anyone clicking a menu.

param(
    [Parameter(Mandatory = $true)][string]$Instance,
    [string]$MinecraftRoot = 'E:\@26.3\.minecraft',
    [string]$QuickPlayWorld = '',
    # Extra JVM arguments, e.g. -ExtraJvmArguments '-Dportallink.capture=3' for the mod's own
    # framebuffer capture (the only way to see the frame when the desktop window does not composite).
    [string[]]$ExtraJvmArguments = @(),
    # FML draws its own progress window over the game until loading finishes. It is a separate OS
    # window that can stay white and cover the real one, which makes screenshots useless.
    [switch]$NoEarlyWindow,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$versionDir = Join-Path $MinecraftRoot "versions\$Instance"
$jsonPath = Join-Path $versionDir "$Instance.json"
if (-not (Test-Path $jsonPath)) { throw "no version json: $jsonPath" }

$version = Get-Content $jsonPath -Raw | ConvertFrom-Json

# ---- classpath -------------------------------------------------------------------------------
$missing = New-Object System.Collections.Generic.List[string]
$classpath = New-Object System.Collections.Generic.List[string]
$classpath.Add((Join-Path $versionDir "$Instance.jar"))

foreach ($library in $version.libraries) {
    $path = $null
    if ($library.downloads -and $library.downloads.artifact) { $path = $library.downloads.artifact.path }
    if (-not $path) { continue }
    $full = Join-Path (Join-Path $MinecraftRoot 'libraries') ($path -replace '/', '\')
    if (Test-Path $full) { $classpath.Add($full) } else { $missing.Add($path) }
}
if ($missing.Count -gt 0) {
    Write-Warning "$($missing.Count) libraries missing (run the instance once with the launcher first):"
    $missing | Select-Object -First 10 | ForEach-Object { Write-Warning "  $_" }
}

# ---- JVM arguments ---------------------------------------------------------------------------
$nativesDir = Join-Path $versionDir 'natives-windows-x86_64'
$librariesDir = Join-Path $MinecraftRoot 'libraries'
$replacements = @{
    '${natives_directory}'     = $nativesDir
    '${launcher_name}'         = 'portallink-probe'
    '${launcher_version}'      = '1.0'
    '${classpath}'             = ($classpath -join ';')
    '${classpath_separator}'   = ';'
    '${library_directory}'     = $librariesDir
    '${primary_jar_name}'      = "$Instance.jar"
}

function Expand-Arg([string]$text) {
    $out = $text
    foreach ($key in $replacements.Keys) { $out = $out.Replace($key, $replacements[$key]) }
    return $out
}

$jvmArgs = New-Object System.Collections.Generic.List[string]
foreach ($entry in $version.arguments.jvm) {
    if ($entry -is [string]) { $jvmArgs.Add((Expand-Arg $entry)); continue }
    # Conditional entry: only keep rules that allow this OS.
    $allow = $true
    foreach ($rule in $entry.rules) {
        if ($rule.os.name -eq 'windows' -and $rule.action -eq 'allow') { $allow = $true }
        elseif ($rule.os.name -and $rule.os.name -ne 'windows' -and $rule.action -eq 'allow') { $allow = $false }
        if ($rule.action -eq 'disallow' -and $rule.os.name -eq 'windows') { $allow = $false }
    }
    if ($allow) { foreach ($v in $entry.value) { $jvmArgs.Add((Expand-Arg $v)) } }
}

# ---- game arguments --------------------------------------------------------------------------
$gameArgs = New-Object System.Collections.Generic.List[string]
$playerName = 'PortLinkProbe'
$uuid = '00000000000000000000000000000001'
$gameReplacements = @{
    '${auth_player_name}'  = $playerName
    '${version_name}'      = $Instance
    '${game_directory}'    = $versionDir
    '${assets_root}'       = (Join-Path $MinecraftRoot 'assets')
    '${assets_index_name}' = $version.assetIndex.id
    '${auth_uuid}'         = $uuid
    '${auth_access_token}' = '0'
    '${clientid}'          = '0'
    '${auth_xuid}'         = '0'
    '${version_type}'      = 'release'
    '${user_type}'         = 'legacy'
}

function Expand-GameArg([string]$text) {
    $out = $text
    foreach ($key in $gameReplacements.Keys) { $out = $out.Replace($key, $gameReplacements[$key]) }
    return $out
}

foreach ($entry in $version.arguments.game) {
    if ($entry -is [string]) { $gameArgs.Add((Expand-GameArg $entry)); continue }
    $allow = $true
    foreach ($rule in $entry.rules) {
        if ($rule.os.name -and $rule.os.name -ne 'windows' -and $rule.action -eq 'allow') { $allow = $false }
        if ($rule.action -eq 'disallow' -and $rule.os.name -eq 'windows') { $allow = $false }
    }
    if ($allow) { foreach ($v in $entry.value) { $gameArgs.Add((Expand-GameArg $v)) } }
}

# The launcher-written profile contains entries this script cannot fill in (window size, the
# launcher's own quick-play plumbing) and a hard coded --demo. Drop them, in pairs, so no flag is
# left without its value: "$game --width ${resolution_width}" must lose both halves.
$cleanArgs = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $gameArgs.Count; $i++) {
    $arg = $gameArgs[$i]
    if ($arg -eq '--demo') { continue }
    if ($arg -like '*${*') { continue }
    $next = if ($i + 1 -lt $gameArgs.Count) { $gameArgs[$i + 1] } else { '' }
    if ($arg.StartsWith('--') -and $next -like '*${*') { $i++; continue }
    $cleanArgs.Add($arg)
}

if ($QuickPlayWorld -ne '') {
    $cleanArgs.Add('--quickPlaySingleplayer')
    $cleanArgs.Add($QuickPlayWorld)
}

if ($NoEarlyWindow) {
    $jvmArgs.Add('-Dfml.earlyprogresswindow=false')
}

foreach ($extra in $ExtraJvmArguments) {
    if ($extra) { $jvmArgs.Add($extra) }
}

# ---- run -------------------------------------------------------------------------------------
$java = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot\bin\javaw.exe'
if (-not (Test-Path $java)) { throw "java not found: $java" }

$argFile = Join-Path $env:TEMP "portallink-launch-$Instance.txt"
# The JVM arg file may contain ONLY JVM options: anything after it on the command line is parsed as
# JVM arguments too, so game arguments must be passed on the command line (verified: putting
# "--username" in this file yields "Unrecognized option: --username").
$jvmArgs | Set-Content -Path $argFile -Encoding UTF8

Write-Host "instance : $Instance"
Write-Host "mainClass: $($version.mainClass)"
Write-Host "classpath: $($classpath.Count) entries"
Write-Host "args file: $argFile"

if ($DryRun) {
    Write-Host '--- jvm args ---'
    $jvmArgs | ForEach-Object { Write-Host "  $_" }
    Write-Host '--- game args ---'
    $cleanArgs | ForEach-Object { Write-Host "  $_" }
    return
}

# Quote every game argument: the world name may contain spaces and non-ASCII characters, and
# Start-Process passes the whole list through CreateProcess, which keeps Unicode intact.
$quoted = $cleanArgs | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }
$arguments = @("@$argFile", $version.mainClass) + $quoted

$proc = Start-Process -FilePath $java -ArgumentList $arguments `
    -WorkingDirectory $versionDir -PassThru
Write-Host "started pid=$($proc.Id); log: $versionDir\logs\latest.log"
