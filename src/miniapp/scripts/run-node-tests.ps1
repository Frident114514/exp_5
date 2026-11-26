$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
  $node = (Get-Command node -ErrorAction Stop).Source
  & $node 'node_modules/typescript/bin/tsc' -p 'tsconfig.node-test.json'
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

  $packageFile = Join-Path $root '.node-test/package.json'
  [System.IO.File]::WriteAllText($packageFile, '{"type":"commonjs"}', (New-Object System.Text.UTF8Encoding($false)))
  & $node '.node-test/tests-node/core.test.js'
  exit $LASTEXITCODE
}
finally {
  Pop-Location
}