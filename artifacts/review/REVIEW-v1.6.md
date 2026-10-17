# QQ 瘦身模块 v1.6 审查与交付记录

日期：2026-10-17（Asia/Shanghai）

## 范围与结论

逐轮检查了全部生产 Java 源码、69 项目录规则、Manifest、Xposed 入口及作用域、图标资源、构建/测试脚本、文档和 Git 忽略配置。已修复本轮发现的问题；最终已执行的检查未再发现新的失败项。这不是对所有 Android、QQ、LSPosed 版本的无缺陷保证。

保留用户替换后的 `icon.png`，未重新生成或修改画面。APK 中的 10 个 PNG 图标资源与项目资源逐像素一致，自适应图标引用有效。

## 本轮主要修复

1. `FileOps.open/size/clear`：只解析可信根目录的 Android 路径别名；子路径使用 `SecureDirectoryStream` 和 `NOFOLLOW_LINKS`，避免旧版误拒绝合法根别名及检查后再按完整路径操作的竞态。目录根、越界路径、链接、特殊文件、非目录选择和遍历超限均停止操作。
2. `SlimCatalog.delete`：在执行层同时检查规则白名单、只读规则、模板与实际路径匹配。MMKV、动态库、钱包、收藏、语音和部分用户媒体目录仅查看。重复路径合并，账号语音目录展开。
3. `SlimCatalog.externalRoot/resolve`：补外部 files 目录回退；账户目录读取异常不再静默按未发现账户处理。
4. `SlimUi`：工作线程只生成独立结果，主线程发布和排序；增加任务队列上限、重复页面限制、ContextWrapper 解包、Activity 销毁释放、任务取消和监听器注销。清理须确认，并明确部分删除风险。
5. `SettingsInjector`：对不可变列表创建副本；去重按弱引用身份并检查入口 ID，复制列表也不重复插入；仅替换声明返回 `List` 的方法，避免向具体列表子类型返回错误类型；无匹配 provider 时标记入口未启用。
6. `MainActivity/WindowStyle`：版本号从包信息读取；滚动布局、暗色配色、API 28+ 系统栏及 cutout 留白、启动异常处理。
7. `scripts/build.ps1`：移除机器固定 SDK 路径，官方 API 82 依赖校验及按需下载，javac 警告作为错误处理；签名、对齐、badging 验证成功后才替换输出 APK。开发密钥保持不变。

安全目录流的设计依据为 [Android API 文档](https://developer.android.com/reference/java/nio/file/SecureDirectoryStream) 和 [Android 的 UnixFileSystemProvider 实现](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/sun/nio/fs/UnixFileSystemProvider.java)。仅对缺失路径返回 0；权限或遍历异常显示失败，符合 [Files API 对存在性与读取错误的区别](https://developer.android.com/reference/java/nio/file/Files)。

## 实际执行与结果

测试对象：本次单独启动的 `emulator-5580`，Android 16 / API 36.1，AVD 以只读、无窗口、无快照模式启动。所有删除测试均使用随机测试目录或独立的 `local.qqslim.test` 包，不接触 QQ 真实数据。

| 检查 | 实际结果 |
|---|---|
| v1.5 基线，同一 32 项回归 | `RESULT passed=22 failed=10`，Android 测试进程 exit 137；基线复现驱动按预期返回 0 |
| v1.6 回归 | `RESULT passed=32 failed=0`，测试进程与驱动 exit 0 |
| 亮色 UI instrumentation | `RESULT ui_passed=14 failed=0`，`INSTRUMENTATION_CODE: -1`，驱动 exit 0 |
| 暗色 UI instrumentation | `RESULT ui_passed=14 failed=0`，`INSTRUMENTATION_CODE: -1`，驱动 exit 0 |
| 最终构建 | exit 0；`-Xlint:all,-options -Werror`，编译警告 0 |
| APK 签名 | `Verifies`；v3=true；证书与本次 v1.5 相同 |
| APK 对齐 | `Verification succesful`，exit 0 |
| APK 内容 | 33 个类均属于模块自身；无测试类、无打包的 Xposed API 实现；入口、作用域、包名和版本通过检查 |
| 安装与桌面启动 | 模拟器安装成功；亮/暗桌面页均含 `v1.6-reviewed`，crash 缓冲中没有模块崩溃 |
| 错误依赖、缺失源码 | 均 exit 1，已发布 APK 字节保持不变 |
| 从缺失依赖启动构建 | 官方下载、哈希检查、构建成功；DEX 与交付 APK 一致 |
| 补丁 | `git apply --check --whitespace=error` 与实际应用均 exit 0；应用后的内容哈希匹配 |
| 回滚 | 隔离副本输出 `rollback=OK; restored_files=28; baseline=v1.5; device_and_git_index=untouched`；原始字节哈希匹配 |
| 回滚后重复基线测试 | 再次得到 `RESULT passed=22 failed=10`，与原始基线相同 |
| Git 忽略 | 8/8 应忽略路径命中，12/12 应保留源码/资源/报告未被忽略 |
| 既有 APK | 4 个已从 Git 索引取消跟踪，磁盘文件均保留且与原提交字节相同 |
| Git 空白检查 | 工作树及已暂存修改均 exit 0 |

32 项回归包含根目录/越界/前缀碰撞保护、模板与路径匹配、持久数据保护、可信路径别名、链接与坏链接、权限异常、取消、深度上限、账号展开、设置列表副本去重、代理对象方法和 100 次链接替换竞态。28 项 UI 检查包含扫描发布、只读显示、清理确认、保留目录、两像素间距、主题、系统栏留白、页面去重及 Activity 销毁清理。

### 复现命令

```powershell
# baseline
python scripts/test.py --serial emulator-5580 --source .local/review-v16-baseline/qq-slim-module --label baseline-regression --expect-fail
# modified
python scripts/test.py --serial emulator-5580 --label final-regression
python scripts/test-ui.py --serial emulator-5580
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
python scripts/verify-apk.py --serial emulator-5580
# source/artifact rollback; stops before editing if files changed after this review
python artifacts/rollback-qq-slim-v1.6.py
```

原始验证记录（本地保留，Git 忽略）：`baseline-regression.json`、`final-regression.json`、`ui-regression.json`、`final-build.json`、`package-verification.json`、`build-negative-and-bootstrap.json`、`patch-rollback-verification.json`、`rollback-baseline.json`、`git-and-baseline-verification.json`，均位于本报告目录，含实际 argv、输入路径、stdout、stderr 和退出码。

## 交付物

- APK：`artifacts/qq-slim-standalone-v1.6.apk`，771537 字节。
- APK SHA-256：`fb97db4e0b9b3f0c5f0f0d04a6b0b60d128d12a2be61be329d3639be51499762`。
- 源码补丁：`artifacts/qq-slim-v1.6-source.patch`，针对本轮开始时的 v1.5 工作树快照。
- 验证记录：本报告及同目录原始 JSON。
- 回滚：`artifacts/rollback-qq-slim-v1.6.py`；依赖 `.local/rollback-v1.6.json` 和 `.local/review-v16-baseline/`，请一并保留。回滚不修改 Git 索引、不安装或卸载设备应用，也不恢复已经清理的缓存。回滚默认恢复本轮修改的源码/脚本/配置并移除新 v1.6 APK，原 v1.5 APK保持保留。

证书 SHA-256：`e4957288c9c19ee83a4b6eb25a57072b0857c48f62f3bb2b695abacb468af51e`。本包可与本次 v1.5 同签名升级；早期 v1.4 使用另一证书。

## Git 状态与待实机验收

`.gitignore`、`.gitattributes` 已配置。原先入库的 4 个 APK 仅执行 `git rm --cached`，磁盘文件未删。该取消跟踪操作已暂存；其他源码变更保留在工作树，未自动提交、未修改 Git 身份或远端。

尚未验证：特定 QQ 发行版实际混淆类、回调 setter 语义及 QAuxiliary 共存位置、真实 LSPosed 注入、主实例/双开用户隔离、Android 9 等旧版本的真实设备表现。测试 host 类只是结构夹具，不是已确认的 QQ 兼容性证据。QQ 在清理时持续写文件、权限变化及主动取消仍可能带来部分完成；不提供缓存事务恢复保证。
