param([switch]$Background)
$ErrorActionPreference = 'Stop'
$occupancyRoot = Split-Path -Parent $PSScriptRoot
$occupancyPython = Join-Path $occupancyRoot '.venv-occupancy/Scripts/python.exe'
if (!(Test-Path -LiteralPath $occupancyPython)) { throw 'Ejecuta primero scripts/setup-occupancy.ps1.' }
if (!(Test-Path -LiteralPath (Join-Path $occupancyRoot 'ml/artifacts/occupancy.keras'))) { throw 'Falta entrenar: python -m ml.generate_dataset y python -m ml.train.' }
try {
    $health = Invoke-RestMethod 'http://127.0.0.1:8001/health' -TimeoutSec 2
    if ($health.ready -and $health.training_data -eq 'SIMULATED_ONLY') { return }
} catch { }
if (Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue) { throw 'Puerto 8001 ocupado por otro servicio.' }
if ($Background) {
    New-Item -ItemType Directory -Path (Join-Path $occupancyRoot 'target') -Force | Out-Null
    Start-Process -FilePath $occupancyPython -ArgumentList '-m','uvicorn','ml.api:app','--host','127.0.0.1','--port','8001' -WorkingDirectory $occupancyRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $occupancyRoot 'target/occupancy-api.out.log') -RedirectStandardError (Join-Path $occupancyRoot 'target/occupancy-api.err.log') | Out-Null
    for ($occupancyAttempt = 0; $occupancyAttempt -lt 30; $occupancyAttempt++) {
        Start-Sleep -Seconds 1
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:8001/health' -TimeoutSec 1
            if ($health.ready) { return }
        } catch { }
    }
    throw 'El modelo no respondio a tiempo; revisa target/occupancy-api.err.log.'
} else {
    Push-Location $occupancyRoot
    try { & $occupancyPython -m uvicorn ml.api:app --host 127.0.0.1 --port 8001 } finally { Pop-Location }
}
