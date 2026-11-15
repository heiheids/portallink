# INDEX — 文件索引

> 由 `tools\file-index.ps1` 生成，**勿手改**。用途列来自 `tools\file-index-notes.txt`。
> 工程根目录：`E:\Project\portallink`
> 生成时间：2026-11-15 14:45:59　文件数：72　总行数：9419　总大小：550.9 KB

| 行数 | 读取级别 | 允许手段 |
| --- | --- | --- |
| ≤ 80 | L2 窗口 | `read` 带 offset/limit |
| 81–300 | L3 整读 | 改签名前一次整读 |
| > 300 | 违规 | 先拆分，不得整读 |

## 源码（Java）

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `source\portallink\src\main\java\com\heishan\portallink\client\ClientPortalLinkState.java` | 183 | 6.3 | 快照缓存与扫描调度：移动阈值、维度变更、服务端线程投递 | ClientPortalLinkState, snapshot, markDirty, requestScan, tick |
| `source\portallink\src\main\java\com\heishan\portallink\client\command\PortalLinkCommand.java` | 105 | 4.7 | /portallink 系列客户端命令：列出、重扫、开关、开设置 | PortalLinkCommand, register |
| `source\portallink\src\main\java\com\heishan\portallink\client\ConflictNotifier.java` | 108 | 4 | 检测到不对称链接时的一次性聊天警告 | ConflictNotifier, tick, reset, warningCount, lastWarnedKey |
| `source\portallink\src\main\java\com\heishan\portallink\client\gui\FloatSlider.java` | 50 | 1.5 | 浮点区间滑块（保留一位小数） | FloatSlider, floatValue, updateMessage, applyValue |
| `source\portallink\src\main\java\com\heishan\portallink\client\gui\IntSlider.java` | 52 | 1.6 | 整数区间滑块 | IntSlider, intValue, updateMessage, applyValue |
| `source\portallink\src\main\java\com\heishan\portallink\client\gui\PortalLinkConfigScreen.java` | 200 | 8.5 | 设置界面：语言、渲染开关、距离与缩放 | PortalLinkConfigScreen, init, extractRenderState, onClose, isPauseS... |
| `source\portallink\src\main\java\com\heishan\portallink\client\PortalIdStore.java` | 138 | 4.7 | 传送门持久编号：config/portallink-ids.json，按 PortalRef.key() 分配递增且永不复用 | PortalIdStore, get, idOf, idOf, idOf, count |
| `source\portallink\src\main\java\com\heishan\portallink\client\PortalLinkClient.java` | 125 | 4.6 | 客户端入口，接线按键、tick、世界渲染、HUD、命令 | PortalLinkClient, onInitializeClient, toggleKey, configKey |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\LinkColorAssigner.java` | 136 | 5.1 | 按链接组分配颜色（黄金比色相，配对同色） | LinkColorAssigner, colorOf, colorOf, colorOf |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalGhostRenderer.java` | 71 | 3.1 | 绘制对端门幽灵与配对连线 | PortalGhostRenderer |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalGizmoSupport.java` | 100 | 4.3 | 再导出 gizmo 时统一施加穿透与存活时长 | PortalGizmoSupport |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalLabelRenderer.java` | 65 | 3 | 绘制门上方两行标签（状态 + 去向） | PortalLabelRenderer |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalLinkHud.java` | 96 | 4.1 | 左上角状态列表（可选） | PortalLinkHud, extractRenderState |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalLinkRenderer.java` | 169 | 7.5 | 世界内绘制入口，只做编排（范围/门框/幽灵/标签各有专职类） | PortalLinkRenderer, render |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalPalette.java` | 38 | 1.3 | 固定语义色（冲突/未知）与透明度工具，链接色由 LinkColorAssigner 生成 | PortalPalette, forDimension, withAlpha |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\PortalRangeRenderer.java` | 78 | 3.1 | 绘制柱体棱边/横环/分段/标签（gizmo） | PortalRangeRenderer |
| `source\portallink\src\main\java\com\heishan\portallink\client\render\RangeMeshRenderer.java` | 172 | 8.1 | 认领范围的全部几何：提交式网格（相机相对坐标），无 gizmo 保留副本，故不闪不跟随 | RangeMeshRenderer |
| `source\portallink\src\main\java\com\heishan\portallink\client\scan\ClientPortalScanner.java` | 85 | 3.2 | 客户端已加载区块扫描，不触发加载、不发包 | ClientPortalScanner, scan |
| `source\portallink\src\main\java\com\heishan\portallink\client\scan\PortalScanService.java` | 273 | 13 | 组装快照：服务端 POI（权威）或客户端区块（兜底） | PortalScanService, build, buildOnClientThread |
| `source\portallink\src\main\java\com\heishan\portallink\client\scan\ServerPortalScanner.java` | 75 | 2.7 | 服务端 POI 权威扫描（仅服务端线程） | ServerPortalScanner, scan |
| `source\portallink\src\main\java\com\heishan\portallink\config\PortalLinkConfig.java` | 180 | 6.6 | config/portallink.json 读写与字段夹取 | PortalLinkConfig, get, save, replace |
| `source\portallink\src\main\java\com\heishan\portallink\core\DimensionPortals.java` | 69 | 2 | 单维度传送门集合 + 完整性标记 | DimensionPortals, empty, dimension, portals, complete, count, withC... |
| `source\portallink\src\main\java\com\heishan\portallink\core\LinkageMath.java` | 168 | 7.7 | 坐标换算、搜索半径、方形判定、原版排序规则 | LinkageMath, contactMargin, coordinateScale, defaultCoordinateScale... |
| `source\portallink\src\main\java\com\heishan\portallink\core\LinkageResolver.java` | 104 | 4.5 | 纯函数实现原版最近门匹配，并判定双向对称性（串门） | LinkageResolver, resolve, resolveAll, applyConflicts |
| `source\portallink\src\main\java\com\heishan\portallink\core\LinkGroups.java` | 112 | 4.5 | 把门按「互指配对」分组，决定配色；单向链接不夺走真配对的身份色 | LinkGroups, empty, of, groupOf, linkedTogether, pairKey |
| `source\portallink\src\main\java\com\heishan\portallink\core\LinkStatus.java` | 38 | 1 | 链接状态枚举：已链接/会串门/将新建/未知 | LinkStatus, id, byId, isProblem |
| `source\portallink\src\main\java\com\heishan\portallink\core\PortalEntry.java` | 69 | 2.6 | 一个门的链接结果：锚点、命中方块、状态、水平距离 | PortalEntry, dimension, targetDimension, hasMatch, key, matchedDist... |
| `source\portallink\src\main\java\com\heishan\portallink\core\PortalFrameBuilder.java` | 140 | 5.3 | 把逐方块的 POI 记录用连通分量还原成矩形门框 | PortalFrameBuilder, build |
| `source\portallink\src\main\java\com\heishan\portallink\core\PortalRef.java` | 92 | 3.3 | 单个传送门的几何：轴向、最小角、宽高、包围盒、顶部锚点 | PortalRef, maxCorner, bounds, center, centerPos, topCenter, isNethe... |
| `source\portallink\src\main\java\com\heishan\portallink\core\PortalSnapshot.java` | 74 | 2.9 | 一次扫描的完整只读快照（含双向条目），渲染与命令的唯一数据源 | PortalSnapshot, empty, isEmpty, otherDim, allEntries, hasProblems |
| `source\portallink\src\main\java\com\heishan\portallink\core\RangeMarker.java` | 110 | 4.1 | 一个认领范围的几何：中心、半边长、归属门、所属链接组 | RangeMarker, minBlockX, maxBlockX, minBlockZ, maxBlockZ, blockWidth... |
| `source\portallink\src\main\java\com\heishan\portallink\core\RangePlanner.java` | 226 | 11.1 | 规划本维度要画的认领范围（以对端门为中心） | RangePlanner, plan |
| `source\portallink\src\main\java\com\heishan\portallink\core\ScannedPortal.java` | 26 | 0.7 | 传送门 + 其全部方块坐标（原版按方块而非中心匹配） | ScannedPortal, key, contains |
| `source\portallink\src\main\java\com\heishan\portallink\i18n\PortalI18n.java` | 114 | 3.9 | 文案查找：跟随游戏语言或强制内置语言，带缓存失效 | PortalI18n, tr, tr, invalidate, tick |
| `source\portallink\src\main\java\com\heishan\portallink\PortalLink.java` | 20 | 0.6 | 全局常量：MOD_ID、作者、主页、日志器 | PortalLink |
| `source\portallink\src\main\java\com\heishan\portallink\PortalLinkInit.java` | 11 | 0.3 | 服务端入口，仅记录初始化日志 | PortalLinkInit, onInitialize |

## 游戏测试（不进发布 jar）

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalFixtures.java` | 133 | 5.5 | 测试夹具：建门、夹取高度、重置配置、放置相机、断言辅助 | PortalFixtures, toNether, clampY, portalNearSpawn, buildPortal, res... |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalGroupAssertions.java` | 266 | 13.8 | 纯断言：互指配对同组、单向不夺色、柱体分带合法且稳定 | PortalGroupAssertions |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalLinkClientGameTest.java` | 134 | 7.3 | 端到端客户端游戏测试：真实建双门、校验链接、截图 | PortalLinkClientGameTest, runTest |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalLinkConflictGameTest.java` | 163 | 8.4 | 串门场景测试：两主世界门抢同一下界门，断言 LINKED/CONFLICT 与警告触发 | PortalLinkConflictGameTest, runTest |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalLinkHeightGameTest.java` | 161 | 8.2 | 同 X/Z 不同高度双下界门：断言柱体分带不重叠、分界=两门 Y 中点、两段异色 | PortalLinkHeightGameTest, runTest |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalLinkMultiplayerGameTest.java` | 89 | 4.5 | 专用服务器测试：客户端扫描兜底、不谎报权威/链接/冲突 | PortalLinkMultiplayerGameTest, runTest |
| `source\portallink\src\gametest\java\com\heishan\portallink\gametest\PortalLinkUiGameTest.java` | 166 | 7.9 | UI 测试：设置界面边界、HUD 存活、命令触发扫描与开关 | PortalLinkUiGameTest, runTest |
| `source\portallink\src\gametest\resources\fabric.mod.json` | 19 | 0.6 | 游戏测试附加模组声明（fabric-client-gametest 入口） |  |

## 资源

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `source\portallink\src\main\resources\assets\portallink\icon.png` | - | 1.2 | 模组图标（128x128） |  |
| `source\portallink\src\main\resources\assets\portallink\lang\en_us.json` | 66 | 3.2 | 英文文案 |  |
| `source\portallink\src\main\resources\assets\portallink\lang\zh_cn.json` | 66 | 3.3 | 中文文案 |  |
| `source\portallink\src\main\resources\fabric.mod.json` | 31 | 0.8 | 模组元数据：入口、依赖、作者、图标 |  |

## 文档

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `docs\文件读取与索引规范.md` | 315 | 13.9 | 读取策略、索引结构、目录卫生 |  |
| `docs\运行失败复盘.md` | 106 | 7.1 | 已发生失败的分类、根因与改进规则 |  |
| `docs\mod_context.md` | 758 | 37.6 | 工程上下文：环境事实、已验证 API、验证纪律 |  |

## 构建配置

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `source\portallink\build.gradle` | 58 | 1.3 | 构建脚本：Loom 配置、依赖、客户端游戏测试开关 |  |
| `source\portallink\gradle.properties` | 15 | 0.4 | Minecraft/加载器/API 版本参数（ASCII） |  |
| `source\portallink\gradle\wrapper\gradle-wrapper.properties` | 11 | 0.4 | Gradle Wrapper 版本与下载地址（已指向腾讯镜像） |  |
| `source\portallink\settings.gradle` | 12 | 0.2 | 仓库与工程名 |  |

## 工具脚本

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `tools\backup.ps1` | 172 | 6.2 | 源码 zip 备份 + 操作日志追加（每次改文件前先跑） |  |
| `tools\build.ps1` | 131 | 4.8 | 构建入口：自动定位 JDK25 与工程内 Gradle，提供 compile/gametest/build 三档 |  |
| `tools\deploy.ps1` | 130 | 5.3 | 把最新 jar 部署到实例 mods\，先清旧版本再复制并校验 SHA256（每次改完都要跑） |  |
| `tools\file-index-notes.txt` | 77 | 8.7 | 文件用途标注数据源（人工维护） |  |
| `tools\file-index.ps1` | 245 | 9.7 | 生成 docs\INDEX.md 与执行 -Check 门禁 |  |

## 数据表

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `下界门映射工程备份.txt` | 608 | 45.2 | 备份与操作记录（脚本追加） |  |

## 其它

| 路径 | 行数 | KB | 用途 | 关键符号 |
| --- | --- | --- | --- | --- |
| `.gitignore` | 27 | 0.6 | （未标注） |  |
| `LICENSE` | 27 | 1.2 | MIT 许可证 |  |
| `mod\portallink-1.0.0.jar` | - | 73.1 | （未标注） |  |
| `PACKAGE.md` | 110 | 4.7 | 发布包说明与校验 |  |
| `README.md` | 168 | 6.9 | 对外说明：功能、操作、原理、安装 |  |
| `source\portallink\gradle\wrapper\gradle-wrapper.jar` | - | 46.4 | Gradle Wrapper 引导 jar（二进制，禁止手工读取） |  |
| `source\portallink\gradlew` | 248 | 8.5 | Gradle 启动脚本（Unix） |  |
| `source\portallink\gradlew.bat` | 82 | 2.8 | Gradle 启动脚本（Windows） |  |
| `source\portallink\LICENSE` | 27 | 1.2 | （未标注） |  |
| `source\portallink\mod_context.md` | 758 | 37.6 | （未标注） |  |
| `source\portallink\README.md` | 168 | 6.9 | （未标注） |  |

## 违规项

无。

---

## 已排除的目录

`backup`、`tmp`、`logs`、`build`、`.gradle`、`.gradle-home`、`run`、`toolchain`、`libs`、`skills_extracted`、`.git`、`natives-windows`、`临时文件`、`screenshots`、`clientGameTest`、`processedMods`、`.fabric`、`saves`、`screenshots`

这些目录只由脚本处理（备份、构建、抓取），任何情况下都不应手工 `read`。

