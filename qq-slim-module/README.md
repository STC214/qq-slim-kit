# QQ瘦身独立模块

- 独立包名：`local.qqslim.module`
- Xposed 入口：`local.qqslim.module.SlimEntry`
- 默认作用域：`com.tencent.mobileqq`
- 功能入口：QQ 设置列表顶部的“QQ瘦身”
- 功能页面：69 项原模块目录规则、异步容量扫描、按容量排序、逐项确认清理
- 双开隔离：每个 QQ 进程使用自己的 UID、`ApplicationInfo.dataDir` 和用户外部存储根目录；日志输出 user、UID、内部/外部路径。

独立模块不会加载或调度原模块 APK。桌面入口仅提供状态和导航，实际功能界面在 QQ 内打开。
