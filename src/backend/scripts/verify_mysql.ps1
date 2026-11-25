param(
    [string]$MysqlBin = $env:MYSQL_BIN
)

# Uses an isolated server and random credentials; never connects to the normal development database.
$ErrorActionPreference = 'Stop'
if (-not $MysqlBin) { throw 'Set MYSQL_BIN to the bin directory of MySQL 8.4.' }
$server = (Resolve-Path (Join-Path $MysqlBin 'mysqld.exe')).Path
$mysqlBase = Split-Path (Split-Path $server)
$runDir = Join-Path $env:TEMP ("moments-m01-verify-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runDir | Out-Null
$dataDir = Join-Path $runDir 'data'
$initFile = Join-Path $runDir 'init.sql'
$mysqlLog = Join-Path $runDir 'mysql.log'
$testPassword = [guid]::NewGuid().ToString('N')
$rootPassword = [guid]::NewGuid().ToString('N')
$envNames = @('MOMENTS_DB_URL', 'MOMENTS_DB_USER', 'MOMENTS_DB_PASSWORD', 'MOMENTS_JWT_SECRET')
$previousEnv = @{}
foreach ($name in $envNames) { $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$instance = $null
$exitCode = 1
Push-Location (Split-Path $PSScriptRoot)
try {
    & $server --no-defaults "--basedir=$mysqlBase" "--datadir=$dataDir" "--log-error=$mysqlLog" --initialize-insecure
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed. See $mysqlLog" }
    @(
        "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';"
        "CREATE DATABASE moments_m01_test CHARACTER SET utf8mb4;"
        "CREATE USER 'm01_test'@'127.0.0.1' IDENTIFIED BY '$testPassword';"
        "GRANT ALL ON moments_m01_test.* TO 'm01_test'@'127.0.0.1';"
    ) | Set-Content -LiteralPath $initFile -Encoding ascii
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    $instance = Start-Process -FilePath $server -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults', ('--basedir="{0}"' -f $mysqlBase), ('--datadir="{0}"' -f $dataDir),
        ('--log-error="{0}"' -f $mysqlLog), ('--init-file="{0}"' -f $initFile),
        "--port=$port", '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-name-resolve'
    )
    $ready = $false
    for ($attempt = 0; $attempt -lt 120; $attempt++) {
        if ($instance.HasExited) { break }
        $probe = [Net.Sockets.TcpClient]::new()
        try { $probe.Connect('127.0.0.1', $port); $ready = $true; break }
        catch {} finally { $probe.Dispose() }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw "MySQL did not start. See $mysqlLog" }
    Remove-Item -LiteralPath $initFile
    $env:MOMENTS_DB_URL = "jdbc:mysql://127.0.0.1:${port}/moments_m01_test"
    $env:MOMENTS_DB_USER = 'm01_test'
    $env:MOMENTS_DB_PASSWORD = $testPassword
    $env:MOMENTS_JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    & .\mvnw.cmd -B -ntp verify
    $exitCode = $LASTEXITCODE
} finally {
    if ($instance -and -not $instance.HasExited) {
        Stop-Process -Id $instance.Id
        $instance.WaitForExit(10000) | Out-Null
    }
    if (Test-Path -LiteralPath $initFile) { Remove-Item -LiteralPath $initFile }
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process') }
    Pop-Location
    Write-Output "Isolated MySQL logs: $mysqlLog"
}
exit $exitCode
