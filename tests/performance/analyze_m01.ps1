param([Parameter(Mandatory)][string]$Evidence)
$ErrorActionPreference='Stop'
function Percentile($values,[double]$q){
 $sorted=@($values | Sort-Object)
 if($sorted.Count -eq 0){return -1}
 return $sorted[[int][Math]::Ceiling($q*$sorted.Count)-1]
}
function Peak-Intervals($traces,[string]$start,[string]$end){
 $events=[Collections.Generic.List[object]]::new()
 foreach($trace in $traces){
  if($trace.$start -ge 0 -and $trace.$end -ge $trace.$start){
   $events.Add(@{time=[long]$trace.$start;delta=1})
   $events.Add(@{time=[long]$trace.$end;delta=-1})
  }
 }
 $current=0;$maximum=0
 foreach($event in ($events | Sort-Object { $_.time },{ $_.delta })){
  $current+=$event.delta;$maximum=[Math]::Max($maximum,$current)
 }
 return $maximum
}
$runs=Get-Content -Raw -Encoding utf8 (Join-Path $Evidence 'runs.json') | ConvertFrom-Json
$report=foreach($run in $runs){
 $name=$run.name
 $summary=Get-Content -Raw -Encoding utf8 (Join-Path $Evidence "$name.json") | ConvertFrom-Json
 $server=Get-Content -Raw -Encoding utf8 (Join-Path $Evidence "$name-server-traces.json") | ConvertFrom-Json
 $clients=@(Get-Content -Raw -Encoding utf8 (Join-Path $Evidence "$name-client-traces.json") | ConvertFrom-Json)
 $byId=@{};foreach($trace in $server.traces){$byId[[int]$trace.id]=$trace}
 $classified=@{admission_rejected=0;database_filter=0;database_global=0;other_503=0;not_observed_in_filter=0}
 $missing=[Collections.Generic.List[int]]::new();$statusMismatch=[Collections.Generic.List[int]]::new()
 foreach($client in $clients){
  $trace=$byId[[int]$client.id]
  if(-not $trace){$missing.Add([int]$client.id)}
  elseif($trace.status -ne $client.status){$statusMismatch.Add([int]$client.id)}
  if($client.status -eq 503){
   $reason=if($trace -and $trace.status -eq 503){$trace.reason503}else{'not_observed_in_filter'}
   if(-not $classified.ContainsKey($reason)){$reason='other_503'}
   $classified[$reason]++
  }
 }
 $clientIds=@($clients.id | Sort-Object -Unique)
 $serverOnly=@($server.traces | Where-Object { $_.id -notin $clientIds } | ForEach-Object id)
 $classifiedTotal=($classified.Values | Measure-Object -Sum).Sum
 $filterMs=@($server.traces | ForEach-Object {($_.filterExitNs-$_.filterEnterNs)/1000000.0})
 $dbMs=@($server.traces | Where-Object databaseStartNs -ge 0 | ForEach-Object {($_.databaseEndNs-$_.databaseStartNs)/1000000.0})
 $holdMs=@($server.traces | Where-Object releaseObservedNs -ge 0 | ForEach-Object {($_.releaseObservedNs-$_.admittedNs)/1000000.0})
 [ordered]@{
  trial=$name;requests=$summary.requests;success=$summary.success;successP95Ms=$summary.successP95Ms
  client503=$summary.rejected503;client503Classified=$classified
  count503Balances=($classifiedTotal -eq $summary.rejected503)
  idAndStatusReconciliation=($server.found -and $missing.Count -eq 0 -and $serverOnly.Count -eq 0 -and $statusMismatch.Count -eq 0 -and $server.duplicates -eq 0 -and $server.dropped -eq 0 -and $server.active -eq 0 -and $clientIds.Count -eq $clients.Count -and $server.completed -eq $clients.Count)
  missingOnServer=$missing.ToArray();serverOnlyIds=$serverOnly;statusMismatchIds=$statusMismatch.ToArray()
  serverEntered=$server.entered;serverAdmitted=$server.admitted;serverCompleted=$server.completed
  serverFilterIntervalPeak=(Peak-Intervals $server.traces 'filterEnterNs' 'filterExitNs')
  serverObservedAdmittedIntervalPeak=(Peak-Intervals $server.traces 'admittedNs' 'releaseObservedNs')
  intervalMeaning='application filter and observed admitted section; not complete network request/response interval'
  filterP95Ms=(Percentile $filterMs .95);databaseLookupIncludingPoolAndMappingP95Ms=(Percentile $dbMs .95)
  observedPermitSectionP95Ms=(Percentile $holdMs .95)
  clientWrittenResponseIntervalPeak=$summary.clientWrittenResponseIntervalPeak
  clientWriteCompletedSpreadMs=$summary.writeCompletedSpreadMs
  businessGate=$summary.pass
 }
}
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $Evidence 'reconciliation.json') -Encoding utf8
$report | ForEach-Object {[pscustomobject]$_} | Select-Object trial,success,client503,count503Balances,idAndStatusReconciliation,serverFilterIntervalPeak,clientWrittenResponseIntervalPeak | Format-Table
