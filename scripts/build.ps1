param(
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Source = "qq-slim-module",
    [string]$Output = "artifacts/qq-slim-standalone-v1.6.apk",
    [string]$XposedApi = ".local/xposed-api-82.jar",
    [string]$Keystore = ".local/debug.keystore"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
    if (!$Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $bt = Join-Path $Sdk "build-tools/35.0.0"
    $android = Join-Path $Sdk "platforms/android-35/android.jar"
    if (!(Test-Path -LiteralPath $XposedApi)) {
        New-Item -ItemType Directory -Force (Split-Path $XposedApi -Parent) | Out-Null
        Invoke-WebRequest -UseBasicParsing -Uri 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar' -OutFile $XposedApi
    }
    foreach ($file in @($android, $XposedApi, "$Source/AndroidManifest.xml", "$bt/aapt.exe", "$bt/d8.bat", "$bt/apksigner.bat", "$bt/zipalign.exe")) {
        if (!(Test-Path -LiteralPath $file)) { throw "Missing build input: $file" }
    }
    if ((Get-FileHash $XposedApi -Algorithm SHA256).Hash -ne "F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25") {
        throw "Xposed API checksum mismatch"
    }
    $work = Join-Path $root (".local/build/" + [guid]::NewGuid().ToString("N"))
    $outputDirectory=Split-Path $Output -Parent
    if (!$outputDirectory) { $outputDirectory='.' }
    New-Item -ItemType Directory -Force "$work/classes","$work/dex",$outputDirectory | Out-Null
    $sources = @(Get-ChildItem "$Source/src" -Recurse -Filter *.java | ForEach-Object FullName)
    & javac '-J-Duser.language=en' '-J-Duser.country=US' -encoding UTF-8 --release 8 '-Xlint:all,-options' -Werror -cp "$android;$XposedApi" -d "$work/classes" @sources
    if ($LASTEXITCODE) { throw "javac exit=$LASTEXITCODE" }
    & jar cf "$work/classes.jar" -C "$work/classes" .
    if ($LASTEXITCODE) { throw "jar exit=$LASTEXITCODE" }
    & "$bt/d8.bat" --min-api 28 --lib $android --classpath $XposedApi --output "$work/dex" "$work/classes.jar"
    if ($LASTEXITCODE) { throw "d8 exit=$LASTEXITCODE" }
    $resourceArgs = @()
    if (Test-Path "$Source/res") { $resourceArgs = @('-S', "$Source/res") }
    & "$bt/aapt.exe" package -f -M "$Source/AndroidManifest.xml" -I $android -A "$Source/assets" @resourceArgs -F "$work/unsigned.apk"
    if ($LASTEXITCODE) { throw "aapt exit=$LASTEXITCODE" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open("$work/unsigned.apk", 'Update')
    try {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$work/dex/classes.dex","classes.dex") | Out-Null
        Get-ChildItem "$Source/META-INF" -Recurse -File | ForEach-Object {
            $name = "META-INF/" + $_.FullName.Substring((Resolve-Path "$Source/META-INF").Path.Length + 1).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$_.FullName,$name) | Out-Null
        }
    } finally { $zip.Dispose() }
    & "$bt/zipalign.exe" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
    if ($LASTEXITCODE) { throw "zipalign exit=$LASTEXITCODE" }
    if (!(Test-Path $Keystore)) {
        New-Item -ItemType Directory -Force (Split-Path $Keystore -Parent) | Out-Null
        & keytool -genkeypair -keystore $Keystore -storepass android -keypass android -alias androiddebugkey -dname "CN=QQ Slim Development" -keyalg RSA -keysize 3072 -validity 10000
        if ($LASTEXITCODE) { throw "keytool exit=$LASTEXITCODE" }
    }
    & "$bt/apksigner.bat" sign --v4-signing-enabled false --ks $Keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$work/signed.apk" "$work/aligned.apk"
    if ($LASTEXITCODE) { throw "sign exit=$LASTEXITCODE" }
    & "$bt/apksigner.bat" verify --verbose --print-certs "$work/signed.apk"
    if ($LASTEXITCODE) { throw "verify exit=$LASTEXITCODE" }
    & "$bt/zipalign.exe" -c -v 4 "$work/signed.apk"
    if ($LASTEXITCODE) { throw "alignment exit=$LASTEXITCODE" }
    & "$bt/aapt.exe" dump badging "$work/signed.apk"
    if ($LASTEXITCODE) { throw "badging exit=$LASTEXITCODE" }
    if ([IO.Path]::IsPathRooted($Output)) { $destination=[IO.Path]::GetFullPath($Output) }
    else { $destination=[IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Output)) }
    Copy-Item -LiteralPath "$work/signed.apk" -Destination "$destination.new" -Force
    if (Test-Path -LiteralPath $destination) { [IO.File]::Replace("$destination.new", $destination, $null) }
    else { [IO.File]::Move("$destination.new", $destination) }
    Get-FileHash -Algorithm SHA256 $destination | Format-List
} finally { Pop-Location }
