param([string]$Serial = 'RQCX204AFQK')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\android-env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & gradle.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar.' }
    & adb.exe -s $Serial install -r app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar app.' }
    & adb.exe -s $Serial install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar teste.' }
    $testOutput = & adb.exe -s $Serial shell am instrument -w -e runAnalysis true -e class com.ricferreira.photoautomation.AnalysisIntegrationTest com.ricferreira.photoautomation.test/androidx.test.runner.AndroidJUnitRunner
    $testOutput | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(1 test\)') { throw 'A analise real falhou. Consulte a mensagem acima.' }
    & adb.exe -s $Serial shell am start -n com.ricferreira.photoautomation/.MainActivity
} finally { Pop-Location }
