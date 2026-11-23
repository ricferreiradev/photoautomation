$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$tools = Join-Path $root '.tools'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
function Download($url, $target) {
    if (!(Test-Path -LiteralPath $target)) {
        & curl.exe --fail --location --retry 3 --silent --show-error $url --output "$target.partial"
        if ($LASTEXITCODE -ne 0) { throw "Falha no download: $url" }
        Move-Item -LiteralPath "$target.partial" -Destination $target
    }
}
function Verify($file, $expected) {
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $expected) { throw "SHA-256 incorreto: $file" }
}
Write-Output 'Preparando JDK 17...'
$jdkHome = Join-Path $tools 'jdk'
if (!(Test-Path "$jdkHome\bin\java.exe")) {
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $assets[0].binary.package
    $archive = Join-Path $tools 'jdk17.zip'
    Download $package.link $archive
    Verify $archive $package.checksum
    $stage = Join-Path $tools 'jdk-extract'
    Expand-Archive -LiteralPath $archive -DestinationPath $stage -Force
    $extracted = Get-ChildItem -LiteralPath $stage -Directory | Select-Object -First 1
    Move-Item -LiteralPath $extracted.FullName -Destination $jdkHome
}
$env:JAVA_HOME = $jdkHome
$env:PATH = "$jdkHome\bin;$env:PATH"
Write-Output 'Preparando Android command-line tools...'
$sdk = Join-Path $tools 'android-sdk'
$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (!(Test-Path $sdkManager)) {
    $archive = Join-Path $tools 'android-commandlinetools.zip'
    Download 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' $archive
    $stage = Join-Path $tools 'android-extract'
    Expand-Archive -LiteralPath $archive -DestinationPath $stage -Force
    New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null
    Move-Item -LiteralPath "$stage\cmdline-tools" -Destination "$sdk\cmdline-tools\latest"
}
$env:ANDROID_HOME = $sdk
$env:GRADLE_USER_HOME = Join-Path $tools 'gradle-user-home'
Write-Output 'Aceitando licencas e instalando SDK 35, build-tools e adb...'
1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdk" --licenses
if ($LASTEXITCODE -ne 0) { throw 'Falha ao configurar licencas Android.' }
& $sdkManager "--sdk_root=$sdk" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar SDK.' }
Write-Output 'Preparando Gradle 8.11.1...'
$gradle = Join-Path $tools 'gradle-8.11.1\bin\gradle.bat'
if (!(Test-Path $gradle)) {
    $archive = Join-Path $tools 'gradle-8.11.1-bin.zip'
    Download 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' $archive
    $checksum = (Invoke-RestMethod 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256').Trim()
    Verify $archive $checksum
    Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
}
$sdkProperty = $sdk.Replace('\', '/')
Set-Content -LiteralPath (Join-Path $root 'local.properties') -Value "sdk.dir=$sdkProperty" -Encoding ASCII
& "$jdkHome\bin\java.exe" -version
& "$sdk\platform-tools\adb.exe" version
& $gradle --version
if ($LASTEXITCODE -ne 0) { throw 'Falha ao verificar Gradle.' }
Write-Output 'Ambiente instalado.'
