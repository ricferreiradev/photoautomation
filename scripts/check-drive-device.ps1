param([switch]$RestoreTrashed, [string]$TestClass = 'com.ricferreira.photoautomation.ConfigDriveIntegrationTest')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\android-env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & gradle.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar diagnostico.' }
    & adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar app.' }
    & adb.exe install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar teste.' }
    if ($RestoreTrashed) {
        $testOutput = & adb.exe shell am instrument -w -e restoreTrashedConfig true -e class com.ricferreira.photoautomation.ConfigDriveIntegrationTest com.ricferreira.photoautomation.test/androidx.test.runner.AndroidJUnitRunner
    } else {
        $testOutput = & adb.exe shell am instrument -w -e class $TestClass com.ricferreira.photoautomation.test/androidx.test.runner.AndroidJUnitRunner
    }
    $testOutput | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($testOutput -join "`n") -notmatch 'OK \(1 test\)') { throw 'Verificacao do Drive no dispositivo encontrou uma falha; consulte o relatorio.' }
} finally { Pop-Location }
