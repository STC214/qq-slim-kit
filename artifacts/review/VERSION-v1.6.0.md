# QQ Slim Kit v1.6.0 版本记录

日期：2026-10-17（Asia/Shanghai）。

- 原内部构建：versionName `1.6-reviewed` / versionCode `7`。
- 正式版本：versionName `1.6.0` / versionCode `8`，标签 `v1.6.0`。
- 版本来源：`qq-slim-module/AndroidManifest.xml`；桌面页面、默认 APK 文件名与包验证脚本均读取该版本。
- 更新日志：根目录 `CHANGELOG.md`；公开版本采用三段式编号，Android versionCode 每次发布递增。

## 实际验证

构建命令：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1`，exit 0，编译警告作为错误处理。

包验证命令：`python scripts/verify-apk.py`，exit 0，确认包名、versionName/versionCode、API 范围、签名、对齐、图标像素、Xposed 入口和作用域。

旧、新 APK 使用相同证书；`classes.dex` 逐字节一致。本次未修改清理逻辑，未重复运行模拟器/QQ 注入测试。此前的模拟器检查保留在 [Git 历史审查记录](https://github.com/STC214/qq-slim-kit/blob/0360e7007f24315c813961674f3377cdc11362ed/artifacts/review/REVIEW-v1.6.md)，不等同于真实 QQ 适配证明；旧版本本地交付文件已清理。

源码版本补丁已实际应用于隔离副本；隔离回滚输出：`version_rollback=OK; versionName=1.6-reviewed; versionCode=7; git_and_devices=untouched`。原始 argv、stdout、stderr、退出码保存在同目录的 `version-1.6.0-build.json`、`version-1.6.0-validation.json`、`package-verification-v1.6.0.json`，本地保留、Git 忽略。

## 交付

- APK：`artifacts/qq-slim-kit-v1.6.0.apk`，771537 字节。
- SHA-256：`543bbd784a1cb79f7df89b4aeaffe13bd3b6b5fcf59526692791b1f6064a4fc1`。
- 补丁：`artifacts/qq-slim-kit-v1.6.0-version.patch`。
- 本地文件回滚：`python artifacts/rollback-version-v1.6.0.py`，依赖 `.local/version-1.6.0-baseline/`，不修改 Git 提交、标签或设备。目标文件与验证时不一致则停止回滚。
