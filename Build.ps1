param([switch]$Install, [switch]$Check, [ValidateSet('release','debug')][string]$Variant='release')
$ErrorActionPreference='Stop'
$taskProject=$PSScriptRoot
$taskWork=Join-Path (Split-Path -Parent $taskProject) 'work'
if(Test-Path -LiteralPath (Join-Path $taskWork 'jdk')) {
    $taskJdk=Get-ChildItem -LiteralPath (Join-Path $taskWork 'jdk') -Directory | Select-Object -First 1
    $env:JAVA_HOME=$taskJdk.FullName
}
$taskSdk=Join-Path $taskWork 'android-sdk'
if(Test-Path -LiteralPath $taskSdk) {
    $env:ANDROID_HOME=$taskSdk
    $env:ANDROID_USER_HOME=Join-Path $taskWork 'android-user'
    $env:GRADLE_USER_HOME=Join-Path $taskWork 'gradle-home'
    $env:JAVA_OPTS='-Duser.home='+ (Join-Path $taskWork 'user-home')
    [IO.File]::WriteAllText((Join-Path $taskProject 'local.properties'),'sdk.dir='+$taskSdk.Replace('\','/')+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
}
if(-not $env:JAVA_HOME) { throw 'Please install JDK 17 and set JAVA_HOME, or open the project in Android Studio.' }
$taskStore=Join-Path $taskWork 'signing/release.jks'
$taskSecretFile=Join-Path $taskWork 'signing/password.clixml'
if(Test-Path -LiteralPath $taskStore) {
    $env:UESTC_STORE_FILE=$taskStore
    $taskSecret=Import-Clixml -LiteralPath $taskSecretFile
    $taskPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecret)
    try {
        $env:UESTC_STORE_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer)
        $env:UESTC_KEY_PASSWORD=$env:UESTC_STORE_PASSWORD
    } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
} elseif($Variant -eq 'release' -and -not $env:UESTC_STORE_FILE) {
    throw 'Release signing key is missing. Use -Variant debug, or configure UESTC_STORE_FILE / UESTC_STORE_PASSWORD / UESTC_KEY_PASSWORD.'
}
$taskTasks=@(':app:assemble'+(Get-Culture).TextInfo.ToTitleCase($Variant))
if($Check) { $taskTasks+=@(':app:testDebugUnitTest',':app:lintRelease',':app:assembleDebugAndroidTest') }
& (Join-Path $taskProject 'gradlew.bat') -p $taskProject @taskTasks --no-daemon
if($LASTEXITCODE -ne 0) { throw 'Gradle build/check failed.' }
if($Install) {
    $taskApk=Join-Path $taskProject ('app/build/outputs/apk/'+$Variant+'/app-'+$Variant+'.apk')
    & (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe') install -r $taskApk
    if($LASTEXITCODE -ne 0) { throw 'ADB install failed. Check the phone authorization dialog.' }
    & (Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe') shell am start -W -n cn.edu.uestc.wifiassistant/.MainActivity
}
