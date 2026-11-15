<#
.SYNOPSIS
    生成 / 校验 docs\INDEX.md 文件索引。

.DESCRIPTION
    对应 docs\文件读取与索引规范.md §4 与 §9：
      * 扫描工程内源码、资源、文档、构建配置、工具脚本；
      * 每个文件输出 路径 / 行数 / KB / 用途 / 关键符号；
      * 用途来自 tools\file-index-notes.txt（人工维护，脚本只读）；
      * -Check 模式做门禁：超长文件、未标注用途、根目录散落临时文件。

    设计约束：
      * 只读文件头部与类声明，不做全文加载（大文件安全）；
      * 输出固定写 docs\INDEX.md，不打印大表到控制台。

.PARAMETER Check
    只校验不写文件，有违规时退出码 1。

.PARAMETER MaxLines
    单文件行数上限，默认 300（规范硬要求）。

.EXAMPLE
    .\tools\file-index.ps1
    .\tools\file-index.ps1 -Check
#>
[CmdletBinding()]
param(
    [switch]$Check,
    [int]$MaxLines = 300,
    [string]$Root
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Root)) { $Root = Split-Path -Parent $PSScriptRoot }
$Root = (Resolve-Path -LiteralPath $Root).Path

$notesFile = Join-Path $Root 'tools\file-index-notes.txt'
$indexFile = Join-Path $Root 'docs\INDEX.md'

$excludeDirs = @(
    'backup', 'tmp', 'logs', 'build', '.gradle', '.gradle-home', 'run',
    'toolchain', 'libs', 'skills_extracted', '.git', 'natives-windows',
    '临时文件', 'screenshots', 'clientGameTest',
    'processedMods', '.fabric', 'saves', 'screenshots'
)

# ---------- 1. 读取用途标注 ----------
$notes = @{}
if (Test-Path -LiteralPath $notesFile) {
    foreach ($line in (Get-Content -LiteralPath $notesFile -Encoding utf8)) {
        $t = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($t)) { continue }
        if ($t.StartsWith('#')) { continue }
        $i = $t.IndexOf('=')
        if ($i -lt 1) { continue }
        # 标注文件里统一用正斜杠书写，这里归一化成反斜杠，避免 Windows 下匹配不到
        $key = $t.Substring(0, $i).Trim().Replace('/', '\')
        $notes[$key] = $t.Substring($i + 1).Trim()
    }
}

# ---------- 2. 收集文件 ----------
function Get-ProjectFiles {
    param([string]$Base)
    $out = New-Object System.Collections.Generic.List[System.IO.FileInfo]
    foreach ($top in (Get-ChildItem -LiteralPath $Base -Force)) {
        if ($top.PSIsContainer) {
            if ($excludeDirs -contains $top.Name) { continue }
            foreach ($f in (Get-ChildItem -LiteralPath $top.FullName -Recurse -Force -File -ErrorAction SilentlyContinue)) {
                $rel = $f.FullName.Substring($Base.Length + 1)
                $skip = $false
                foreach ($seg in ($rel -split '\\')) {
                    if ($excludeDirs -contains $seg) { $skip = $true; break }
                }
                if (-not $skip) { $out.Add($f) }
            }
        } else {
            $out.Add($top)
        }
    }
    return $out
}

$all = Get-ProjectFiles -Base $Root

# ---------- 3. 分类 ----------
function Get-Category {
    param([string]$Rel, [string]$Ext)
    if ($info = $notes[$Rel]) { }
    switch -Regex ($Rel) {
        '^source\\portallink\\src\\gametest\\' { return '游戏测试（不进发布 jar）' }
        '^source\\portallink\\src\\main\\java\\' { return '源码（Java）' }
        '^source\\portallink\\src\\main\\resources\\' { return '资源' }
        '^docs\\' { return '文档' }
        '^tools\\' { return '工具脚本' }
        '^source\\portallink\\src\\gametest\\resources\\' { return '资源' }
        '\.(gradle|properties)$' { return '构建配置' }
        '^screenshots\\' { return '截图' }
        '\.(txt)$' { return '数据表' }
        '\.(zip|jar)$' { return '其它' }
        '^source\\portallink\\(gradlew|gradlew\.bat|gradle\\)' { return '其它' }
        default { return '其它' }
    }
}

# ---------- 4. 抽取关键符号（只读前 60 行里的类型声明） ----------
function Get-Symbols {
    param([string]$Path, [string]$Ext)
    if ($Ext -ne '.java') { return '' }
    $syms = New-Object System.Collections.Generic.List[string]
    try {
        $head = Get-Content -LiteralPath $Path -TotalCount 200 -Encoding utf8 -ErrorAction SilentlyContinue
    } catch { return '' }
    $typeName = ''
    $methods = New-Object System.Collections.Generic.List[string]
    foreach ($line in $head) {
        # 跳过注释行，否则 Javadoc 里的英文句子会被误判成类声明/方法
        if ($line -match '^\s*(\*|//|/\*|package\s|import\s)') { continue }
        if ($line -match '^\s*(?:public\s+|protected\s+|private\s+)?(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+)*(?:class|interface|enum|record)\s+([A-Z]\w+)') {
            if (-not $typeName) { $typeName = $Matches[1] }
            continue
        }
        # 注意：方法名是捕获组 1（可见性用非捕获组）
        if ($line -match '^\s*(?:public|protected)\s+(?:static\s+)?(?:final\s+)?(?:abstract\s+)?[\w<>\[\],\.\?]+\s+([a-z]\w*)\s*\(') {
            $methods.Add($Matches[1])
        }
        if ($methods.Count -ge 9) { break }
    }
    if ($typeName) { $syms.Add($typeName) }
    foreach ($m in $methods) { if ($syms.Count -lt 10) { $syms.Add($m) } }
    if ($syms.Count -eq 0) { return '' }
    $txt = ($syms -join ', ')
    if ($txt.Length -gt 70) { $txt = $txt.Substring(0, 67) + '...' }
    return $txt
}

# ---------- 5. 统计 ----------
$rows = New-Object System.Collections.Generic.List[object]
$violations = New-Object System.Collections.Generic.List[string]
$totalLines = 0
$totalKB = 0.0
$binaryExt = @('.jar', '.zip', '.png', '.dat', '.mca')

foreach ($f in $all) {
    $rel = $f.FullName.Substring($Root.Length + 1)
    if ($rel -eq 'docs\INDEX.md') { continue }   # 索引自身不计数
    $kb = [math]::Round($f.Length / 1KB, 1)
    $ext = $f.Extension.ToLowerInvariant()
    $binary = $binaryExt -contains $ext
    $lines = 0
    if (-not $binary) {
        try { $lines = (Get-Content -LiteralPath $f.FullName -Encoding utf8 -ErrorAction SilentlyContinue | Measure-Object).Count } catch { $lines = 0 }
    }
    $totalLines += $lines
    $totalKB += $kb

    $usage = $notes[$rel]
    $isSource = ($rel -match '^source\\.*\.java$') -or ($rel -match '^source\\.*\.gradle$') -or ($rel -match '^tools\\.*\.ps1$')
    if (-not $usage) {
        $usage = '（未标注）'
        if ($isSource) { $violations.Add("未标注用途：$rel") }
    }
    if ($isSource -and -not $binary -and $lines -gt $MaxLines) {
        $violations.Add("超长文件（$lines 行 > $MaxLines）：$rel")
    }
    $rows.Add([pscustomobject]@{
        Rel     = $rel
        Cat     = (Get-Category -Rel $rel -Ext $ext)
        Lines   = $(if ($binary) { '-' } else { $lines })
        KB      = $kb
        Usage   = $usage
        Symbols = (Get-Symbols -Path $f.FullName -Ext $ext)
        Binary  = $binary
    })
}

# 根目录散落临时文件
foreach ($f in (Get-ChildItem -LiteralPath $Root -File -Force)) {
    if ($f.Name -match '^tmp_.*\.(txt|md|log)$') {
        $violations.Add("根目录散落临时文件：$($f.Name)")
    }
}

# ---------- 6. 输出 ----------
$catOrder = @('源码（Java）', '游戏测试（不进发布 jar）', '资源', '文档', '构建配置', '工具脚本', '数据表', '其它')
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('# INDEX — 文件索引')
[void]$sb.AppendLine()
[void]$sb.AppendLine('> 由 `tools\file-index.ps1` 生成，**勿手改**。用途列来自 `tools\file-index-notes.txt`。')
[void]$sb.AppendLine("> 工程根目录：``$Root``")
[void]$sb.AppendLine("> 生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')　文件数：$($rows.Count)　总行数：$totalLines　总大小：$([math]::Round($totalKB,1)) KB")
[void]$sb.AppendLine()
[void]$sb.AppendLine('| 行数 | 读取级别 | 允许手段 |')
[void]$sb.AppendLine('| --- | --- | --- |')
[void]$sb.AppendLine('| ≤ 80 | L2 窗口 | `read` 带 offset/limit |')
[void]$sb.AppendLine('| 81–300 | L3 整读 | 改签名前一次整读 |')
[void]$sb.AppendLine("| > $MaxLines | 违规 | 先拆分，不得整读 |")
[void]$sb.AppendLine()

foreach ($cat in $catOrder) {
    $items = $rows | Where-Object { $_.Cat -eq $cat } | Sort-Object Rel
    if (-not $items) { continue }
    [void]$sb.AppendLine("## $cat")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('| 路径 | 行数 | KB | 用途 | 关键符号 |')
    [void]$sb.AppendLine('| --- | --- | --- | --- | --- |')
    foreach ($r in $items) {
        [void]$sb.AppendLine("| ``$($r.Rel)`` | $($r.Lines) | $($r.KB) | $($r.Usage) | $($r.Symbols) |")
    }
    [void]$sb.AppendLine()
}

[void]$sb.AppendLine('## 违规项')
[void]$sb.AppendLine()
if ($violations.Count -eq 0) {
    [void]$sb.AppendLine('无。')
} else {
    foreach ($v in $violations) { [void]$sb.AppendLine("- $v") }
}
[void]$sb.AppendLine()
[void]$sb.AppendLine('---')
[void]$sb.AppendLine()
[void]$sb.AppendLine('## 已排除的目录')
[void]$sb.AppendLine()
[void]$sb.AppendLine(($excludeDirs | ForEach-Object { "``$_``" }) -join '、')
[void]$sb.AppendLine()
[void]$sb.AppendLine('这些目录只由脚本处理（备份、构建、抓取），任何情况下都不应手工 `read`。')

if (-not $Check) {
    $docsDir = Split-Path -Parent $indexFile
    if (-not (Test-Path -LiteralPath $docsDir)) { New-Item -ItemType Directory -Path $docsDir | Out-Null }
    $sb.ToString() | Set-Content -LiteralPath $indexFile -Encoding utf8
}

Write-Host "files=$($rows.Count) lines=$totalLines kb=$([math]::Round($totalKB,1)) violations=$($violations.Count)"
if ($violations.Count -gt 0) {
    foreach ($v in $violations) { Write-Host "  ! $v" }
    if ($Check) {
        Write-Host 'INDEX CHECK FAILED'
        exit 1
    }
}
if ($Check) { Write-Host 'INDEX CHECK OK' } else { Write-Host "written: $indexFile" }
exit 0
