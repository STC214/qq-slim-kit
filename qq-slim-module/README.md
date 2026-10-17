# QQ瘦身独立模块

- 独立包名：`local.qqslim.module`
- Xposed 入口：`local.qqslim.module.SlimEntry`
- 默认作用域：`com.tencent.mobileqq`
- 功能入口：QQ 设置列表顶部的“QQ瘦身”
- 当前版本：1.6-reviewed（versionCode 7），保留替换后的多密度与自适应图标。
- 功能页面：69 项规则、重复路径合并、异步容量扫描、容量排序、逐项确认清理，重要数据仅查看。
- 双开隔离：每个 QQ 进程使用自己的 UID、`ApplicationInfo.dataDir` 和用户外部存储根目录。此设计仍需目标实机验收。
- 清理保留所选目录、检查底层规则，并使用相对目录句柄与 NOFOLLOW 遍历；清理中断可能留下部分已删除数据。
- 页面销毁会取消任务、清除待处理回调并注销生命周期监听器。

独立模块不会加载或调度原模块 APK。桌面入口仅提供状态和导航，实际功能界面在 QQ 内打开。
