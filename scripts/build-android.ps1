$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\android-env.ps1"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & gradle.bat wrapper --gradle-version 8.11.1 --distribution-type bin --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar Gradle Wrapper.' }
    & gradle.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:signingReport --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar/verificar app.' }
} finally { Pop-Location }
