<#
.SYNOPSIS
    Portal Link 构建入口：一条命令搞定 JDK、Gradle 缓存位置与三档验证。

.DESCRIPTION
    为什么需要这个脚本：
      * 本机默认 java 是 17/21，26.3 需要 Java 25，直接跑会报 "invalid release"；
      * 本机 services.gradle.org 不可达，Gradle 分发已解压到工程内 toolchain\；
      * 依赖缓存放在工程内 .gradle-home\，避免污染用户目录（符合"临时文件只在本工程内"）。

    三档验证（成本差 15 倍，永远先跑便宜的）：
      compile  ~秒级  只编译主源码，抓签名/语法错误
      gametest ~1 分钟 真客户端 + 真世界 + 截图
      build    完整构建并产出 jar

.PARAMETER Task
    compile | gametest | build | clean，默认 build。

.PARAMETER GradleArgs
    透传给 Gradle 的额外参数，例如 -GradleArgs '--info'。

.EXAMPLE
    .\tools\build.ps1 compile
    .\tools\build.ps1 build
#>
[CmdletBinding()]
param(
    [ValidateSet('compile', 'gametest', 'build', 'clean')]
    [string]$Task = 'build',
    [string[]]$GradleArgs = @()
)

$ErrorActionPreference = 'Stop'

# ---------- 1. 定位工程与源码目录 ----------
$Root = Split-Path -Parent $PSScriptRoot
$ProjectDir = Join-Path $Root 'source\portallink'
if (-not (Test-Path -LiteralPath $ProjectDir)) {
    throw "找不到源码工程：$ProjectDir"
}

# ---------- 2. JDK 25 ----------
# 优先级：环境变量 PORTALLINK_JAVA_HOME -> 本机已知 Microsoft JDK 25 -> PATH 里的 java
$candidates = @()
if ($env:PORTALLINK_JAVA_HOME) { $candidates += $env:PORTALLINK_JAVA_HOME }
$candidates += @(
    'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot',
    'C:\Program Files\Microsoft\jdk-25.0.5.8-hotspot',
    'C:\Program Files\Eclipse Adoptium\jdk-25'
)
$javaHome = $null
foreach ($c in $candidates) {
    if ($c -and (Test-Path -LiteralPath (Join-Path $c 'bin\java.exe'))) { $javaHome = $c; break }
}
if (-not $javaHome) {
    $javaExe = (Get-Command java -ErrorAction SilentlyContinue)
    if ($javaExe) { $javaHome = Split-Path -Parent (Split-Path -Parent $javaExe.Source) }
}
if (-not $javaHome) {
    throw '找不到 JDK 25。请安装 JDK 25 或用 $env:PORTALLINK_JAVA_HOME 指定。'
}
$env:JAVA_HOME = $javaHome

$major = (& (Join-Path $javaHome 'bin\java.exe') -version 2>&1 | Select-Object -First 1) -replace '.*"(\d+).*', '$1'
if ([int]$major -lt 25) {
    throw "JAVA_HOME 指向 Java $major，26.3 需要 Java 25：$javaHome"
}

# ---------- 3. Gradle ----------
# 优先用工程内 toolchain（离线可用），其次工程 wrapper，最后 PATH 里的 gradle
$gradleBat = $null
$localGradle = Join-Path $Root 'toolchain\gradle-9.7.1\bin\gradle.bat'
if (Test-Path -LiteralPath $localGradle) {
    $gradleBat = $localGradle
} elseif (Test-Path -LiteralPath (Join-Path $ProjectDir 'gradlew.bat')) {
    $gradleBat = Join-Path $ProjectDir 'gradlew.bat'
} else {
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { $gradleBat = $cmd.Source }
}
if (-not $gradleBat) {
    throw '找不到 Gradle：既没有 toolchain\gradle-9.7.1，也没有 gradlew.bat。'
}

# ---------- 4. 缓存与编码 ----------
$env:GRADLE_USER_HOME = Join-Path $Root '.gradle-home'
$env:GRADLE_OPTS = '-Dfile.encoding=UTF-8'

# ---------- 5. 任务映射 ----------
$tasks = switch ($Task) {
    'compile'  { @('compileJava') }
    'gametest' { @('compileJava', 'compileGametestJava', 'runClientGameTest') }
    'build'    { @('build') }
    'clean'    { @('clean') }
}

$logDir = Join-Path $Root 'logs'
if (-not (Test-Path -LiteralPath $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }
$buildLog = Join-Path $logDir "build-$Task.log"

Write-Host "JAVA_HOME        : $env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME : $env:GRADLE_USER_HOME"
Write-Host "Gradle           : $gradleBat"
Write-Host "任务             : $($tasks -join ' ')"
Write-Host ''

$sw = [Diagnostics.Stopwatch]::StartNew()
$argv = @('-p', $ProjectDir) + $tasks + @('--console=plain') + $GradleArgs
& $gradleBat @argv 2>&1 | Tee-Object -FilePath $buildLog
$code = $LASTEXITCODE
$sw.Stop()

Write-Host ''
Write-Host "日志：$buildLog"
if ($code -eq 0) {
    Write-Host "结果：成功（$([math]::Round($sw.Elapsed.TotalSeconds,1)) 秒）" -ForegroundColor Green
    if ($Task -eq 'build') {
        $libsDir = Join-Path $ProjectDir 'build\libs'
        $jar = Get-ChildItem -LiteralPath $libsDir -File -Filter 'portallink-*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch '-(sources|dev|javadoc)\.jar$' } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($jar) {
            $kb = [math]::Round($jar.Length / 1KB, 1)
            Write-Host "产物：$($jar.FullName)（$kb KB）"
        }
    }
} else {
    Write-Host "结果：失败（退出码 $code，$([math]::Round($sw.Elapsed.TotalSeconds,1)) 秒）" -ForegroundColor Red
}
exit $code
