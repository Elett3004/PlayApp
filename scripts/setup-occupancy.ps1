$ErrorActionPreference = 'Stop'
$occupancyRoot = Split-Path -Parent $PSScriptRoot
Push-Location $occupancyRoot
try {
    if (!(Test-Path -LiteralPath '.venv-occupancy/Scripts/python.exe')) {
        python -m venv .venv-occupancy
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el entorno Python.' }
    }
    & ./.venv-occupancy/Scripts/python.exe -m pip install -r ml/requirements.txt
    if ($LASTEXITCODE -ne 0) { throw 'Fallo al instalar las dependencias.' }
    & ./.venv-occupancy/Scripts/python.exe -m ml.generate_dataset
    if ($LASTEXITCODE -ne 0) { throw 'Fallo al generar el dataset.' }
    & ./.venv-occupancy/Scripts/python.exe -m ml.train
    if ($LASTEXITCODE -ne 0) { throw 'Fallo al entrenar el modelo.' }
} finally { Pop-Location }
