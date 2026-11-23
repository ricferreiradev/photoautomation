$projectRoot = Split-Path $PSScriptRoot -Parent
$env:DEBUG = $null
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk'
$env:ANDROID_HOME = Join-Path $projectRoot '.tools\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-user-home'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$projectRoot\.tools\gradle-8.11.1\bin;$env:PATH"
