param([ValidatePattern('^[0-9]+[.][0-9]+[.][0-9]+$')][string]$Version = '2.10.43', [switch]$PaperTested, [switch]$FoliaTested, [switch]$VaultTested, [switch]$MySqlTested)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$output = Join-Path $root 'outputs'
$jar = Join-Path $root "target/XiyuanMarry-$Version.jar"
$jarOut = Join-Path $output "XiyuanMarry-$Version.jar"
$zip = Join-Path $output "XiyuanMarry-source-$Version.zip"
$log = Join-Path $output "build-$Version.log"
if (!(Test-Path -LiteralPath $log) -or [IO.File]::ReadAllText($log) -notmatch 'BUILD SUCCESS') { throw '缺少本轮成功构建日志。' }
# 每次发布重新校验 JAR 与测试；拒绝源码更新后沿用旧构建。
& (Join-Path $PSScriptRoot 'verify-release.ps1') -Version $Version
$verification = Get-Content -LiteralPath (Join-Path $output "verification-$Version.json") -Raw -Encoding utf8 | ConvertFrom-Json
$jarTime = (Get-Item -LiteralPath $jar).LastWriteTimeUtc
$buildInputs = @((Get-Item -LiteralPath (Join-Path $root 'pom.xml'))) + @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -File -Recurse)
if (@($buildInputs | Where-Object {$_.LastWriteTimeUtc -gt $jarTime}).Count) { throw '构建后源码或资源已改变，请先重新执行 clean verify。' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$files = [Collections.Generic.List[IO.FileInfo]]::new()
foreach ($name in @('pom.xml','README.md','LICENSE',"RELEASE-$Version.md","AUDIT-$Version.md")) {
    $files.Add((Get-Item -LiteralPath (Join-Path $root $name)))
}
if (Test-Path -LiteralPath (Join-Path $root 'AGENTS.md')) { $files.Add((Get-Item -LiteralPath (Join-Path $root 'AGENTS.md'))) }
foreach ($directory in @('src','scripts')) {
    Get-ChildItem -LiteralPath (Join-Path $root $directory) -File -Recurse | ForEach-Object { $files.Add($_) }
}
# 使用新建临时 ZIP；不递归删除共享 TEMP 目录或旧版本产物。
$temporaryZip = Join-Path $output ('.package-' + [guid]::NewGuid().ToString('N') + '.zip')
$archive = [IO.Compression.ZipFile]::Open($temporaryZip,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in ($files | Sort-Object FullName)) {
        $relative = $file.FullName.Substring($root.Length + 1).Replace([char]92,[char]47)
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$relative,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
$archive = [IO.Compression.ZipFile]::OpenRead($temporaryZip)
try {
    if ($archive.Entries.Count -ne $files.Count) { throw '源码包条目数不一致。' }
    foreach ($entry in $archive.Entries) {
        $stream = $entry.Open(); $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','') }
        finally { $stream.Dispose(); $sha.Dispose() }
        $source = Join-Path $root $entry.FullName
        if ($hash -ne (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash) { throw "源码包内容不一致：$($entry.FullName)" }
    }
    $zipEntryCount = $archive.Entries.Count
} finally { $archive.Dispose() }
Move-Item -LiteralPath $temporaryZip -Destination $zip -Force
Copy-Item -LiteralPath $jar -Destination $jarOut -Force
if ((Get-FileHash -LiteralPath $jarOut).Hash -ne (Get-FileHash -LiteralPath $jar).Hash) { throw 'JAR 复制校验失败。' }
$manifest = [ordered]@{
    version=$Version; generatedAt=(Get-Date -Format o); java=21; paperApi='1.21.11-R0.1-SNAPSHOT'; foliaTarget='1.21.11'
    build=[ordered]@{success=$true; command='mvn clean verify -DskipTests=false'; log=$log}
    tests=$verification.tests
    jar=[ordered]@{path=$jarOut; sizeBytes=(Get-Item -LiteralPath $jarOut).Length; sha256=(Get-FileHash -LiteralPath $jarOut -Algorithm SHA256).Hash; entryCount=$verification.jarEntries; javaClassMajor=$verification.javaClassMajor; externalDependencyClassCount=@($verification.foreignClasses).Count}
    sourceZip=[ordered]@{path=$zip; sizeBytes=(Get-Item -LiteralPath $zip).Length; sha256=(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash; entryCount=$zipEntryCount; sourceMatchesArchive=$true}
    runtimeTests=[ordered]@{paper=[bool]$PaperTested; folia=[bool]$FoliaTested; vault=[bool]$VaultTested; mysql=[bool]$MySqlTested}
    verificationReport=(Join-Path $output "verification-$Version.json"); releaseNotes=(Join-Path $root "RELEASE-$Version.md"); auditReport=(Join-Path $root "AUDIT-$Version.md")
    limitations=@('真实 Paper/Folia/Vault/MySQL 联调及内存压力测试未执行。','配置文件读取及空闲池销毁仍有同步边界；停服实体属性清理可能被拒绝或取消。','没有跨服多实例分布式协调保证。','断电、离线或停服拒绝调度时，物品补偿及 REVIEW 仍需人工核对。','等级和自定义物品奖励、任务额外奖励、DH/ItemsAdder 尚未完整实现。')
}
$manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output "XiyuanMarry-$Version-manifest.json") -Encoding utf8
$manifest.jar | ConvertTo-Json
$manifest.sourceZip | ConvertTo-Json







