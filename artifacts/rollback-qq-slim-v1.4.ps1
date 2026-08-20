param([string]$Serial="54084396")
$ErrorActionPreference="Stop"
$adb="F:\Android\Sdk\platform-tools\adb.exe"
$apk="F:\Project\07_Androd\07_QQ_MOD\artifacts\qq-slim-standalone-v1.3.apk"
& $adb -s $Serial install -r $apk
if($LASTEXITCODE){throw "rollback install failed"}
& $adb -s $Serial shell cmd package install-existing --user 999 local.qqslim.module
if($LASTEXITCODE){throw "rollback user-999 install failed"}
& $adb -s $Serial shell am force-stop --user 0 com.tencent.mobileqq
& $adb -s $Serial shell am force-stop --user 999 com.tencent.mobileqq
Write-Output "rollback=OK; restored=1.3-media-path-fix; users=0,999"
