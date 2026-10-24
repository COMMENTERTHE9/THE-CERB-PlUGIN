<#
  Cerberus dev tool - build, deploy, start, and check the local Folia test server.

  Usage (from the repo folder; the .bat files in the repo root call this for you):
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\cerb.ps1 <command>

  Commands:
    status   Report Java, server folder, jar, EULA, worlds, plugin, and whether the server runs.
    setup    First-time setup: create the server folder and download the Folia server jar.
    build    Build the plugin (mvnw clean package, runs unit tests).
    deploy   Build, back up plugin data, copy Cerberus.jar into the server (server must be stopped).
    start    Back up plugin data and start the server in this window (no build).
    run      build + deploy + start: the normal daily loop.
    backup   Copy worlds + plugin data into test-server\backups\ (server must be stopped).

  Settings (optional environment variables):
    CERB_SERVER_DIR    server folder           (default: <repo>\test-server)
    CERB_SERVER_JAR    server jar file name     (default: folia-1.21.11-14.jar, else any folia-*.jar)
    BUILD_JAVA_HOME    JDK used to build        (default: newest JDK >= 21 found)
    RUN_JAVA_HOME      Java used to run server  (default: newest Java >= 21 found)
    JAVA_XMS/JAVA_XMX  server heap              (default: 1G / 4G)

  Written for Windows PowerShell 5.1 and PowerShell 7. Keep this file ASCII-only.
#>
param(
    [Parameter(Position = 0)][string]$Command = 'status'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

# ---------------------------------------------------------------- constants
$FoliaVersion   = '1.21.11'
$FoliaBuild     = 14
$FoliaJarName   = "folia-$FoliaVersion-$FoliaBuild.jar"
$FoliaSha256    = 'f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39'
$FoliaUrl       = "https://fill-data.papermc.io/v1/objects/$FoliaSha256/$FoliaJarName"
$MinJava        = 21
$PluginDirName  = 'CerberusPlugin'    # data folder = plugin.yml name
$PluginJarName  = 'Cerberus.jar'
$KeepDataBackups = 10

$OnWindows = ($env:OS -eq 'Windows_NT')
$RepoRoot  = Split-Path -Parent $PSScriptRoot
$ServerDir = if ($env:CERB_SERVER_DIR) { $env:CERB_SERVER_DIR } else { Join-Path $RepoRoot 'test-server' }
$BuiltJar  = Join-Path (Join-Path $RepoRoot 'target') $PluginJarName

# ---------------------------------------------------------------- output helpers
function Say([string]$msg)  { Write-Host $msg }
function Ok([string]$msg)   { Write-Host "  [ok]   $msg" -ForegroundColor Green }
function Warn([string]$msg) { Write-Host "  [warn] $msg" -ForegroundColor Yellow }
function Bad([string]$msg)  { Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Step([string]$msg) { Write-Host ''; Write-Host "== $msg" -ForegroundColor Cyan }

function Stop-Cerb([string]$msg) {
    # Print a clear one-line reason and exit non-zero (finally blocks still run).
    Write-Host ''
    Bad $msg
    exit 1
}

# ---------------------------------------------------------------- java discovery
function Get-JavaMajor([string]$javaHome) {
    # Read the JDK 'release' file (no process launch). Returns 0 if unknown.
    $release = Join-Path $javaHome 'release'
    if (Test-Path -LiteralPath $release) {
        $line = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
        if ($line) {
            $v = $line.Matches[0].Groups[1].Value
            if ($v.StartsWith('1.')) { return [int]($v.Split('.')[1]) }
            return [int]($v.Split('.')[0].Split('-')[0].Split('+')[0])
        }
    }
    return 0
}

function Get-JavaExe([string]$javaHome) {
    $name = if ($OnWindows) { 'java.exe' } else { 'java' }
    return Join-Path (Join-Path $javaHome 'bin') $name
}

function Test-IsJdk([string]$javaHome) {
    $name = if ($OnWindows) { 'javac.exe' } else { 'javac' }
    return Test-Path -LiteralPath (Join-Path (Join-Path $javaHome 'bin') $name)
}

function Find-JavaHomes {
    $homes = New-Object System.Collections.Generic.List[string]
    foreach ($v in @($env:BUILD_JAVA_HOME, $env:RUN_JAVA_HOME, $env:JAVA_HOME)) {
        if ($v) { $homes.Add($v.TrimEnd('\', '/')) }
    }
    $roots = @()
    if ($OnWindows) {
        $roots = @(
            'C:\Program Files\Eclipse Adoptium',
            'C:\Program Files\Java',
            'C:\Program Files\Microsoft',
            'C:\Program Files\Zulu',
            'C:\Program Files\Amazon Corretto',
            'C:\Program Files\BellSoft'
        )
        if ($env:LOCALAPPDATA) { $roots += (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium') }
    } else {
        $roots = @('/usr/lib/jvm', '/opt', '/Library/Java/JavaVirtualMachines')
    }
    foreach ($r in $roots) {
        if (Test-Path -LiteralPath $r) {
            foreach ($d in Get-ChildItem -LiteralPath $r -Directory -ErrorAction SilentlyContinue) {
                $h = $d.FullName
                $mac = Join-Path $h 'Contents/Home'
                if (Test-Path -LiteralPath $mac) { $h = $mac }
                if (Test-Path -LiteralPath (Get-JavaExe $h)) { $homes.Add($h) }
            }
        }
    }
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) {
        $h = Split-Path -Parent (Split-Path -Parent $onPath.Source)
        if (Test-Path -LiteralPath (Join-Path $h 'release')) { $homes.Add($h) }
    }
    return $homes | Select-Object -Unique
}

function Select-Java([string]$explicitVar, [bool]$needJdk, [string]$purpose) {
    $explicit = [Environment]::GetEnvironmentVariable($explicitVar)
    if ($explicit) {
        $explicit = $explicit.TrimEnd('\', '/')
        if (-not (Test-Path -LiteralPath (Get-JavaExe $explicit))) {
            Stop-Cerb "$explicitVar is set to '$explicit' but there is no Java there. Fix or clear $explicitVar."
        }
        $major = Get-JavaMajor $explicit
        if ($major -ne 0 -and $major -lt $MinJava) { Stop-Cerb "$explicitVar points to Java $major; $purpose needs Java $MinJava or newer." }
        if ($needJdk -and -not (Test-IsJdk $explicit)) { Stop-Cerb "$explicitVar points to a JRE; building needs a JDK (javac)." }
        return [pscustomobject]@{ Home = $explicit; Major = $major; Source = $explicitVar }
    }
    $best = $null
    foreach ($h in Find-JavaHomes) {
        $major = Get-JavaMajor $h
        if ($major -lt $MinJava) { continue }
        if ($needJdk -and -not (Test-IsJdk $h)) { continue }
        if ($null -eq $best -or $major -gt $best.Major) {
            $best = [pscustomobject]@{ Home = $h; Major = $major; Source = 'auto-detected' }
        }
    }
    return $best
}

function Require-Java([string]$explicitVar, [bool]$needJdk, [string]$purpose) {
    $j = Select-Java $explicitVar $needJdk $purpose
    if ($null -eq $j) {
        $kind = if ($needJdk) { 'JDK' } else { 'Java' }
        Stop-Cerb ("No $kind $MinJava+ found for $purpose. Install Eclipse Temurin JDK 21 " +
                   "(https://adoptium.net) or set $explicitVar to its folder.")
    }
    return $j
}

# ---------------------------------------------------------------- server facts
function Get-ServerJar {
    if ($env:CERB_SERVER_JAR) { return Join-Path $ServerDir $env:CERB_SERVER_JAR }
    $default = Join-Path $ServerDir $FoliaJarName
    if (Test-Path -LiteralPath $default) { return $default }
    if (Test-Path -LiteralPath $ServerDir) {
        $other = Get-ChildItem -LiteralPath $ServerDir -Filter 'folia-*.jar' -File -ErrorAction SilentlyContinue |
                 Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($other) { return $other.FullName }
    }
    return $default
}

function Get-ServerProps {
    $props = @{}
    $file = Join-Path $ServerDir 'server.properties'
    if (Test-Path -LiteralPath $file) {
        foreach ($line in Get-Content -LiteralPath $file) {
            if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
            $i = $line.IndexOf('=')
            $props[$line.Substring(0, $i).Trim()] = $line.Substring($i + 1).Trim()
        }
    }
    return $props
}

function Get-ServerPort($props) {
    if ($props.ContainsKey('server-port') -and $props['server-port']) { return [int]$props['server-port'] }
    return 25565
}

function Test-EulaAccepted {
    $file = Join-Path $ServerDir 'eula.txt'
    if (-not (Test-Path -LiteralPath $file)) { return $false }
    return [bool](Select-String -LiteralPath $file -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet)
}

function Test-ServerRunning($props) {
    # A running server holds its port. Try a quick TCP connect to it.
    $port = Get-ServerPort $props
    $hostName = '127.0.0.1'
    if ($props.ContainsKey('server-ip') -and $props['server-ip'] -and $props['server-ip'] -ne '0.0.0.0') { $hostName = $props['server-ip'] }
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($hostName, $port, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(700)) {
            try { $client.EndConnect($iar); return $true } catch { return $false }
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-WorldDirs($props) {
    $level = 'world'
    if ($props.ContainsKey('level-name') -and $props['level-name']) { $level = $props['level-name'] }
    $dirs = @()
    foreach ($n in @($level, "${level}_nether", "${level}_the_end")) {
        $p = Join-Path $ServerDir $n
        if (Test-Path -LiteralPath $p) { $dirs += $p }
    }
    return $dirs
}

function Format-Size([long]$bytes) {
    if ($bytes -ge 1GB) { return ('{0:N1} GB' -f ($bytes / 1GB)) }
    if ($bytes -ge 1MB) { return ('{0:N1} MB' -f ($bytes / 1MB)) }
    return ('{0:N0} KB' -f ($bytes / 1KB))
}

function Get-DirSize([string]$path) {
    $sum = (Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    if ($null -eq $sum) { return 0 }
    return [long]$sum
}

function Get-LanAddresses {
    $ips = @()
    try {
        foreach ($ni in [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
            if ($ni.OperationalStatus -ne 'Up') { continue }
            foreach ($a in $ni.GetIPProperties().UnicastAddresses) {
                $ip = $a.Address
                if ($ip.AddressFamily -eq 'InterNetwork' -and -not [System.Net.IPAddress]::IsLoopback($ip)) { $ips += $ip.ToString() }
            }
        }
    } catch { }
    return $ips
}

# ---------------------------------------------------------------- actions
function Invoke-Status {
    Step 'Cerberus dev status'
    Say "  repo:          $RepoRoot"
    $branch = ''
    try { $branch = (& git -C $RepoRoot rev-parse --abbrev-ref HEAD 2>$null) } catch { }
    if ($branch) { Say "  git branch:    $branch" }

    Step 'Java'
    $b = Select-Java 'BUILD_JAVA_HOME' $true 'building'
    if ($b) { Ok "build JDK:   Java $($b.Major)  $($b.Home)  ($($b.Source))" } else { Bad "no JDK $MinJava+ found for building (install Temurin JDK 21 or set BUILD_JAVA_HOME)" }
    $r = Select-Java 'RUN_JAVA_HOME' $false 'running the server'
    if ($r) { Ok "server Java: Java $($r.Major)  $($r.Home)  ($($r.Source))" } else { Bad "no Java $MinJava+ found for running the server" }

    Step 'Server folder'
    if (-not (Test-Path -LiteralPath $ServerDir)) {
        Bad "no server folder at $ServerDir  (run setup, or set CERB_SERVER_DIR to your existing server folder)"
        return
    }
    Ok "folder:      $ServerDir"
    $jar = Get-ServerJar
    if (Test-Path -LiteralPath $jar) {
        $name = Split-Path -Leaf $jar
        if ($name -eq $FoliaJarName) { Ok "server jar:  $name" } else { Warn "server jar:  $name  (expected $FoliaJarName)" }
    } else { Bad "server jar missing: $jar  (run setup)" }
    if (Test-EulaAccepted) { Ok 'eula.txt:    accepted' } else { Warn 'eula.txt:    not accepted yet (see the restart guide; you accept it yourself)' }

    $props = Get-ServerProps
    if ($props.Count -gt 0) {
        $port = Get-ServerPort $props
        $ip = if ($props.ContainsKey('server-ip') -and $props['server-ip']) { $props['server-ip'] } else { '(all interfaces)' }
        $online = if ($props.ContainsKey('online-mode')) { $props['online-mode'] } else { 'true' }
        $level = if ($props.ContainsKey('level-name')) { $props['level-name'] } else { 'world' }
        Ok "port:        $port   bind: $ip   online-mode: $online   level-name: $level"
    } else { Warn 'server.properties not created yet (it appears after the first start)' }

    $worlds = @(Get-WorldDirs $props)
    if ($worlds.Count -eq 0) { Warn 'no world folders yet (created on first start)' }
    foreach ($w in $worlds) { Ok ("world:       {0}  ({1})" -f (Split-Path -Leaf $w), (Format-Size (Get-DirSize $w))) }

    Step 'Plugin'
    $deployed = Join-Path (Join-Path $ServerDir 'plugins') $PluginJarName
    if (Test-Path -LiteralPath $deployed) {
        $d = Get-Item -LiteralPath $deployed
        Ok ("deployed:    plugins\{0}  ({1:yyyy-MM-dd HH:mm})" -f $PluginJarName, $d.LastWriteTime)
        if (Test-Path -LiteralPath $BuiltJar) {
            $t = Get-Item -LiteralPath $BuiltJar
            if ($t.LastWriteTime -gt $d.LastWriteTime) { Warn 'target\Cerberus.jar is newer than the deployed jar (run deploy or run)' }
        }
    } else { Warn 'Cerberus.jar not deployed yet (run deploy or run)' }
    $others = @(Get-ChildItem -LiteralPath (Join-Path $ServerDir 'plugins') -Filter '*.jar' -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne $PluginJarName })
    foreach ($o in $others) { Warn "other plugin jar: $($o.Name)" }
    $data = Join-Path (Join-Path $ServerDir 'plugins') $PluginDirName
    $db = Join-Path $data 'cerberus.db'
    if (Test-Path -LiteralPath $db) { Ok ("database:    plugins\{0}\cerberus.db  ({1})" -f $PluginDirName, (Format-Size (Get-Item -LiteralPath $db).Length)) }

    Step 'Running?'
    if (Test-ServerRunning $props) {
        Ok "a server is listening on port $(Get-ServerPort $props)  (stop it with 'stop' in its console before deploy/backup)"
    } else { Say "  not running" }
    Say "  log:         $(Join-Path (Join-Path $ServerDir 'logs') 'latest.log')"
    $lan = @(Get-LanAddresses)
    if ($lan.Count -gt 0) { Say "  this PC's LAN address(es): $($lan -join ', ')  (use only if you play from another PC)" }
}

function Invoke-Setup {
    Step 'First-time setup'
    $null = Require-Java 'BUILD_JAVA_HOME' $true 'building'
    foreach ($d in @($ServerDir, (Join-Path $ServerDir 'plugins'))) {
        if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Path $d | Out-Null; Ok "created $d" }
    }
    $jar = Join-Path $ServerDir $FoliaJarName
    if (Test-Path -LiteralPath $jar) {
        Ok "$FoliaJarName already present - not downloading"
    } else {
        Say "  downloading $FoliaJarName from PaperMC ..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $tmp = "$jar.part"
        $old = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
        try { Invoke-WebRequest -Uri $FoliaUrl -OutFile $tmp -UseBasicParsing -UserAgent 'cerberus-dev-setup' }
        finally { $ProgressPreference = $old }
        $hash = (Get-FileHash -LiteralPath $tmp -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $FoliaSha256) {
            Remove-Item -LiteralPath $tmp -Force
            Stop-Cerb "Downloaded jar failed its SHA-256 check (got $hash). Nothing was installed; try again."
        }
        Move-Item -LiteralPath $tmp -Destination $jar
        Ok "downloaded and verified $FoliaJarName"
    }
    if (Test-EulaAccepted) {
        Ok 'eula.txt already accepted'
    } else {
        Warn 'The Minecraft EULA is not accepted for this folder yet.'
        Say  '         Read https://aka.ms/MinecraftEULA, then open test-server\eula.txt'
        Say  '         (created on the first start) and change eula=false to eula=true yourself.'
    }
    Say ''
    Say 'Next: run.bat (build + deploy + start).'
}

function Invoke-Build {
    Step 'Build'
    $j = Require-Java 'BUILD_JAVA_HOME' $true 'building'
    Say "  using Java $($j.Major) at $($j.Home)"
    $env:JAVA_HOME = $j.Home
    $env:PATH = (Join-Path $j.Home 'bin') + [IO.Path]::PathSeparator + $env:PATH
    $mvn = if ($env:CERB_MVN) { $env:CERB_MVN } elseif ($OnWindows) { Join-Path $RepoRoot 'mvnw.cmd' } else { Join-Path $RepoRoot 'mvnw' }
    # A failed wrapper download leaves an empty jar, after which mvnw fails forever with
    # "Could not find or load main class ...MavenWrapperMain". Remove it so mvnw re-downloads.
    $wrapperJar = Join-Path (Join-Path (Join-Path $RepoRoot '.mvn') 'wrapper') 'maven-wrapper.jar'
    if ((Test-Path -LiteralPath $wrapperJar) -and (Get-Item -LiteralPath $wrapperJar).Length -eq 0) {
        Remove-Item -LiteralPath $wrapperJar -Force
        Warn 'removed an empty .mvn\wrapper\maven-wrapper.jar left by a failed download; mvnw will fetch it again'
    }
    Push-Location $RepoRoot
    try {
        & $mvn -B -q clean package
        $code = $LASTEXITCODE
    } finally { Pop-Location }
    if ($code -ne 0) { Stop-Cerb "Build failed (exit $code). Scroll up for the compiler or test error." }
    if (-not (Test-Path -LiteralPath $BuiltJar)) { Stop-Cerb "Build finished but $BuiltJar is missing." }
    Ok ("built target\{0}  ({1})" -f $PluginJarName, (Format-Size (Get-Item -LiteralPath $BuiltJar).Length))
}

function Backup-PluginData {
    # Cheap safety net before every deploy/start: copy the plugin database + configs.
    $data = Join-Path (Join-Path $ServerDir 'plugins') $PluginDirName
    if (-not (Test-Path -LiteralPath $data)) { return }
    $root = Join-Path (Join-Path $ServerDir 'backups') 'plugin-data'
    $dest = Join-Path $root (Get-Date -Format 'yyyyMMdd-HHmmss')
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Get-ChildItem -LiteralPath $data -File | Where-Object { $_.Extension -in '.db', '.yml', '.yaml' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $dest }
    Ok "plugin data backed up to backups\plugin-data\$(Split-Path -Leaf $dest)"
    $all = @(Get-ChildItem -LiteralPath $root -Directory | Sort-Object Name -Descending)
    if ($all.Count -gt $KeepDataBackups) {
        $all | Select-Object -Skip $KeepDataBackups | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force }
    }
}

function Assert-Stopped([string]$why) {
    $props = Get-ServerProps
    if (Test-ServerRunning $props) {
        Stop-Cerb ("The server is running on port $(Get-ServerPort $props). Type 'stop' in its console window, " +
                   "wait for it to close, then $why again.")
    }
}

function Invoke-Deploy {
    Assert-Stopped 'deploy'
    Invoke-Build
    Step 'Deploy'
    $plugins = Join-Path $ServerDir 'plugins'
    if (-not (Test-Path -LiteralPath $plugins)) { Stop-Cerb "No $plugins folder. Run setup first (or set CERB_SERVER_DIR)." }
    Backup-PluginData
    Copy-Item -LiteralPath $BuiltJar -Destination (Join-Path $plugins $PluginJarName) -Force
    Ok "copied to plugins\$PluginJarName"
}

function Invoke-Start([bool]$skipBackup = $false) {
    Step 'Start'
    $props = Get-ServerProps
    if (Test-ServerRunning $props) { Stop-Cerb "A server is already running on port $(Get-ServerPort $props). Use that one, or stop it first." }
    $jar = Get-ServerJar
    if (-not (Test-Path -LiteralPath $jar)) { Stop-Cerb "Server jar not found: $jar. Run setup first." }
    if (-not (Test-Path -LiteralPath (Join-Path (Join-Path $ServerDir 'plugins') $PluginJarName))) { Warn 'Cerberus.jar is not in plugins\ - use run (build+deploy+start) instead.' }
    if (-not (Test-EulaAccepted)) {
        Warn 'eula.txt is not accepted. The server will create eula.txt and stop.'
        Warn 'Read https://aka.ms/MinecraftEULA, set eula=true in test-server\eula.txt yourself, then start again.'
    }
    $j = Require-Java 'RUN_JAVA_HOME' $false 'running the server'
    if (-not $skipBackup) { Backup-PluginData }
    $xms = if ($env:JAVA_XMS) { $env:JAVA_XMS } else { '1G' }
    $xmx = if ($env:JAVA_XMX) { $env:JAVA_XMX } else { '4G' }
    $javaArgs = @("-Xms$xms", "-Xmx$xmx",
        '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled', '-XX:MaxGCPauseMillis=200',
        '-XX:+UnlockExperimentalVMOptions', '-XX:+DisableExplicitGC',
        '-XX:G1HeapRegionSize=8M', '-XX:G1NewSizePercent=30', '-XX:G1MaxNewSizePercent=40',
        '-XX:G1MixedGCLiveThresholdPercent=90', '-XX:+HeapDumpOnOutOfMemoryError',
        '-jar', (Split-Path -Leaf $jar), 'nogui')
    Say "  Java $($j.Major), heap $xms-$xmx, $(Split-Path -Leaf $jar)"
    Say "  Ready when you see:  [CerberusPlugin] Cerberus Plugin has been enabled!"
    Say "  Join from Minecraft Java Edition $FoliaVersion at:  localhost:$(Get-ServerPort $props)  (on this PC)"
    Say "  Stop it by typing:   stop"
    Say ''
    Push-Location $ServerDir
    try { & (Get-JavaExe $j.Home) @javaArgs; $code = $LASTEXITCODE } finally { Pop-Location }
    Say ''
    if ($code -eq 0) { Ok 'server stopped cleanly' } else { Warn "server exited with code $code - check test-server\logs\latest.log" }
}

function Invoke-Backup {
    Assert-Stopped 'back up'
    Step 'Backup (worlds + plugin data)'
    $props = Get-ServerProps
    $dest = Join-Path (Join-Path $ServerDir 'backups') ("full-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    foreach ($w in @(Get-WorldDirs $props)) {
        Copy-Item -LiteralPath $w -Destination $dest -Recurse
        Ok "copied $(Split-Path -Leaf $w)"
    }
    $data = Join-Path (Join-Path $ServerDir 'plugins') $PluginDirName
    if (Test-Path -LiteralPath $data) { Copy-Item -LiteralPath $data -Destination $dest -Recurse; Ok "copied plugins\$PluginDirName" }
    foreach ($f in @('server.properties', 'bukkit.yml', 'spigot.yml', 'ops.json', 'whitelist.json')) {
        $p = Join-Path $ServerDir $f
        if (Test-Path -LiteralPath $p) { Copy-Item -LiteralPath $p -Destination $dest }
    }
    $cfg = Join-Path $ServerDir 'config'
    if (Test-Path -LiteralPath $cfg) { Copy-Item -LiteralPath $cfg -Destination $dest -Recurse }
    Ok ("backup complete: {0}  ({1})" -f $dest, (Format-Size (Get-DirSize $dest)))
}

# ---------------------------------------------------------------- main
switch ($Command.ToLowerInvariant()) {
    'status' { Invoke-Status }
    'setup'  { Invoke-Setup }
    'build'  { Invoke-Build }
    'deploy' { Invoke-Deploy; Say ''; Say 'Deployed. Start the server (start.bat or run.bat) to load it.' }
    'start'  { Invoke-Start }
    'run'    { Assert-Stopped 'run'; Invoke-Deploy; Invoke-Start $true }
    'backup' { Invoke-Backup }
    default  { Stop-Cerb "Unknown command '$Command'. Use: status, setup, build, deploy, start, run, backup." }
}
exit 0
