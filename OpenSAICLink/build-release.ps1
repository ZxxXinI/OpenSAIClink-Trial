$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$env:JAVA_HOME='E:/Soft/Dev/jetbrains/jdk/jdk-17'
$env:GRADLE_USER_HOME=Join-Path $PSScriptRoot '.gradle-user'
$env:ANDROID_USER_HOME=Join-Path $PSScriptRoot '.android-user'
$taskTemp=Join-Path $PSScriptRoot '.tmp'
New-Item -ItemType Directory -Path $taskTemp -Force | Out-Null
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir="'+$taskTemp+'" -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
& "$PSScriptRoot/gradlew.bat" :app:assembleRelease :app:lintRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build or lint failed' }
& 'E:/Soft/Dev/anaconda3/python.exe' "$PSScriptRoot/tests/run_offline.py"
if ($LASTEXITCODE -ne 0) { throw 'Offline checks failed' }
& 'E:/Soft/Dev/anaconda3/python.exe' "$PSScriptRoot/tests/package_release.py"
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
