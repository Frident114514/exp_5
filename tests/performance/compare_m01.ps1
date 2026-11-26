param([Parameter(Mandatory)][string[]]$Evidence,[Parameter(Mandatory)][string]$Output)
$ErrorActionPreference='Stop'
function Median($values){
 $sorted=@($values | Sort-Object);$middle=[int][Math]::Floor($sorted.Count/2)
 if($sorted.Count%2){return $sorted[$middle]}
 return ($sorted[$middle-1]+$sorted[$middle])/2
}
$groups=foreach($folder in $Evidence){
 $configuration=Get-Content -Raw -Encoding utf8 (Join-Path $folder 'experiment.json') | ConvertFrom-Json
 $runs=Get-Content -Raw -Encoding utf8 (Join-Path $folder 'runs.json') | ConvertFrom-Json
 $bursts=@(foreach($run in $runs){
  if($run.name -like 'preconnected-10000*'){
   Get-Content -Raw -Encoding utf8 (Join-Path $folder "$($run.name).json") | ConvertFrom-Json
  }
 })
 if($bursts.Count -eq 0){throw 'No completed bursts in group'}
 [ordered]@{
  evidence=(Resolve-Path $folder).Path;diagnostics=$configuration.diagnosticsEnabled
  meConcurrency=$configuration.meConcurrency;meWaiters=$configuration.meWaiters;meWaitMs=$configuration.meWaitMs
  connectionPolicy=$configuration.connectionPolicy;rounds=$bursts.Count
  plannedRounds=$configuration.repetitions;allPlannedRoundsCompleted=($bursts.Count -eq $configuration.repetitions)
  successMedian=(Median $bursts.success);successMinimum=($bursts.success|Measure-Object -Minimum).Minimum
  successMaximum=($bursts.success|Measure-Object -Maximum).Maximum
  successP95MedianMs=(Median $bursts.successP95Ms)
  successP95MinimumMs=($bursts.successP95Ms|Measure-Object -Minimum).Minimum
  successP95MaximumMs=($bursts.successP95Ms|Measure-Object -Maximum).Maximum
  allBusinessGatesPass=(@($bursts|Where-Object {-not $_.pass}).Count -eq 0)
  raw=$bursts
 }
}
[ordered]@{
 method='separate JVM per group, chronological group order, round-specific warmup; exploratory same-host comparison, not randomized or independent replications'
 caution='do not attribute all group differences to one parameter; verify offered write spans and cross-round variation'
 groups=@($groups)
} | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Output -Encoding utf8
$groups | ForEach-Object {[pscustomobject]$_} | Select-Object diagnostics,connectionPolicy,rounds,successMedian,successMinimum,successMaximum,successP95MedianMs,allBusinessGatesPass | Format-Table
