param([string]$Serial)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\android-env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $devices = & adb.exe devices
    $connected = @($devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if (!$Serial) {
        if ($connected.Count -ne 1) { throw "Conecte e autorize um celular via depuracao USB. Dispositivos: $($devices -join ' '); para escolher, use -Serial." }
        $Serial = $connected[0]
    }
    if ($Serial -notin $connected) { throw 'Celular ausente ou nao autorizado. Aceite a depuracao USB na tela do celular.' }
    & gradle.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falha na compilacao/verificacao.' }
    & adb.exe -s $Serial install -r app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar APK.' }
    & adb.exe -s $Serial shell am start -n com.ricferreira.photoautomation/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao abrir app.' }
} finally { Pop-Location }
