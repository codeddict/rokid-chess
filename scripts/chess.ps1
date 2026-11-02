# Developer build/install only. The installed app needs no PC, USB bridge or API key.
param([switch]$Setup,[switch]$Build,[switch]$Install,[switch]$Tests)
$ErrorActionPreference='Stop'
$chessRoot=Split-Path $PSScriptRoot -Parent
Push-Location $chessRoot
try {
    $chessJdk=Get-ChildItem "$chessRoot/.tools/jdk" -Directory | Select-Object -First 1
    if(-not $chessJdk){throw 'Local JDK missing in .tools/jdk'}
    $env:JAVA_HOME=$chessJdk.FullName
    if($Setup){
        & ./.tools/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat --sdk_root="$chessRoot/.tools/android-sdk" 'ndk;28.2.13676358'
        if($LASTEXITCODE -ne 0){throw 'NDK setup failed'}
        python ./scripts/build-chess-engine.py
        if($LASTEXITCODE -ne 0){throw 'Android engine build failed'}
        if(-not (Test-Path -LiteralPath chessphone/libs/chesslib-1.3.6.jar)){
            New-Item -ItemType Directory -Force chessphone/libs | Out-Null
            Invoke-WebRequest 'https://jitpack.io/com/github/bhlangonijr/chesslib/1.3.6/chesslib-1.3.6.jar' -OutFile chessphone/libs/chesslib-1.3.6.jar
        }
    }
    if($Build -or $Install -or $Tests){
        $chessTasks=@(':chessphone:clean',':chessphone:assembleDebug',':chessphone:lintDebug',':chessglasses:lintDebug')
        if($Tests){$chessTasks+=@(':chessphone:testDebugUnitTest',':chessglasses:testDebugUnitTest')}
        & ./gradlew.bat --no-daemon --console=plain @chessTasks
        if($LASTEXITCODE -ne 0){throw 'Chess build/check failed'}
        Copy-Item -LiteralPath chessphone/build/outputs/apk/debug/chessphone-debug.apk -Destination artifacts/rokid-chess-v0.6.1.apk
    }
    if($Install){
        & ./.tools/platform-tools/adb.exe install -r artifacts/rokid-chess-v0.6.1.apk
        if($LASTEXITCODE -ne 0){throw 'Android phone installation failed'}
        & ./.tools/platform-tools/adb.exe shell am start -n com.sjtec.rokidchess/.MainActivity
        if($LASTEXITCODE -ne 0){throw 'Android phone launch failed'}
    }
    Write-Host 'Phone APK installs the standalone glasses app. After installation, launch it from the glasses app list. No phone or PC is required during play.'
} finally {Pop-Location}
