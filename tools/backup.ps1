<#
.SYNOPSIS
    源码备份脚本：把工程源码打包成 zip，并把"即将进行的操作"追加到备份记录文本里。

.DESCRIPTION
    用途对齐用户要求：每次操作修改文件前都先备份一次源码，打包成 zip 压缩包，
    并且写一段文本记录即将进行的操作是什么。

    备份包只包含工程源码与文档，排除：
        backup  tmp  logs  build  .gradle  .gradle-home  run

.PARAMETER OpFile
    记录"即将进行的操作"的文本文件（UTF-8）。用 -OpText 也可以直接给一段话。

.PARAMETER OpText
    直接给出本次操作说明，优先级高于 -OpFile。

.PARAMETER Tag
    备份标签，会出现在 zip 文件名里。默认 snapshot。

.PARAMETER Root
    工程根目录，默认是本脚本上一级目录（自动推导，换机器不用改）。

.EXAMPLE
    .\tools\backup.ps1 -OpText "拆分 PortalLinkRenderer" -Tag render-split

.EXAMPLE
    "改动说明" | Out-File -Encoding utf8 tmp\next_op.txt
    .\tools\backup.ps1 -OpFile .\tmp\next_op.txt -Tag core-fix
#>
[CmdletBinding()]
param(
    [string]$OpFile,
    [string]$OpText,
    [string]$Tag = 'snapshot',
    [string]$Root,
    [string]$LogName = '下界门映射工程备份.txt',
    [int]$Keep = 30
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

# ---------- 1. 定位工程根目录 ----------
if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent $PSScriptRoot
}
$Root = (Resolve-Path -LiteralPath $Root).Path

# ---------- 2. 取本次操作说明 ----------
$op = ''
if (-not [string]::IsNullOrWhiteSpace($OpText)) {
    $op = $OpText
} elseif (-not [string]::IsNullOrWhiteSpace($OpFile)) {
    if (-not (Test-Path -LiteralPath $OpFile)) {
        throw "操作说明文件不存在：$OpFile"
    }
    $op = Get-Content -LiteralPath $OpFile -Raw -Encoding utf8
}
if ([string]::IsNullOrWhiteSpace($op)) {
    $op = '(未填写操作说明：调用时请用 -OpText 或 -OpFile 说明即将进行的改动)'
}
$op = $op.Trim()

# ---------- 3. 准备目录 ----------
$backupDir = Join-Path $Root 'backup'
foreach ($d in @($backupDir, (Join-Path $Root 'tmp'), (Join-Path $Root 'logs'))) {
    if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

# ---------- 4. 打包源码 ----------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$safeTag = ($Tag -replace '[^\w\-\u4e00-\u9fa5]', '-').Trim('-')
if ([string]::IsNullOrWhiteSpace($safeTag)) { $safeTag = 'snapshot' }
$zipPath = Join-Path $backupDir "$safeTag-$stamp.zip"

$excludeFiles = @('INDEX.md')          # 索引可自动重建，不进备份
$excludeDirs = @(
    'backup', 'tmp', 'logs', 'build', '.gradle', '.gradle-home', 'run',
    'toolchain', 'libs', 'skills_extracted', '.git', 'natives-windows'
)

$files = New-Object System.Collections.Generic.List[string]
$top = Get-ChildItem -LiteralPath $Root -Force
foreach ($it in $top) {
    if ($it.PSIsContainer) {
        if ($excludeDirs -contains $it.Name) { continue }
        $children = Get-ChildItem -LiteralPath $it.FullName -Recurse -Force -File -ErrorAction SilentlyContinue
        foreach ($f in $children) {
            $rel = $f.FullName.Substring($Root.Length + 1)
            $skip = $false
            foreach ($seg in ($rel -split '\\')) {
                if ($excludeDirs -contains $seg) { $skip = $true; break }
            }
            if ($skip) { continue }
            if ($f.FullName -match '\\(build|\.gradle|\.gradle-home|run)\\' ) { continue }
            if ($f.Name -eq 'gradle-wrapper.jar') {
                # wrapper jar 是二进制引导文件，属于工程一部分，仍然保留
            }
            $files.Add($f.FullName) | Out-Null
        }
    } else {
        if ($excludeFiles -contains $it.Name) { continue }
        $files.Add($it.FullName) | Out-Null
    }
}

if ($files.Count -eq 0) {
    throw "没有找到可备份的文件：$Root"
}

$fs = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    foreach ($path in $files) {
        $rel = $path.Substring($Root.Length + 1)
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $fs, $path, $rel, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $fs.Dispose()
}

$zipKB = [math]::Round((Get-Item -LiteralPath $zipPath).Length / 1KB, 1)

# ---------- 5. 追加记录到文本 ----------
$logPath = Join-Path $Root $LogName
if (-not (Test-Path -LiteralPath $logPath)) {
    @(
        '================================================================',
        '下界门映射工程备份 · 操作与备份记录',
        "工程目录：$Root",
        '记录规则：每次改动源码前，先由 tools\backup.ps1 打包 zip，再在此文件追加一条记录。',
        '================================================================'
    ) | Set-Content -LiteralPath $logPath -Encoding utf8
}

$existing = Get-Content -LiteralPath $logPath -Encoding utf8
$maxIdx = 0
foreach ($line in $existing) {
    if ($line -match '^\[(\d{3})\]') {
        $n = [int]$Matches[1]
        if ($n -gt $maxIdx) { $maxIdx = $n }
    }
}
$idx = '{0:D3}' -f ($maxIdx + 1)

$block = @(
    '',
    "[$idx] $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "  备份包：$($zipPath.Substring($Root.Length + 1))（$zipKB KB，$($files.Count) 个文件）",
    '  即将进行的操作：'
) + (($op -split "`r?`n") | ForEach-Object { "    $_" })

$block | Add-Content -LiteralPath $logPath -Encoding utf8

# ---------- 6. 轮转旧备份 ----------
$old = Get-ChildItem -LiteralPath $backupDir -Filter '*.zip' -File | Sort-Object LastWriteTime -Descending
if ($old.Count -gt $Keep) {
    $old | Select-Object -Skip $Keep | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName -Force
    }
    Write-Host "已清理旧备份 $($old.Count - $Keep) 个（保留最近 $Keep 个）"
}

# ---------- 7. 摘要输出 ----------
Write-Host ''
Write-Host '================ 备份完成 ================'
Write-Host "工程根目录 : $Root"
Write-Host "备份包     : $zipPath"
Write-Host "大小/文件数: $zipKB KB / $($files.Count)"
Write-Host "记录文件   : $logPath  ->  [$idx]"
Write-Host '=========================================='
