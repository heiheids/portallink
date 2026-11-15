<#
.SYNOPSIS
    把构建产物部署到 Minecraft 实例的 mods 目录，并替换掉旧版本。

.DESCRIPTION
    用户约定：**每次改完就把新 jar 放进实例 mods\，替换旧的。**

    这个脚本把该动作固定下来，避免手滑：
      1. 校验构建产物存在且比源码新（防止部署一个过期 jar）；
      2. 默认先跑一次 build，除非显式 -SkipBuild；
      3. 删掉 mods\ 里所有旧版 portallink-*.jar（含改名残留），只留一份，
         因为同时存在两个版本会让 Fabric 直接报 mod 冲突；
      4. 复制新 jar 并回读校验 SHA256 一致；
      5. 打印结果清单。

    实例路径可用 -GameDir 覆盖，默认是用户机器上的 26.3 实例。

.PARAMETER GameDir
    游戏实例目录，默认 E:\@26.3\.minecraft\versions\26.3-Fabric

.PARAMETER SkipBuild
    跳过构建，直接部署现有产物（仅在刚刚构建过时使用）。

.EXAMPLE
    .\tools\deploy.ps1

.EXAMPLE
    .\tools\deploy.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    [string]$GameDir = 'E:\@26.3\.minecraft\versions\26.3-Fabric',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$ProjectDir = Join-Path $Root 'source\portallink'
$libsDir = Join-Path $ProjectDir 'build\libs'
$modsDir = Join-Path $GameDir 'mods'

# ---------- 1. 检查实例 ----------
if (-not (Test-Path -LiteralPath $GameDir)) {
    throw "找不到游戏实例：$GameDir（可用 -GameDir 指定）"
}
if (-not (Test-Path -LiteralPath $modsDir)) {
    New-Item -ItemType Directory -Path $modsDir | Out-Null
    Write-Host "已创建 mods 目录：$modsDir"
}

# ---------- 2. 构建 ----------
if (-not $SkipBuild) {
    Write-Host '--- 构建 ---'
    & (Join-Path $Root 'tools\build.ps1') build
    if ($LASTEXITCODE -ne 0) {
        throw "构建失败（退出码 $LASTEXITCODE），未部署任何文件。"
    }
}

if (-not (Test-Path -LiteralPath $libsDir)) {
    throw "找不到产物目录：$libsDir（先运行 tools\build.ps1 build）"
}

# 取最新的正式 jar（排除 -sources.jar 与 -dev.jar）
$built = Get-ChildItem -LiteralPath $libsDir -File -Filter 'portallink-*.jar' |
    Where-Object { $_.Name -notmatch '-(sources|dev|javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $built) {
    throw "找不到构建产物：$libsDir\portallink-*.jar"
}
$builtJar = $built.FullName
$jarName = $built.Name

# ---------- 3. 产物不能比源码旧 ----------
$jarTime = (Get-Item -LiteralPath $builtJar).LastWriteTime
$newestSource = Get-ChildItem (Join-Path $ProjectDir 'src') -Recurse -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($newestSource -and $newestSource.LastWriteTime -gt $jarTime.AddSeconds(2)) {
    Write-Warning "产物比源码旧：jar=$($jarTime)，最新源码=$($newestSource.LastWriteTime) [$($newestSource.Name)]"
    Write-Warning '可能部署的是过期 jar。建议先 tools\build.ps1 build。'
}

# ---------- 4. 移除旧版本 ----------
# 同时清掉改过名的残留（如 portallink-1.0.0 (1).jar、其他版本号），只留一份。
$existing = Get-ChildItem -LiteralPath $modsDir -File -Filter 'portallink*.jar' -ErrorAction SilentlyContinue
foreach ($old in $existing) {
    try {
        Remove-Item -LiteralPath $old.FullName -Force
        Write-Host "已移除旧版本：$($old.Name)（$($old.Length) 字节）"
    } catch {
        # Windows locks a jar while the game is running. Never leave a half deployed state:
        # nothing has been copied yet, so the mods folder is still exactly as the game loaded it.
        Write-Host ''
        Write-Host '================ 未部署 ================' -ForegroundColor Yellow
        Write-Host "游戏正在运行，$($old.Name) 被占用，无法替换。" -ForegroundColor Yellow
        Write-Host 'mods 目录未被改动，当前正在运行的游戏不受影响。' -ForegroundColor Yellow
        Write-Host ''
        Write-Host '关闭 Minecraft 后执行下面一条命令即可完成部署：' -ForegroundColor Cyan
        Write-Host "  $Root\tools\deploy.ps1 -SkipBuild" -ForegroundColor Cyan
        Write-Host "==========================================" -ForegroundColor Yellow
        exit 2
    }
}

# ---------- 5. 复制并校验 ----------
$target = Join-Path $modsDir $jarName
Copy-Item -LiteralPath $builtJar -Destination $target -Force

$srcHash = (Get-FileHash -LiteralPath $builtJar -Algorithm SHA256).Hash
$dstHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
if ($srcHash -ne $dstHash) {
    throw "复制校验失败：$builtJar 与 $target 的 SHA256 不一致。"
}

# ---------- 6. 结果 ----------
$sizeKb = [math]::Round((Get-Item -LiteralPath $target).Length / 1KB, 1)
Write-Host ''
Write-Host '================ 部署完成 ================'
Write-Host "目标   : $target"
Write-Host "大小   : $sizeKb KB"
Write-Host "SHA256 : $($dstHash.Substring(0, 32))..."
Write-Host "构建于 : $jarTime"
$others = Get-ChildItem -LiteralPath $modsDir -File -Filter 'portallink*.jar'
Write-Host "mods 内 portallink jar 数量：$($others.Count)（必须是 1）"
if ($others.Count -ne 1) {
    Write-Warning '存在多份 portallink jar，会导致 mod 冲突！'
}
Write-Host '=========================================='
