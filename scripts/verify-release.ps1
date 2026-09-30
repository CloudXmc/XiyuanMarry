param([string]$Version = '2.10.43')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root
$output = Join-Path $root 'outputs'
New-Item -ItemType Directory -Force -Path $output | Out-Null
function Scan-Java([string]$Pattern) {
    $matches = @(& rg -n --glob '*.java' $Pattern 'src/main/java')
    if ($LASTEXITCODE -gt 1) { throw "源码扫描失败：$Pattern" }
    return ,$matches
}
$traditional = Scan-Java 'Bukkit[.]getScheduler|scheduleSyncDelayedTask|scheduleAsyncRepeatingTask|[.]runTask(Later|Timer)?[(]'
$unavailable = Scan-Java 'PlayerRespawnEvent|PlayerTeleportEvent|PlayerChangedWorldEvent|WorldLoadEvent|WorldUnloadEvent'
$join = Scan-Java '[.]join[(][ ]*[)]|Future[.]get[(]'
$allTeleport = Scan-Java '[.]teleport[(]'
# 此入口调用自己的业务服务，真正传送仍由 UnifiedScheduler.teleportAsync 执行。
$realTeleport = @($allTeleport | Where-Object { $_ -notmatch 'TeleportSubcommand[.]java:[0-9]+:.*service[.]teleport[(]actor[)]' })
$getCandidates = Scan-Java '[.]get[(][ ]*[)]'
$reflection = Scan-Java 'Class[.]forName|getDeclaredMethod|java[.]lang[.]reflect'
$scheduler = Scan-Java 'getAsyncScheduler|getGlobalRegionScheduler|getRegionScheduler|getScheduler[(]'
$collections = Scan-Java 'new (HashMap|HashSet|ArrayList|ConcurrentHashMap|CopyOnWriteArrayList)|ConcurrentHashMap[.]newKeySet'
$nonScheduler = @($scheduler | Where-Object { $_ -notmatch 'UnifiedScheduler[.]java:' })
if ($traditional.Count -or $unavailable.Count -or $join.Count -or $realTeleport.Count -or $nonScheduler.Count) {
    throw '静态检查存在需要审查的调度、事件或阻塞调用。'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = Join-Path $root "target/XiyuanMarry-$Version.jar"
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
    $foreign = @($entries | Where-Object { $_.EndsWith('.class') -and (-not $_.StartsWith('cn/mcxyd/xiyuanmarry/')) })
    if ($foreign.Count) { throw 'JAR 含外部代码或未识别类。' }
    foreach ($required in @('plugin.yml','rewards.yml','messages.yml','META-INF/LICENSE')) {
        if ($entries -notcontains $required) { throw "JAR 缺少 $required" }
    }
    $reader = [IO.StreamReader]::new($archive.GetEntry('plugin.yml').Open())
    try { $descriptor = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($descriptor.Replace([string][char]13,'') -notmatch ('(?m)^version: ' + [regex]::Escape($Version) + '$')) { throw 'JAR 插件版本不一致。' }
    $stream = $archive.GetEntry('cn/mcxyd/xiyuanmarry/XiyuanMarryPlugin.class').Open()
    try { $bytes = [byte[]]::new(8); [void]$stream.Read($bytes,0,8); $major = $bytes[6] * 256 + $bytes[7] } finally { $stream.Dispose() }
    if ($major -ne 65) { throw '成品不是 Java 21 字节码。' }
} finally { $archive.Dispose() }
[xml]$pom = Get-Content -LiteralPath 'pom.xml' -Raw
if ($pom.project.version -ne $Version) { throw 'POM 版本不一致。' }
$invalidDependencies = @($pom.project.dependencies.dependency | Where-Object { ($_.scope -notin @('provided','test')) -or $_.systemPath })
if ($invalidDependencies.Count) { throw '存在错误作用域或 systemPath 依赖。' }
$total=0; $failures=0; $errors=0; $skipped=0
$suites = @(Get-ChildItem -LiteralPath 'target/surefire-reports' -Filter 'TEST-*.xml')
if (!$suites.Count) { throw '没有本轮测试报告。' }
foreach ($file in $suites) {
    [xml]$report = Get-Content -LiteralPath $file.FullName -Raw
    $total += [int]$report.testsuite.tests
    $failures += [int]$report.testsuite.failures
    $errors += [int]$report.testsuite.errors
    $skipped += [int]$report.testsuite.skipped
}
if ($failures -or $errors -or $skipped) { throw '测试失败、异常或跳过，需要处理。' }
$result = [ordered]@{
    version=$Version; checkedAt=(Get-Date -Format o)
    tests=[ordered]@{total=$total; failures=$failures; errors=$errors; skipped=$skipped; suites=$suites.Count}
    traditionalSchedulerHits=$traditional; unavailableEventHits=$unavailable; blockingHits=$join
    syncTeleportHits=$realTeleport; reviewedServiceTeleportCalls=$allTeleport
    getCandidates=$getCandidates; reflection=$reflection; scheduler=$scheduler; collectionCandidates=$collections
    foreignClasses=$foreign; jarEntries=$entries.Count; javaClassMajor=$major
    realPaperTested=$false; realFoliaTested=$false; realVaultTested=$false; realMySqlTested=$false
}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output "verification-$Version.json") -Encoding utf8
[ordered]@{version=$Version; tests=$result.tests; jarEntries=$entries.Count; javaClassMajor=$major; foreignClasses=$foreign.Count; report="outputs/verification-$Version.json"} | ConvertTo-Json -Depth 6












