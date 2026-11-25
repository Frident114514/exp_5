param([Parameter(Mandatory)][string]$MysqlBin,[int]$MaxBurst=10000,[switch]$BurstOnly,[int]$MeConcurrency=64,[int]$WarmupSeconds=20,[int]$WarmupRate=1000,[switch]$Profile,[switch]$Calibrate)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$evidence=Join-Path $repo ("tests/evidence/M01/performance-"+(Get-Date -Format 'yyyy-MM-dd-HHmmss'))
$privateDir=Join-Path $env:TEMP ("m01-load-"+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $privateDir,$evidence | Out-Null
$server=(Resolve-Path (Join-Path $MysqlBin 'mysqld.exe')).Path
$client=Join-Path $MysqlBin 'mysql.exe'
$base=Split-Path (Split-Path $server)
$initFile=Join-Path $privateDir 'init.sql'
$dbPassword=[guid]::NewGuid().ToString('N')
$rootPassword=[guid]::NewGuid().ToString('N')
$password=[guid]::NewGuid().ToString('N')
$jwtKey=[Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
$db=$null; $app=$null; $load=$null
Add-Type -Path (Join-Path $PSScriptRoot 'M01Load.cs')
$names=@('MOMENTS_DB_URL','MOMENTS_DB_USER','MOMENTS_DB_PASSWORD','MOMENTS_JWT_SECRET','MOMENTS_ME_CONCURRENCY','MOMENTS_DIAGNOSTICS_ENABLED','SERVER_TOMCAT_CONNECTION_TIMEOUT','SPRING_PROFILES_ACTIVE','SERVER_PORT','SERVER_ADDRESS','MYSQL_PWD','M01_USERS_FILE','BASE_URL','MODE','RATE','DURATION','BURST_VUS','VUS','MAX_VUS','K6_SUMMARY_TREND_STATS')
$previous=@{}
foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
function Free-Port {
 $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
 $listener.Start(); $p=$listener.LocalEndpoint.Port; $listener.Stop(); return $p
}
function B64Url([byte[]]$bytes){return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')}
try {
 & $server --no-defaults "--basedir=$base" "--datadir=$(Join-Path $privateDir 'data')" "--log-error=$(Join-Path $privateDir 'mysql.log')" --initialize-insecure
 if($LASTEXITCODE -ne 0){throw 'MySQL initialization failed'}
 @("ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';","CREATE DATABASE m01_load CHARACTER SET utf8mb4;","CREATE USER 'm01_load'@'127.0.0.1' IDENTIFIED BY '$dbPassword';","GRANT ALL ON m01_load.* TO 'm01_load'@'127.0.0.1';") | Set-Content -LiteralPath $initFile -Encoding ascii
 $dbPort=Free-Port
 $db=Start-Process $server -WindowStyle Hidden -PassThru -ArgumentList @('--no-defaults',('--basedir="{0}"' -f $base),('--datadir="{0}"' -f (Join-Path $privateDir 'data')),('--init-file="{0}"' -f $initFile),('--log-error="{0}"' -f (Join-Path $privateDir 'mysql.log')),"--port=$dbPort",'--bind-address=127.0.0.1','--mysqlx=OFF','--skip-name-resolve')
 $ready=$false
 for($i=0;$i -lt 120;$i++){
  $socket=[Net.Sockets.TcpClient]::new()
  try{$socket.Connect('127.0.0.1',$dbPort);$ready=$true;break}catch{}finally{$socket.Dispose()}
  if($db.HasExited){throw 'MySQL exited'}
  Start-Sleep -Milliseconds 250
 }
 if(-not $ready){throw 'MySQL startup timed out'}
 # MySQL on Windows may launch a monitored worker; sample and stop that worker too.
 $dbWorkerInfo=Get-CimInstance Win32_Process -Filter "Name='mysqld.exe' AND ParentProcessId=$($db.Id)" | Select-Object -First 1
 $dbWorker=if($dbWorkerInfo){Get-Process -Id $dbWorkerInfo.ProcessId}else{$db}
 Remove-Item -LiteralPath $initFile
 $env:MOMENTS_DB_URL="jdbc:mysql://127.0.0.1:${dbPort}/m01_load"
 $env:MOMENTS_DB_USER='m01_load';$env:MOMENTS_DB_PASSWORD=$dbPassword;$env:MYSQL_PWD=$dbPassword
 $env:MOMENTS_JWT_SECRET=[Convert]::ToBase64String($jwtKey)
 $env:MOMENTS_ME_CONCURRENCY=[string]$MeConcurrency
 $env:MOMENTS_DIAGNOSTICS_ENABLED='true'
 if($BurstOnly){$env:SERVER_TOMCAT_CONNECTION_TIMEOUT='60s'}
 $env:SPRING_PROFILES_ACTIVE='high-concurrency';$env:SERVER_ADDRESS='127.0.0.1';$env:SERVER_PORT=[string](Free-Port)
 $env:BASE_URL="http://127.0.0.1:$env:SERVER_PORT/api/v1"
 $java=Join-Path $env:JAVA_HOME 'bin/java.exe'
 $jar=Join-Path $repo 'src/backend/target/moments-backend-0.1.0-SNAPSHOT.jar'
 $app=Start-Process $java -WindowStyle Hidden -PassThru -ArgumentList @('-Xms256m','-Xmx2g','-jar',('"{0}"' -f $jar)) -RedirectStandardOutput (Join-Path $evidence 'server.log') -RedirectStandardError (Join-Path $evidence 'server-error.log')
 $ready=$false
 for($i=0;$i -lt 90;$i++){
  try{$probe=Invoke-WebRequest "$env:BASE_URL/users/me" -SkipHttpErrorCheck -TimeoutSec 2;if($probe.StatusCode -eq 401){$ready=$true;break}}catch{}
  if($app.HasExited){throw 'Application exited during startup'}
  Start-Sleep -Milliseconds 500
 }
 if(-not $ready){throw 'Application startup timed out'}
 $seed=@{account='m01_seed';password=$password;nickname='Load seed'} | ConvertTo-Json
 Invoke-RestMethod "$env:BASE_URL/auth/register" -Method Post -ContentType 'application/json' -Body $seed | Out-Null
 $sql=@'
CREATE TABLE digit(n INT);
INSERT INTO digit VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);
INSERT INTO users(account,password_hash,nickname,role,status,token_version,created_at)
SELECT CONCAT('load_',a.n,b.n,c.n,d.n),seed.password_hash,'Load user','USER','ACTIVE',0,UTC_TIMESTAMP(6)
FROM digit a CROSS JOIN digit b CROSS JOIN digit c CROSS JOIN digit d CROSS JOIN users seed
WHERE seed.account='m01_seed';
DROP TABLE digit;
SELECT id,account FROM users WHERE account LIKE 'load_%' ORDER BY id;
'@
 $accounts=$sql | & $client --protocol=TCP --host=127.0.0.1 "--port=$dbPort" --user=m01_load --batch --skip-column-names m01_load
 if($LASTEXITCODE -ne 0 -or $accounts.Count -ne 10000){throw 'Expected 10000 synthetic users'}
 $now=[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
 $header=B64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
 $signer=[Security.Cryptography.HMACSHA256]::new($jwtKey)
 $fixtures=foreach($row in $accounts){
  $columns=$row -split [char]9
  $claims=@{iss='moments-backend';sub=$columns[0];iat=$now;exp=$now+43200;ver=0} | ConvertTo-Json -Compress
  $body=B64Url ([Text.Encoding]::UTF8.GetBytes($claims))
  $unsigned="$header.$body"
  $signature=B64Url ($signer.ComputeHash([Text.Encoding]::ASCII.GetBytes($unsigned)))
  @{account=$columns[1];password=$password;accessToken="$unsigned.$signature"}
 }
 $signer.Dispose()
 $env:M01_USERS_FILE=Join-Path $privateDir 'users.local.json'
 $fixtures | ConvertTo-Json -Compress | Set-Content -LiteralPath $env:M01_USERS_FILE -Encoding utf8NoBOM
 $first=Invoke-RestMethod "$env:BASE_URL/users/me" -Headers @{Authorization="Bearer $($fixtures[0].accessToken)"}
 if($first.data.account -ne $fixtures[0].account){throw 'Token fixture validation failed'}
 # Only the isolated seed account can read experiment observations; tokens stay private.
 "UPDATE users SET role='ADMIN' WHERE account='m01_seed';" | & $client --protocol=TCP --host=127.0.0.1 "--port=$dbPort" --user=m01_load m01_load
 if($LASTEXITCODE -ne 0){throw 'Could not prepare diagnostics reader'}
 $admin=Invoke-RestMethod "$env:BASE_URL/auth/login" -Method Post -ContentType 'application/json' -Body $seed
 $diagnosticHeaders=@{Authorization="Bearer $($admin.data.accessToken)"}
 if($Calibrate){
  [Threading.ThreadPool]::SetMinThreads(128,128) | Out-Null
  Write-Output 'START load-generator-calibration (not business capacity)'
  $calibration=[M01Load.Runner]::Calibrate($env:M01_USERS_FILE,10000,(Join-Path $evidence 'calibration-client-traces.json'))
  $calibration.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $evidence 'calibration.json') -Encoding utf8
  Write-Output 'END load-generator-calibration'
 }
 $computer=Get-CimInstance Win32_ComputerSystem
 @{date=(Get-Date -Format o);cpu=(Get-CimInstance Win32_Processor | Select-Object -ExpandProperty Name);logicalProcessors=$computer.NumberOfLogicalProcessors;memoryBytes=$computer.TotalPhysicalMemory;profile='high-concurrency';javaHeapMax='2g';users=10000;database='MySQL 8.4.13';topology='dotnet load client Java MySQL on same Windows machine';authConcurrency=4;authRate=10;authBurst=20;meConcurrency=$MeConcurrency;dbPool=20;httpConnections=16384;engine='dotnet-async-http';dotnet=[Environment]::Version.ToString()} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'environment.json') -Encoding utf8
 $trials=@(
  @{Name='warmup';Mode='me';Rate=20;Duration='10s'},
  @{Name='me-100rps';Mode='me';Rate=100;Duration='20s'},
  @{Name='me-500rps';Mode='me';Rate=500;Duration='20s'},
  @{Name='me-1000rps';Mode='me';Rate=1000;Duration='20s'},
  @{Name='login-2rps';Mode='login';Rate=2;Duration='20s'},
  @{Name='login-20rps';Mode='login';Rate=20;Duration='20s'},
  @{Name='burst-100';Mode='me';Burst=100;Duration='30s'},
  @{Name='burst-1000';Mode='me';Burst=1000;Duration='30s'}
 )
 if($MaxBurst -ge 10000){$trials+=@{Name='burst-10000';Mode='me';Burst=10000;Duration='30s'}}
 $trials+=@{Name='recovery-100rps';Mode='me';Rate=100;Duration='20s'}
 if($BurstOnly){
  [Threading.ThreadPool]::SetMinThreads(128,128) | Out-Null
  $trials=@(@{Name='warmup';Mode='me';Rate=$WarmupRate;Duration="${WarmupSeconds}s"},
    @{Name='preconnected-10000';Mode='me';Burst=10000;Duration='30s';Preconnect=$true},
    @{Name='recovery-100rps';Mode='me';Rate=100;Duration='10s'})
 }
 @{meConcurrency=$MeConcurrency;warmupSeconds=$WarmupSeconds;warmupRate=$WarmupRate;serverConnectionTimeoutSeconds=if($BurstOnly){60}else{5};serverConnectionTimeoutScope='entire Java service process, including all measured requests';dispatch='10000 asynchronous tasks released by one gate';burstOnly=[bool]$BurstOnly;preconnectedClients=if($BurstOnly){10000}else{0};responseReading='immediate, no artificial delay in business test';requestTimeoutSeconds=15;diagnosticsEnabled=$true;calibration=[bool]$Calibrate;threadPoolMinimum=if($BurstOnly){128}else{'runtime default'};jfr=[bool]$Profile} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'experiment.json') -Encoding utf8
 $samples=[Collections.Generic.List[object]]::new();$runs=[Collections.Generic.List[object]]::new()
 foreach($trial in $trials){
  $env:MODE=$trial.Mode;$env:RATE=[string]$trial.Rate;$env:DURATION=$trial.Duration;$env:BURST_VUS=[string]$trial.Burst
  $env:VUS='100';$env:MAX_VUS='1000';$env:K6_SUMMARY_TREND_STATS='avg,min,med,max,p(90),p(95),p(99)'
  $summary=Join-Path $evidence "$($trial.Name).json"
  Write-Output "START $($trial.Name)"
  $started=Get-Date
  $seconds=[int]$trial.Duration.TrimEnd('s')
  if($Profile -and $trial.Preconnect){
   & (Join-Path $env:JAVA_HOME 'bin/jcmd.exe') $app.Id JFR.start name=m01 settings=profile | Out-Null
   if($LASTEXITCODE -ne 0){throw 'JFR start failed'}
  }
  $load=[M01Load.Runner]::Run($env:M01_USERS_FILE,$env:BASE_URL,$trial.Mode,[int]$trial.Rate,$seconds,[int]$trial.Burst,[bool]$trial.Preconnect,$trial.Name,(Join-Path $evidence "$($trial.Name)-client-traces.json"))
  while(-not $load.IsCompleted){
   $app.Refresh();$dbWorker.Refresh();$self=Get-Process -Id $PID;$os=Get-CimInstance Win32_OperatingSystem
   $samples.Add(@{trial=$trial.Name;phase=[M01Load.Runner]::Phase;requestPhaseElapsedMs=[M01Load.Runner]::PhaseElapsedMs;time=(Get-Date -Format o);appCpuSeconds=$app.TotalProcessorTime.TotalSeconds;appWorkingSet=$app.WorkingSet64;appPrivate=$app.PrivateMemorySize64;dbCpuSeconds=$dbWorker.TotalProcessorTime.TotalSeconds;dbWorkingSet=$dbWorker.WorkingSet64;loadCpuSeconds=$self.TotalProcessorTime.TotalSeconds;loadWorkingSet=$self.WorkingSet64;freeMemoryKB=$os.FreePhysicalMemory;loadGcCollections=@([GC]::CollectionCount(0),[GC]::CollectionCount(1),[GC]::CollectionCount(2));loadThreadPoolPending=[Threading.ThreadPool]::PendingWorkItemCount})
   if($os.FreePhysicalMemory -lt 1500000){[M01Load.Runner]::Cancel();throw 'Load stopped to preserve host memory'}
   Start-Sleep -Milliseconds 100
  }
  $result=$load.GetAwaiter().GetResult()
  $result | Set-Content -LiteralPath $summary -Encoding utf8
  $metrics=$result | ConvertFrom-Json
  $snapshot=Invoke-RestMethod "$env:BASE_URL/internal/m01-diagnostics?trial=$($trial.Name)" -Headers $diagnosticHeaders
  # A response can reach the client just before the server filter finally runs.
  for($attempt=0;$attempt -lt 50 -and $snapshot.active -ne 0;$attempt++){
   Start-Sleep -Milliseconds 20
   $snapshot=Invoke-RestMethod "$env:BASE_URL/internal/m01-diagnostics?trial=$($trial.Name)" -Headers $diagnosticHeaders
  }
  $snapshot | ConvertTo-Json -Depth 12 -Compress | Set-Content -LiteralPath (Join-Path $evidence "$($trial.Name)-server-traces.json") -Encoding utf8
  if($Profile -and $trial.Preconnect){
   $profilePath=Join-Path $evidence 'spike.local.jfr'
   & (Join-Path $env:JAVA_HOME 'bin/jcmd.exe') $app.Id JFR.dump name=m01 "filename=$profilePath" | Out-Null
   & (Join-Path $env:JAVA_HOME 'bin/jcmd.exe') $app.Id JFR.stop name=m01 | Out-Null
   @{path=$profilePath;burstEndUtc=[DateTime]::UtcNow.ToString('o');burstSeconds=$metrics.elapsedSeconds} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'profile-location.local.json') -Encoding utf8
  }
  $runs.Add(@{name=$trial.Name;pass=$metrics.pass;elapsedSeconds=((Get-Date)-$started).TotalSeconds})
  $runs | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'runs.json') -Encoding utf8
  $samples | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'resources.json') -Encoding utf8
  Write-Output "END $($trial.Name) pass=$($metrics.pass) success=$($metrics.success)/$($metrics.requests) P95=$($metrics.successP95Ms) ms"
 }
 & (Join-Path $PSScriptRoot 'analyze_m01.ps1') -Evidence $evidence
 Write-Output "Evidence: $evidence"
}finally{
 if($load -and -not $load.IsCompleted){[M01Load.Runner]::Cancel()}
 if($db){
  $children=Get-CimInstance Win32_Process -Filter "Name='mysqld.exe' AND ParentProcessId=$($db.Id)"
  foreach($child in $children){Stop-Process -Id $child.ProcessId -ErrorAction SilentlyContinue}
 }
 foreach($p in @($app,$db)){if($p -and -not $p.HasExited){Stop-Process -Id $p.Id -ErrorAction SilentlyContinue}}
 if(Test-Path -LiteralPath $initFile){Remove-Item -LiteralPath $initFile}
 if($env:M01_USERS_FILE -and (Test-Path -LiteralPath $env:M01_USERS_FILE)){Remove-Item -LiteralPath $env:M01_USERS_FILE}
 foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}
