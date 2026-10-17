# QQ瘦身独立模块

**QQ Slim Kit · v1.6.0** · [更新日志](CHANGELOG.md)

一个面向 Android QQ 的独立 Xposed 缓存清理模块。

## 功能

- 在 **QQ → 设置** 中提供“QQ瘦身”入口。
- 扫描 QQ 内部缓存与外部媒体缓存。
- 显示各缓存目录占用空间，并按容量从大到小排列。
- 支持逐项确认清理，避免误触后直接删除。
- 自动适配系统亮色、暗色主题。
- 支持主 QQ 与双开/XSpace QQ，按 Android 用户、UID 和数据目录隔离扫描。

## 使用方法

1. 安装模块 APK。
2. 在 LSPosed 中启用模块。
3. 将主 QQ 以及需要使用的双开 QQ 加入模块作用域。
4. 强制停止并重新启动对应 QQ 实例。
5. 打开 **QQ → 设置 → QQ瘦身**。
6. 等待容量计算完成后，按需点击对应项目的“清理”。

## 清理范围

模块沿用 69 项目录规则，合并重复路径；多帐号语音目录会展开为多行。重要数据目录仅显示容量，不开放清理。主要覆盖：

- 聊天图片、照片与缩略图
- 图片磁盘缓存
- 短视频及视频编辑缓存
- 语音消息缓存
- 小程序、网页与 X5 WebView 缓存
- QQ 秀、气泡、挂件、字体与资料卡素材
- QQ 空间、小世界、钱包及地图缓存
- 日志、临时文件和接收文件缩略图

外部缓存根目录会按当前实例自动解析，例如：

```text
/storage/emulated/0/Android/data/com.tencent.mobileqq
/storage/emulated/999/Android/data/com.tencent.mobileqq
```

因此主实例和双开实例的缓存不会混在一起。

## 注意事项

- “清理”会删除所选目录内的文件和子目录，保留所选目录本身，请先确认条目名称。
- MMKV、动态库、收藏、语音、相册、钱包等重要目录为“仅查看”；底层清理入口也会校验规则和目标路径。
- 清理后的本地图片或视频副本可能丢失；仅服务器仍保留的内容才有机会重新下载。
- 清理不是事务操作：QQ 可能继续写入缓存；取消、权限变化或文件变化可能使清理只完成一部分。模块不备份已清理文件。
- 遇到链接、特殊文件、权限异常或超出遍历限制时停止相应操作。设备若缺少安全目录流支持，相关项目显示扫描失败，清理停止。
- QQ 更新后可能调整缓存目录；若某一项目长期显示为 0，需要重新核对当前版本的实际存储路径。
- 模块仅处理缓存目录，不处理聊天数据库、帐号资料或登录凭据。

## 构建信息

- 包名：`local.qqslim.module`
- 当前版本：`1.6.0`（versionCode 8，Git 标签 `v1.6.0`）
- 最低 Android 版本：Android 9（API 28）
- 默认作用域：`com.tencent.mobileqq`

## 构建与验证

需要 Windows PowerShell、JDK 17/21、Android SDK Platform 35 / Build Tools 35.0.0，以及用于测试的 Python 3。

```powershell
$env:ANDROID_HOME = '你的 Android SDK 路径'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
# 以下测试仅对显式选定的模拟器运行，测试包与测试目录均独立于 QQ：
python scripts/test.py --serial emulator-5580
python scripts/test-ui.py --serial emulator-5580
```

- 默认 APK：`artifacts/qq-slim-kit-v1.6.0.apk`，文件名自动读取 Manifest 中的版本号。
- 首次构建从 Xposed 官方仓库获取 API 82 编译依赖，并核对 SHA-256；依赖仅参与编译，不打入模块 APK。
- 本地开发签名保存在 `.local/debug.keystore`，请自行备份。与本次 v1.5 签名相同，与早期 v1.4 签名不同；签名不同的安装包不能直接覆盖升级。
- 源码、图标资源、测试与脚本纳入版本管理；SDK、密钥、构建缓存、APK 和原始测试日志由 `.gitignore` 排除。忽略规则不删除磁盘文件。
- 当前版本记录：[VERSION-v1.6.0.md](artifacts/review/VERSION-v1.6.0.md)。Android 模拟器测试不代表特定 QQ/LSPosed 版本已适配，实机入口和双开隔离仍应单独验收。
- 本地交付目录只保留当前版本；旧版补丁、验证记录和回滚文件随旧 APK 一并清理，历史内容可从 Git 提交查询。

## 版本约定

- 公开版本使用 `主版本.次版本.修订号`，如 `1.6.0`；Git 标签增加 `v` 前缀。
- 不兼容的大调整增加主版本；兼容的新功能增加次版本；修复增加修订号。
- 每次发布都递增 Android `versionCode`，不因版本命名调整而降低；桌面页面和打包文件名均读取 Manifest。
- `1.6-reviewed` 是先前的内部构建标识，正式版本从 `1.6.0` 延续现有编号，不重置升级序号。

## 致谢

- 感谢 **“模了个块”**：QQ瘦身的缓存目录规则、功能流程和页面设计以该模块为主要参考。
- 感谢 **QAuxiliary**：QQ 设置入口的版本适配与位置处理参考了其实现思路。
- 感谢 **LSPosed** 项目及相关社区，为 Android 模块开发与运行环境提供基础支持。

本项目仅聚焦 QQ 缓存扫描与清理功能。
