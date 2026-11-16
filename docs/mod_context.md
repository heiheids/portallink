# Portal Link — 工程上下文 / Engineering notes

作者：黑山大叔 · <https://space.bilibili.com/19428259> · <https://github.com/heiheids>

本文件记录本工程的硬性环境事实与已验证的 API 结论，供后续维护/二次开发直接参考，避免重复试错。

---

## 1. 目标环境

| 项目 | 值 |
| --- | --- |
| Minecraft | `26.3` |
| 实例 | `E:\@26.3\.minecraft\versions\26.3-Fabric`（模组目录 `mods\`，已装 `portallink-1.0.0.jar`） |
| 加载器 | Fabric Loader `0.20.1`（实例内 `>=0.20.0` 即可） |
| Fabric API | `0.163.0+26.3`（实例内实测已是 `0.166.2+26.3`，兼容） |
| Java | **25**，本机 `C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot`（系统默认 java 是 21，太旧） |
| Gradle | 9.7.1，已解压到工程内 `toolchain\gradle-9.7.1`；wrapper 的 `distributionUrl` 已指向腾讯镜像 |
| Loom | `1.18.4`，插件 id `net.fabricmc.fabric-loom` |
| 命名空间 | `official`（26.x 已去混淆，**没有 mappings 配置行**） |
| 产物任务 | `jar` / `build`（**不是** `remapJar`） |

### 构建命令（推荐：一条命令）

```powershell
cd E:\Project\portallink
.\tools\build.ps1 compile     # 约 5 秒，编译门禁
.\tools\build.ps1 gametest    # 约 1 分钟，真客户端游戏测试
.\tools\build.ps1 build       # 产出 build\libs\portallink-1.0.0.jar
```

`tools\build.ps1` 会自动设置 `JAVA_HOME`（JDK 25）与 `GRADLE_USER_HOME`（工程内 `.gradle-home`），
并优先使用工程内 Gradle，因此不依赖用户目录、也不依赖系统 java。

### 直接用 Gradle（等价命令）

```powershell
cd E:\Project\portallink\source\portallink
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$env:GRADLE_USER_HOME = 'E:\Project\portallink\.gradle-home'
E:\Project\portallink\toolchain\gradle-9.7.1\bin\gradle.bat build --console=plain
```

> **网络事实**：本机 `services.gradle.org` 与 `downloads.gradle.org` 连接超时（已实测），
> 因此 wrapper 的 `distributionUrl` 改为 `https://mirrors.cloud.tencent.com/gradle/`（实测 2.3 秒下完 144 MB）。
> Maven Central / maven.fabricmc.net / libraries.minecraft.net / piston-meta.mojang.com 均可达，无需代理。
> Gradle 9.7.1 + JDK 25 在本机实测无 native-platform 崩溃（完整 `build` 4 分 45 秒通过）。

---

## 2. 26.3 关键差异（已用 javap 逐个验证）

### 2.1 渲染

* 客户端已改为**提取式渲染**：GUI 用 `extractRenderState(GuiGraphicsExtractor, int, int, float)`，不再有 `render(...)`。
* 世界叠加层不再手写 `VertexConsumer`，改用 **gizmo** 系统（`net.minecraft.gizmos.*`）。
* 世界渲染回调：`LevelRenderEvents.BEFORE_GIZMOS`（Fabric，注入点在 `LevelRenderer#finalizeGizmoCollection` 之前）。
* gizmo 坐标系是**世界坐标**，引擎自行减去相机位置，不要自己减。
* `Gizmos.*` 必须在存在 `GizmoCollector` 的线程上下文中调用，`BEFORE_GIZMOS` 满足这一条件。
* gizmo 默认**不持久**（本帧结束即失效），需要 `persistForMillis(int)`；本工程统一用 250 ms。
* `.setAlwaysOnTop()` 可穿透地形显示。
* 主要 API：
  * `Gizmos.cuboid(AABB, GizmoStyle)` / `cuboid(AABB, GizmoStyle, boolean coloredCornerStroke)`
  * `Gizmos.line(Vec3, Vec3, int color)`
  * `Gizmos.rect(Vec3, Vec3, Direction, GizmoStyle)`
  * `Gizmos.circle(Vec3, float, GizmoStyle)`（水平 XZ 圆）
  * `Gizmos.point(Vec3, int color, float size)`
  * `Gizmos.billboardText(String, Vec3, TextGizmo.Style)`
  * `GizmoStyle.stroke(int)` / `fill(int)` / `strokeAndFill(...)`
  * `TextGizmo.Style.forColorAndCentered(int)`、`.withScale(float)`
* HUD：`HudElementRegistry.addLast(Identifier, HudElement)`，`HudElement#extractRenderState(GuiGraphicsExtractor, DeltaTracker)`。
* `GuiGraphicsExtractor` 提供 `fill/text/centeredText/blit/...`；文本绘制用 `graphics.text(font, component, x, y, color, shadow)`。

### 2.2 输入

* 原生 GLFW 键码**不可靠**，改用 `InputConstants.KEY_*`。
* `InputConstants.Type` 的枚举常量是 **`KEYBOARD` / `MOUSE`**（不是 `KEYSYM`）。
* 键位注册：`KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, key, category))`。
* 自定义分类：`KeyMapping.Category.register(Identifier)`。
* 读键用 `KeyMapping#consumeClick()`。

### 2.3 界面

* 打开界面：`Minecraft#setScreenAndShow(Screen)`（旧的 `setScreen` 行为已变）。
* `Screen#init()` 仍是 `protected void init()`；`addRenderableWidget` 也是 protected。
* 按钮：`Button.builder(Component, Button.OnPress).bounds(x, y, w, h).tooltip(Tooltip).build()`。
* 开关：`CycleButton.onOffBuilder(boolean).create(x, y, w, h, Component, OnValueChange)`。
* 滑块：继承 `AbstractSliderButton`，必须实现 `updateMessage()` / `applyValue()`。

### 2.4 类型与工具

* `ResourceLocation` → **`Identifier`**（`Identifier.fromNamespaceAndPath(ns, path)`）。
* `Component.translatable(...)` 不变。
* `Holder#is(ResourceKey)` 可直接判 POI 类型。
* 高度相关：`LevelHeightAccessor#getMinY/getMaxY/getSectionsCount/getSectionYFromSectionIndex`。

### 2.5 传送门链接（核心算法，来自 `PortalForcer` 字节码）

```
target = (from == NETHER) ? OVERWORLD : NETHER
scale  = fromDimType.coordinateScale / toDimType.coordinateScale   // 主世界 1.0，下界 8.0
pos    = worldBorder.clampToBounds(anchor.x * scale, anchor.y, anchor.z * scale)

radius = (target == NETHER) ? 16 : 128
poiManager.ensureLoadedAndValid(level, pos, radius)
poiManager.getInSquare(NETHER_PORTAL, pos, radius, Occupancy.ANY)
    .map(PoiRecord::getPos)
    .filter(border::isWithinBounds)
    .filter(state -> state.hasProperty(HORIZONTAL_AXIS))
    .min(comparingDouble(block -> block.distSqr(pos)).thenComparingInt(Vec3i::getY))
```

要点：

* 搜索是 **X/Z 方形，完全忽略 Y**；
* 候选是**传送门方块**，不是传送门中心；
* `getInSquare` 内部 `ChunkPos.rangeClosed(center, radius/16 + 1)`，所以区块范围是半径向上取整再加 1。

---

## 3. 数据来源策略

| 场景 | 来源 | 精确度 |
| --- | --- | --- |
| 单人 / 局域网主机 | `ServerLevel#getPoiManager()` 服务端 POI 索引 | 与传送判定完全同源 |
| 多人服务器 | 客户端已加载区块扫描（`ClientChunkCache#getChunk(x, z, FULL, false)`） | 可能不完整，并明确提示 |

* 客户端扫描**不触发区块加载**（`load=false`），不发包，不影响服务器。
* 另一维度在多人环境下通常未加载 → 状态标记为 `UNKNOWN`，界面/命令都会说明"可能不完整"。
* 客户端扫描用 `LevelChunkSection#maybeHas(Predicate<BlockState>)` 先剪枝，再逐方块判定。

---

## 4. 代码分层（禁止单文件海量代码）

```
core/        纯逻辑，无渲染依赖，可单测
client/scan/ 数据采集（服务端 POI / 客户端区块）
client/render/ 只负责画
client/gui/  只负责界面
config/      JSON 持久化
i18n/        语言查找
```

约定：

* 一个类一个职责，超过 ~250 行就该拆分；
* 渲染器不做数据采集，采集器不做绘制；
* 逻辑结论（链接是否对称）全部在 `core` 里，渲染层只读结果。

---

## 5. 国际化

* 内置 `assets/portallink/lang/en_us.json` 与 `zh_cn.json`，键完全对齐。
* 运行时两种模式：
  * `auto` —— 走原版 `Language.getInstance().getOrDefault(key)`，跟随游戏语言；
  * `zh_cn` / `en_us` —— 直接读自带 lang 文件（`Minecraft#getResourceManager()`），可自定义语言与游戏语言不同。
* 语言切换即时生效（`PortalI18n#invalidate()` 清缓存）。

---

## 6. 备份纪律

**每次改动源码前**都要备份（硬要求，不是按批次）：

```powershell
"改动说明" | Out-File -Encoding utf8 tmp\next_op.txt
.\tools\backup.ps1 -OpFile .\tmp\next_op.txt -Tag <标签>

# 或者一条命令直接给说明
.\tools\backup.ps1 -OpText "拆分 PortalLinkRenderer 的连线绘制" -Tag render-split
```

产物：

* `backup\<标签>-<时间戳>.zip` —— 源码快照，默认保留最近 30 个；
* `下界门映射工程备份.txt` —— 追加一条编号记录（时间、zip 名、文件数、**即将进行的操作**）。

备份脚本只打包工程内源码与文档，排除 `backup tmp logs build .gradle .gradle-home run toolchain`。
脚本自己推导工程根目录（`tools\` 的上一级），换机器不用改路径。

> 历史教训（`运行失败复盘.md` §4.2）：上一轮开发实际是按 5 次里程碑备份，与"每次改动前备份"不一致。
> 现在脚本已就位，按约定执行即可。

### 6.1 三档验证 + 备份的完整节奏

```powershell
# 1) 改代码前
.\tools\backup.ps1 -OpText "计划：..." -Tag before-xxx

# 2) 改完立刻编译（5 秒，禁止跳过）
.\tools\build.ps1 compile

# 3) 需要运行时证据时
.\tools\build.ps1 gametest

# 4) 构建并发版
.\tools\build.ps1 build
```

### 6.1.1 部署纪律（用户要求，每次改完必须执行）

> **每次修改完成后，都要把新 jar 放进实例 `mods\` 替换旧的。**

```powershell
.\tools\deploy.ps1            # 先构建再部署（推荐）
.\tools\deploy.ps1 -SkipBuild # 刚刚构建过，直接部署
```

脚本做的事（不要手工复制，容易留下双份导致 mod 冲突）：

1. 校验实例路径存在，必要时创建 `mods\`；
2. 默认先跑 `build`，失败则**不部署任何文件**；
3. 检查产物是否比源码旧，旧则警告；
4. 删除 `mods\` 里**所有** `portallink*.jar`（含改名残留及其他版本号）；
5. 复制新 jar，回读 SHA256 校验一致；
6. 打印 `mods 内 portallink jar 数量`，必须为 1。

| 项目 | 值 |
| --- | --- |
| 实例 | `E:\@26.3\.minecraft\versions\26.3-Fabric` |
| 部署目录 | `...\mods\` |
| 当前版本 | **1.2.0**（`portallink-1.2.0.jar`，90,463 字节） |

> **游戏运行时部署会失败，这是设计好的**：Windows 锁住 jar，删除失败时脚本以
> **退出码 2** 结束，`mods\` 保持原样（先删后复制，不会出现半截状态），并打印
> 关游戏后要执行的那一条命令。当前运行中的游戏不受影响。
>
> 版本号在 `gradle.properties` 的 `mod_version`，改版本时同步改 `PortalLink.MOD_VERSION`。
> `build\libs` 只保留最新产物，历史 jar 定期清理，避免误部署旧版。

---

## 6.2 范围的语义（2026-11-14 重构，重要）

### 6.2.1 范围回答的问题

> **在这个竖直柱体里的任意位置新建一扇门，它会被链接到哪一扇门？**

因为原版搜索是 X/Z 方形、**Y 无边界**，范围是**全高度柱体**，不是某个高度的平面。
柱体的中心是对端的那扇门，不是玩家当前看的这扇门。

| 玩家所在维度 | 柱体归属 | 半边长 | 说明 |
| --- | --- | --- | --- |
| 主世界 | **下界**的门 | `16 × 8 = 128` | 原版进下界只搜 16 格，换算回主世界就是 128 |
| 下界 | **主世界**的门 | `128 ÷ 8 = 16` | 原版回主世界搜 128 格，换算到下界就是 16 |

推导：在主世界建门于 `p`，原版会去下界 `p/8` 处以半径 `R` 搜；所以它能链接到某扇下界门 `G`，
当且仅当 `p` 落在 `G × 8` 的 `R × 8` 方框内。代码走 `RangePlanner`，不要另写一份。

### 6.2.2 配色以「链接配对」为单位

`LinkGroups` 只把**互指**的两扇门归为一组（A 指向 G，且 G 也指向 A）。

* 同组 → 同色（黄金比色相自动分配，`LinkColorAssigner`）；
* 单向链接（B 指向 G，但 G 回的是 A）→ B 自成一组，**不夺取** G 的身份色；
  否则真配对 (A,G) 会被画成两种颜色，看起来反而像冲突；
* 冲突状态仍强制用红色（`PortalPalette.CONFLICT`）——那是警告，不是身份；
* 分组 id 用两门 key 排序拼接，跨重扫/重开局稳定，同一配对永远同色。

### 6.2.3 排序是三维距离（javap 复核，务必不要搞反）

`PortalForcer#findClosestPortalPosition` 的两件事必须分开记：

| 环节 | 事实 |
| --- | --- |
| 搜索窗口 | `PoiManager#getInSquare(radius)`，X/Z 方形，**Y 完全无边界** |
| 排序 | `Comparator.comparingDouble(distSqr).thenComparingInt(Y)` |

lambda 体是 `BlockPos.distSqr(Vec3i)`，**三维**（`dx²+dy²+dz²`），Y 只在完全平局时才比较。

推论：Y **确实**决定谁胜出；但"搜索范围跨全高"和"Y 参与排序"并不矛盾——
**柱体是完整的，柱体内部再按高度分给不同的门**（见 §6.2.5）。

### 6.2.4 范围的绘制方式（1.11.0：提交式网格 + 通高 + 棱边）

**画法（1.11.0：全部走提交式网格，零 gizmo）**

| 元素 | 实现 | 备注 |
| --- | --- | --- |
| 侧壁 | 提交式垂直四边形，通高 | `zoneWallAlpha` 默认 70 |
| 四角棱边 | 每角两片互相垂直的薄四边形（`EDGE=0.08`） | 单四边面只有一个法线，背面会消失，所以垂直两片 |
| 顶 / 底边框 | 水平薄四边形 | 有高参考，且不随玩家高度移动 |
| 标签 | `billboardTextOverBlock`（gizmo） | 仅剩的 gizmo 元素 |

**关键原则：范围几何一律用提交式网格，绝不用 gizmo。**
gizmo 是保留式的（必须设寿命才绘制，寿命又带来多份副本），
所以 gizmo 画半透明面必然脉动（闪），画通高线条必然占满视野（观感上"跟随"）。
提交式网格每帧只画一次、alpha 精确，两个问题同时消失。

**没有任何东西画在玩家高度上。**
曾用"玩家高度分档的地板面"来制造贴地感，结果玩家上下移动时那层一直贴在视线高度，
表现为"视线高度总有一层颜色"。现在所有面都锚在柱体自身范围 `bottomY..topY`。

**闪烁的根因与唯一可行解法**

gizmo 是保留式的：不设寿命**完全不绘制**（实测：去掉后所有线、面、标签一起消失），
设了寿命就有 `N = 寿命 / 帧时间` 份副本同时存活。半透明面叠 N 份后
不透明度是 `1-(1-a)^N`，而 **N 每帧在 3 与 4 之间跳变** → 亮度脉动 = 闪烁。

唯一同时满足「可见」与「不闪」的做法是**按副本数反解单层 alpha**：

```
N = ceil(50ms / 帧时间)
a = 1 - (1 - 目标alpha/255)^(1/N)
```

帧时间在渲染回调里实测，所以 30/60/240 fps 观感一致。

**试过但不可行的路线（不要再试）**

| 方案 | 结果 |
| --- | --- |
| 去掉 `persistForMillis` | 全部消失（含线、面、标签） |
| `submitCustomGeometry` + 世界坐标 | 不可见（内外视角都无墙面） |
| `submitCustomGeometry` + 相机相对坐标 | 可见，但整体偏移 `-camera`，跟着玩家走 |

### 6.2.4.1 高度：通顶

侧壁铺满该柱体的 `bottomY()..topY()`（即该维度可建高度），因为下界门在任何高度都算连通。
`fullHeightZones` 关掉可退回矮墙模式（`zoneUpHeight`/`zoneDownHeight`），仅作备用。

同柱多门时，`RangePlanner` 按门自身 Y 切分成段，渲染只画**包含玩家当前高度的那一段**。
这只是可见性筛选，不会在玩家高度上画出任何面。

### 6.2.4.2 快捷键

| 键 | 功能 |
| --- | --- |
| **P** | 总开关（默认，可在原版按键设置里改） |
| B | 状态列表开关 |
| O | 设置界面 |

**参考实现**：`临时文件/`（另一次 AI 产出的 NeoForge 同类模组，
`client/ZoneRenderer.java` + `client/ClientState.java`），用户确认其范围渲染可用。

**它与本工程此前做法的本质差异（全部照搬）**

| 项目 | 参考实现（可用） | 本工程此前（观感差） |
| --- | --- | --- |
| 形状 | 玩家脚下**水平面**（`baseY = floor(playerY/8)*8+4`） | 257×382 通高巨柱 |
| 侧壁 | 仅 **向上 12 / 向下 4** 格 | 通高 382 格 |
| 填充画法 | `Gizmos.rect(a, b, 质心, 质心, fill)` **扇形三角** | 自定义网格 / 单个四边形 |
| 剔除距离 | **1536** | 256 |
| 填充 alpha | **40**（墙 28，描边 200） | 0x0A~0x70 反复调 |
| 几何缓存 | 按维度 + Y 分档缓存，不每帧重建 | 每帧重算 |

**为什么地板面不会“跟着玩家走”**：它贴在你脚下的地形上，
走动时像地面上的固定区域；而通高巨墙在 100 格外就占满视野，
棱线扫过时读作“箱子在动”。

**踩过的两个坑（都发生在照搬过程中）**

1. **不要按副本数均分 alpha。** 我一度把 40 均分到 4 份副本 → 单层 11，
   等于不可见。参考实现直接使用 40/28/200，照抄即可。
2. **地板不要用单个四边形。** 四边形的可见面取决于绕序，而水平面有一半时间从上方看；
   用「每边一个扇形三角」的写法（`rect(a, b, 质心, 质心, fill)`）俯视仰视都可见。

**新配置项**（`config/portallink.json`）

```
zoneFillAlpha   40    地板填充 alpha
zoneWallAlpha   28    侧壁 alpha
zoneStrokeAlpha 200   描边 alpha
showZoneWalls   true  是否画侧壁
zoneUpHeight    12    侧壁向上高度
zoneDownHeight  4     侧壁向下深度
maxRenderDistance 1536
```

> 旧的 `submitCustomGeometry` 体积方案（`PortalVolumeSubmitter`）已删除：
> 它能把几何画对，但**画大了就是占满视野**，方向本身不对。

### 6.2.4.3 `[probe]` 每秒原值日志（排查渲染类问题先看它）

### 6.2.4.4 「范围跟着玩家走」的排查结论

**已用 `[probe]` 排除数据层**：28 行日志覆盖玩家移动 ~80 格，
`anchor=(-40,-236)` 一个字节没变，`drawn=1 culled=0 submits/s=60`。
真正的成因是**形状**：通高巨墙在近处占满视野、棱线扫过被读作"在动"。
1.8.0 改成地板面后该现象从根上消失。

**因此剩下两种解释，1.7.0 各处理一半：**

1. **gizmo 保留副本拖影。** gizmo 是保留式的，`drainGizmos()` 会把未过期的全部重画。
   寿命 250ms 时同屏约 15 份副本；若副本携带的是**提交当时的相机偏移**，
   玩家移动时整只笼子就会被拖着走。
   → `LINE_LIFETIME_MILLIS` 降到 **50ms**（约 3 份，仍大于一帧所以不会消失）。
2. **观感：巨物贴脸。** 柱体 257×382，玩家常在距墙 90~160 格处，
   一面 382 格高、带 16 格棱线的墙会占满整个视野，走动时棱线扫过 → 读作"它在动"。
   → 半透明填充仍覆盖**通高**（保留"全高度都算"的语义），
     但**笼子只画门所在高度 ±40 格**（`CAGE_HALF_HEIGHT`），让形状可判读。

> 判据（分辨"真跟随"与"巨物贴脸"）：站着不动时框是否稳定？
> 走动 40 格后，近处那面墙应当**退到身后**；若它保持同样的相对位置，才是真跟随。

### 6.2.4.5 剔除距离会伪造"世界锚定"证据

`maxRenderDistance` 默认 256。**超过它的位置本模组什么都不画**，
所以"远处画面干净"不能证明"没有跟随"——它可能只是被剔除了。
做任何锚定判定前，先确认目标在剔除距离以内。

playbook Rule 2：**「不生效」先打原始值，再猜原因**。本工程为此固定输出：

```
[probe] eye=(6.5, -56.38, 8.5) maxDist=256 columns=1 drawn=1 culled=0 submits/s=60
  | first anchor=(-120, -124) half=128 x=-120..136 z=-124..132 y=-63..318
```

判读表：

| 现象 | 结论 |
| --- | --- |
| `anchor` 随 `eye` 一起变 | 数据层在跟随 → 查扫描锚点 |
| `anchor` 不变，但画面还在动 | 数据没问题 → 查绘制坐标空间 |
| `drawn=0 / culled=1` | 被 `maxRenderDistance` 剔除，画面干净**不能**当作"没跟随"的证据 |
| `submits/s=0` | 提交根本没发生 |

> **踩过的坑**：曾把相机放到 400 格外背对柱体，看到画面干净就判定"世界锚定成功"。
> 但 `maxRenderDistance` 默认 256，那个位置整个柱体已被剔除 —— 干净是"没画"，
> 不是"没跟随"。**做判据前先确认目标没有被剔除。**
> 修正后的有效判据：站在 `maxRenderDistance` 以内、背对柱体，此时画面应当干净。

**gizmo 的两个已知事实**（playbook D4 + 本机实测）

* 不调 `persistForMillis` → 完全不绘制；
* gizmo 是**保留式**实例，`drainGizmos()` 会把未过期的全部再画一遍。
  所以**不要用 gizmo 画半透明面**，也不要去调 `persistForMillis` 消残影。

**为什么需要"可数结构"（1.3.2）**

站在 257 格的半透明体积**内部**时，填充是均匀一层罩色，里面没有任何可参照的刻度，
于是"看起来只有几十格"，而且移动时看到的远墙投影在变，容易被误认为"柱体在跟着我走"。

因此四壁加了**每 16 格一道竖向棱线**（锚定绝对区块边界），横向每 32 格一道环线：

* 棱线可数 → 能直接数出 16 个区块；
* 锚定区块边界 → 走动时结构不动，柱体没在跟人走这一事实变得可见。

> 教训：几何正确 ≠ 观感正确。数据层的 `half=128 / x=-40..216` 已被日志证明，
> 但没有任何刻度时，人无法判断大小。可数结构是让正确性"看得见"的必要条件。

### 6.2.5 形状：为什么是方形，不是圆柱

**结论：必须是方形/矩形。画成圆柱是错的。**

链路核对（26.3 字节码）：

1. 传送由 `Block#entityInside` 触发 = **玩家碰撞箱与传送门方块相交**，
   不是玩家中心落在方块内。玩家箱宽 0.6（半宽 0.3），所以他能站在门外 0.3 格仍然触发。
2. 进入点 `e` 被 `*scale` 映射到目标维度，然后在 **X/Z 方形**（`PoiManager#getInSquare`，
   切比雪夫距离）里搜索半径 `R`（下界 16 / 主世界 128）。
3. 所以"新门能否连到目标门 G"的条件是：存在可达进入点 `e`，使 `|map(e)-G| ≤ R`。

**形状推导**

| 参与量 | 形状 |
| --- | --- |
| 搜索区 | 轴对齐**方形**（切比雪夫） |
| 门户面 | 轴对齐**矩形**（宽 × 高） |
| 玩家碰撞箱 | 轴对齐**矩形** |

矩形 ⊕ 方形 = 轴对齐矩形。**不会产生圆**。
只有当玩家是球（半径 0.3）时才会把四角磨圆，磨圆半径 0.3 格，在这个尺度下看不出来。

> 用户早期参考图里的圆柱，是 MiniHUD 自己的球/柱形状参数，与传送门映射无关。

### 6.2.6 接触面余量（1.12.0）

```
half = R/|scale| + (门宽/2 + 0.3)
```

* 第一项是原来的半边长（主世界侧 128、下界侧 16）；
* 第二项是**接触余量，单位是玩家所在维度的方块，不乘 scale**
  ——玩家碰撞箱不随维度缩放。

主世界侧从 128 变成约 **129.3**（门宽 2 → 1 + 0.3），日志里四舍五入显示 `half=129`。
下界侧从 16 变成约 17.3，相对变化约 8%，比主世界侧明显。

实现：`LinkageMath.contactMargin(portalWidth)`，被 `RangePlanner` 的两个分支共同使用。
新门宽度未知，用所属门的宽度作代理（通常同尺寸）。

护栏：`assertOverworldColumnsAre128` 现在断言
`128 ≤ half ≤ 128 + MAX_SIZE/2 + 0.3` 且**宽深必须相等**（保持方形）。
### 6.2.7 地理位置绝不随玩家变化

数据层 `RangePlanner.plan` 的签名里**没有玩家位置**；渲染层 `eye` **只用于整体剔除**，
几何坐标全部来自 `RangeMarker`（世界坐标）。这两点是可以逐行核查的硬事实。

但仍有三个"看起来在动"的来源，前两个已修，第三个是 1.4.0 修的：

1. **门框被截断**：POI 查询会截断跨边界的门，门框由方块反推 → 中心偏移。
   现查询半径 +21（门最大尺寸）再用 `fullyInside` 只保留完整门。
2. **渲染层裁剪**：曾把可见几何裁剪成玩家周围（点阵半径、栏柱距离过滤）。
   现 `draw` 不接受相机参数。
3. **画出"扫描碰巧找到的门"**（1.4.1 修，这是"走开几百格它还跟着"的根因）：
   对维度扫描在找不到本维度传送门时会**退回以玩家位置为锚点**，于是无论走到哪都能
   扫到附近的若干下界门，并把它们全部画出来 —— 框自然永远出现在玩家身边。
   两处一起改：
   * `otherCenters` 不再回退到玩家位置；本维度没有门就**不扫对维度**（返回空列表）；
   * `RangePlanner` 只画 `entry.hasMatch()` 的对端门，即**确实是本维度某扇门的配对**，
     而不是"扫描顺带发现的门"。

> **判据**：`[ranges]` 日志里若 `entriesHere=0` 却仍有 `columns>0`，就是踩了这个坑。

护栏：`assertRangesStable`（挪开 30 格重扫逐组比对）、`assertNoTruncatedFrames`（门框 ≥2x3）。

> **排查心法**：先看 `[ranges]` 日志里的 `owner=` 有没有变。
> 变 → 是"找到的门换了"（扫描锚点问题，见上面第 3 条）；
> 不变 → 数据没问题，问题一定在绘制或观感。

### 6.2.8 诊断日志（不要再靠截图猜几何）

每次重扫输出一行到 `logs/latest.log`：

```
[ranges] columns=1 entriesHere=1 entriesOther=1 otherKnown=true authoritative=true
  | group=overworld@6,-56,0/the_nether@0,8,0 owner=0,9,0 half=128
    x=-120..136 z=-124..132 y=-63..318
```

`half` / `x..` / `z..` 就是柱体真实尺寸，可直接和上面的公式对照。
这条日志已经把两次几何误判当场抓出来了。

### 6.2.9 渲染分层

```
client/render/
├── PortalLinkRenderer      入口，编排 + 每扫描一次的诊断日志
├── PortalVolumeSubmitter   半透明柱体（自定义几何，相机相对坐标）
├── PortalRangeRenderer     棱边 / 横环 / 标签（gizmo）
├── PortalGhostRenderer     对端门幽灵 + 配对连线
├── PortalLabelRenderer     门上方两行标签
├── LinkColorAssigner       按链接组分配颜色（按分组 id 排序，颜色不随重扫变化）
├── PortalGizmoSupport      gizmo 公共操作
└── PortalPalette           固定语义色
```



```
client/render/
├── PortalLinkRenderer     入口，只做编排
├── PortalRangeRenderer    认领柱体（填充 + 线框 + 分带）
├── PortalGhostRenderer    对端门幽灵 + 配对连线
├── PortalLabelRenderer    门上方两行标签
├── LinkColorAssigner      按链接组分配颜色
├── PortalGizmoSupport     穿透/存活时长等公共 gizmo 操作
└── PortalPalette          仅保留冲突/未知等固定语义色
```

对应断言在 `src/gametest/.../PortalGroupAssertions.java`，已接入串门测试，
不依赖客户端即可跑（毫秒级失败），避免用 50 秒的客户端启动去发现分组错误。

> **游戏测试必须登记入口**：`src/gametest/resources/fabric.mod.json` 的
> `fabric-client-gametest` 是**显式列表**，新测试类不加进去就不会运行，
> 而构建依然显示成功——这是"假绿"，本工程真实踩过一次。

---

## 7. 已踩过的坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `InputConstants.Type.KEYSYM` 找不到 | 26.3 枚举改名 | 用 `KEYBOARD` |
| `Gizmos cannot be created here!` | 没有 collector 的线程调用 | 只在 `BEFORE_GIZMOS` 里画 |
| gizmo 一闪而过 | 默认不持久 | `persistForMillis(...)` |
| 传送门分组错误 | POI 是逐方块记录 | 连通分量分组后再算矩形 |
| 主世界/下界坐标搞反 | `scale` 是 **source/target** | 统一走 `LinkageMath.scaleBetween(from, to)` |
| 资源包换语言后文本不变 | 语言表被缓存 | 监听 `LanguageManager#getSelected` 变化并清缓存 |
| gametest 里 `Minecraft.getInstance()` 抛 IllegalStateException | 测试线程 ≠ 客户端线程 | 走 `context.computeOnClient` / `runOnClient` |
| 服务端 POI 扫描读到客户端字段 | 任务在服务端线程执行 | 进服务端线程前把维度/坐标/etc 全部捕获 |
| 下界写死主世界 y | 两维度高度范围不同 | 用 `getMinY()/getMaxY()` 夹取 |
| 断言"链接正确"用三维距离 | 原版搜索忽略 Y | 用水平距离 |
| `client.gui.getChat()` 找不到 | 26.3 聊天组件移到 `Hud` | 用 `client.gui.hud.getChat()` |
| `client.screen` 找不到 | 屏幕改由 `Gui` 持有 | 用 `client.gui.screen()` |
| 断言聊天文本失败、`recentChat` 为空 | `getRecentChat()` 是**玩家输入历史**，不是收到的消息 | 不改测原版投递；断言自己的可观察量（如 `warningCount()`） |
| 设置界面底部控件被裁 | 首行固定 `height/4` 导致溢出 | 先算总高再居中，并用控件边界断言兜底 |
| 范围方框飘在半空 | 沿用了对端门（下界）的 Y，两维度高度区间不同 | 平面 Y 取本维度关联门 Y − 2.5 并夹取，见 §6.2.4 |
| 范围随玩家移动 | 渲染层裁剪玩家周围，**以及**扫描窗截断门框导致中心偏移 | `fullyInside` 只保留完整门；`draw()` 不接受相机参数，见 §6.2.6 |
| 透明层完全不显示，只剩几条线 | 填充 `persistForMillis(1)` 比一帧还短，从未被绘制 | 间隔100/寿命300，N 恒为3，见 §6.2.4 |
| 不停闪烁 | ①发射间隔余量太小出现空档 ②颜色按扫描顺序分配，每次重扫重排 | 余量 200ms + 颜色按分组 id 排序分配 |
| 与 MiniHUD 完全不同 | MiniHUD 根本不用 gizmo，它走自定义顶点缓冲 | 已核实并记录等价 API，见 §6.2.4 |
| 大片橙色糊住视线 | 填充 250ms 寿命 + 每帧重发 = 约 15 层 alpha 叠加 | 同上，任何帧率下只留 1 层 |
| 柱体尺寸看着只有 6 区块 | 实际是 257 格（16 区块），此前因无填充难以目测 | `assertOverworldColumnsAre128` 钉死尺寸 |
| 水平范围侧视只剩一条线 | 相机与平面共面，水平线全部塌缩到地平线 | 改成全高柱体 + 横环，见 §6.2.4 |
| 同柱多门分带落到错的高度 | 分带依据误用「本维度关联门」的 Y | 必须用**对端门自己的 Y**，见 §6.2.5 |
| 新增游戏测试「通过」但从未执行 | `fabric-client-gametest` 入口是显式列表 | 新测试类必须登记到 `src/gametest/resources/fabric.mod.json` |
| 部署失败但工程被改成半截 | 游戏运行中 jar 被锁 | 脚本先删后复制，删失败即退出码 2 且不改动 mods，见 §6.1.1 |
| 串门场景两个门颜色一样 | 旧配色按「状态」给色，无法表达配对 | 改为按链接组配色，见 §6.2.2 |
| 真配对反被拆成两色 | 单向链接夺走了对端门的身份色 | `LinkGroups` 只认互指配对，单向自成组 |
| 范围标注被方框填充遮住 | 先画填充后画文字 | 已去掉填充，标签独立绘制 |

---

## 8. 验证纪律（必须遵守）

失败复盘见 `运行失败复盘.md`。以下是硬性流程，不是建议。

### 8.1 三档验证，成本差 15 倍

| 手段 | 成本 | 能发现 |
| --- | --- | --- |
| `.\tools\build.ps1 compile` | ~5 s | 编译错误、签名不符 |
| `.\tools\build.ps1 gametest` | ~1 min（首次 18 min，需下载素材） | 运行时崩溃、断言失败、视觉结果 |
| 看截图 | +一次推理 | 布局、可读性 |

**永远先跑便宜的。**

### 8.2 九条规则

| 编号 | 规则 | 针对的失败 |
| --- | --- | --- |
| R1 | 本会话首次使用的 API，先 `javap -public` 拿签名再写；禁止凭记忆写 API | 3 次签名猜错 |
| R2 | 改方法签名前 `grep` 全部调用点，列清单；改完立刻编译，不跨工具调用攒改动 | 5 处漏改 |
| R3 | `compileJava` 不过，禁止跑 `runClientGameTest` | 2 次无效运行 |
| R4 | 验证失败时，本轮只做"加只读探针"或"改一处"，禁止同时改夹具和行为 | 不知道谁修好的 |
| R5 | 夹具坐标从 `spawn` / `getMinY()` / `getMaxY()` 推导，禁止写死世界高度 | 下界 y 越界 |
| R6 | 断言必须对齐业务事实（链接 = 水平距离，不是三维距离） | 断言比代码更不懂业务 |
| R7 | 正确性用可机读的量断言；截图只做最终确认，取景用计算出的相机位置 | 6 次调相机浪费 |
| R8 | 同一子问题连续 2 次失败，停止改动，先写下"当前假设 + 下一验证步骤" | 叠加式修改 |
| R9 | 任何一轮结束前必须编译通过，不留红仓 | 开局即红 |

### 8.3 已知未消除隐患

- 首次 gametest 跑通的真实原因尚未证明（当时同时改了调度与夹具）。坐标/线程假设已被后续代码修正，且已在 4 个测试中稳定复现，但**未在真实存档复核**。

---

## 9. 客户端游戏测试（已跑通）

### 9.1 如何运行

```powershell
cd E:\Project\portallink
.\tools\build.ps1 gametest      # 自动设 JAVA_HOME / GRADLE_USER_HOME，约 1 分钟
```

等价的手工命令：

```powershell
cd E:\Project\portallink\source\portallink
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'
$env:GRADLE_USER_HOME = 'E:\Project\portallink\.gradle-home'
E:\Project\portallink\toolchain\gradle-9.7.1\bin\gradle.bat compileJava compileGametestJava   # 门禁
E:\Project\portallink\toolchain\gradle-9.7.1\bin\gradle.bat runClientGameTest --console=plain
```

`build.gradle` 里通过 Loom 开启：

```groovy
fabricApi {
    configureTests {
        createSourceSet = true
        modId = 'portallink-test'
        enableGameTests = false
        enableClientGameTests = true
        eula = true
        clearRunDirectory = true
    }
}
```

游戏测试入口键是 **`fabric-client-gametest`**（不是 `client`），声明在 `src/gametest/resources/fabric.mod.json`。

### 9.2 26.3 测试 API 关键事实（已逐个 javap 验证）

| 事实 | 说明 |
| --- | --- |
| 测试线程 ≠ 客户端线程 | 任何 `Minecraft.getInstance()` 必须走 `context.runOnClient` / `computeOnClient`，否则抛 `IllegalStateException` |
| 服务端线程同理 | 用 `singleplayer.getServer().runOnServer` / `computeOnServer` |
| 聊天历史不可读 | `ChatComponent.getRecentChat()` 是**玩家输入历史**，不是收到的消息；26.3 没有公开的"已收消息列表"，`ChatComponent.State` 也没有 getter |
| 聊天组件位置 | `Minecraft.gui.hud.getChat()`（`Gui` 本身没有 chat 字段） |
| 屏幕字段 | `Minecraft.gui.screen()`（不是 `client.screen`），关闭用 `setScreenAndShow(null)` |
| 客户端命令可执行 | `client.player.connection.sendCommand("...")`，Fabric 的 `ClientPacketListenerMixin` 会拦截并在本地执行 |
| 控件坐标 | `AbstractWidget.getX/getY/getRight/getBottom`，可用来断言"控件不越界" |
| 截图 | `context.takeScreenshot(name)` → `build/run/clientGameTest/screenshots/NNNN_name.png` |
| 专用服务器 | `worldBuilder().createServer()` + `.connect()`，用于验证多人路径 |

### 9.3 测试设计原则

1. **断言可机读的量**：状态枚举、水平距离、快照时间戳、控件边界，而不是截图肉眼看。
2. **不为测试改生产代码**：唯一例外是 `ConflictNotifier.warningCount()`，因为聊天内容无法从外部读取，这是唯一可观察证据。
3. **夹具从运行时推导**：`spawn` / `getMinY()` / `getMaxY()`，禁止写死世界高度。
4. **配置在测试开始时重置**：`PortalFixtures.resetConfig()`，防止同一 JVM 内测试互相污染。

### 9.4 覆盖矩阵

| 场景 | 测试类 |
| --- | --- |
| 单人正常链接 + 渲染 | `PortalLinkClientGameTest` |
| 串门（不对称链接）+ 警告 | `PortalLinkConflictGameTest` |
| 设置界面 / HUD / 命令 | `PortalLinkUiGameTest` |
| 专用服务器（客户端扫描兜底） | `PortalLinkMultiplayerGameTest` |

---

## 10. 发布产物

`source\portallink\build\libs\portallink-1.0.0.jar`（74,827 字节，本次已复现校验）。

已部署到：`E:\@26.3\.minecraft\versions\26.3-Fabric\mods\portallink-1.0.0.jar`

jar 内**不含**游戏测试类（`src/gametest` 是独立 source set）。发布前检查清单：

- [ ] `.\tools\build.ps1 build` 通过
- [ ] jar 内有 `fabric.mod.json`、`assets/portallink/icon.png`、两个 lang 文件
- [ ] jar 内**没有** `com/heishan/portallink/gametest/`
- [ ] 实例 `mods/` 里只有一份 portallink jar（重复会导致 mod 冲突）
- [ ] `E:\@26.3\.minecraft\mods\` 不要留副本（该目录不被此实例使用）

### 10.1 本次环境复现记录（2026-11-14）

| 步骤 | 结果 |
| --- | --- |
| 工程内安装 Gradle 9.7.1（腾讯镜像） | 144 MB / 2.3 秒 |
| `build`（首次，含依赖下载） | BUILD SUCCESSFUL，4 分 45 秒 |
| `compileJava`（增量） | BUILD SUCCESSFUL，4–5 秒 |
| `compileGametestJava` | BUILD SUCCESSFUL，1 秒 |
| `runClientGameTest` | BUILD SUCCESSFUL，17 分 45 秒（首次需下载 253 MB 素材），4 个测试全过，产出 6 张截图 |
| 产物大小 | 74,827 字节，与上一版完全一致（构建可复现） |
| jar 内容 | 52 个条目，含两个 lang + icon，无 gametest |

> 游戏测试首次运行会下载 Mojang 素材（`resources.download.minecraft.net`，约 253 MB，4930 个文件），
> 缓存在 `.gradle-home\caches\fabric-loom\assets\`，之后重跑约 1 分钟。


---


### 6.2.10 连接线已默认关闭（1.13.0）

`PortalGhostRenderer.renderConnector` 会从门顶画一条线指向「对端门映射回本维度的位置」，
并把 Y 拉平。跨维度时那个位置是空气，没有直观意义；它也是范围相关元素里**最后一个还在用 gizmo** 的，
是闪烁隐患。`renderConnectors` 默认改为 `false`，需要时可手动开。

### 6.2.11 传送门编号（1.13.0）

目的：用编号代替坐标来识别「串门」的是哪扇门。坐标需要记忆，`#3 → #7` 一眼就能对比。

**实现**：`client/PortalIdStore.java`，客户端持久化到 `config/portallink-ids.json`：

```json
{ "ids": { "overworld@48,64,-87": 1, "the_nether@10,74,-14": 2 }, "next": 3 }
```

* 键 = `PortalRef.key()`（维度@最小角），与 `RangeMarker` 侧用
  `idOf(ownerPos, ownerIsNether)` 重建同形键，保证同一扇门只有一个编号；
* **首次见到即分配 `next++`，永不重排、永不复用**。
  按坐标排序编号被否决：新增一扇门会让后面所有编号错位，正好破坏它存在的意义；
* 读取时用现有最大值修复 `next`，手改或损坏的文件不会发出重复编号。

**显示位置**：门上方标签首行 `#N 状态`、第二行 `→ #M x,y,z (d 格)`、
状态列表 `#N → #M`、区域中央 `#N`、串门聊天提示 `#N … 通向 #M …`。

> 编号只用于显示，不参与任何链接判定。
### 6.2.12 配色强度（1.14.0）

用户反馈"太淡看不清"。两个独立因素，一起调：

| 参数 | 旧 | 新 | 说明 |
| --- | --- | --- | --- |
| `zoneWallAlpha` | 70（27%） | **120（47%）** | 主因：alpha 低，透过天空/地形后颜色被冲淡 |
| `zoneEdgeAlpha` | 230 | **255** | 棱边不透明，任何时候都看得见 |
| `zoneStrokeAlpha` | 200 | **255** | 同上 |
| `LinkColorAssigner.SATURATION` | 0.62 | **0.78** | 同 alpha 下颜色更实，相邻配对更好分辨 |

`PortalPalette.CONFLICT`（串门警告红）是独立常量，不受饱和度调整影响，始终醒目。

> 注意：alpha 提太高会重新出现"遮挡视线"的问题（1.1.x 踩过）。
> 47% 是折中值；若仍嫌淡，优先提饱和度而不是继续压 alpha。
### 6.2.13 按键与分类名（1.16.0）

**只有一个按键**：`P` = 总开关（`config.enabled`），控制本模组全部显示。
原来的状态列表键（B）与设置界面键（O）已删除——"关掉模组"不该需要记住三个键。
设置界面仍可从 Mod Menu 进入。

**分类名的坑（实测）**：`KeyMapping.Category.label()` 的实现是

```java
Component.translatable(this.id.toLanguageKey("key.category"))
```

所以 `portallink:main` 对应的语言键是 **`key.category.portallink.main`（单数 category）**。
我最初写成 `key.categories.portallink.main`（复数，模仿原版内置分类的风格），
匹配不上 → 按键界面直接显示原始键名，看起来像"没显示程序名"。

> 判据：`javap -p -c net.minecraft.client.KeyMapping$Category` 看 `label()` 里的
> `toLanguageKey` 前缀字符串，不要凭原版名字猜。
### 6.2.14 一个按键控制全部显示（1.16.2）

`P` 切换 `config.enabled`，它是**唯一的总开关**。世界渲染与左上角列表都挂在这一个开关下：

```java
if (!config.enabled || !config.showStatusPanel) { return; }   // HUD
```

`showStatusPanel` 默认改为 `true`，所以按一次 P 就能把整个世界显示 + 列表一起关/开。
想只要世界、不要列表的人可以在设置里单独关。

**踩过的坑**：改默认值**不会覆盖玩家已有配置**。
排查时先读实例里的 `config/portallink.json`——本次玩家的文件里已经是
`"showStatusPanel": false`，只改代码默认值对他无效，必须同时修正配置文件。
判据：确认某项"没生效"之前，先看磁盘上那份 json 里的实际值。
### 6.2.15 Mod Menu 的名称与简介本地化（1.16.3）

`fabric.mod.json` 的 `name` / `description` 是**英文硬编码，无法本地化**。
Mod Menu 另有一套翻译键，写在语言文件里即可：

```
modmenu.nameTranslation.portallink
modmenu.descriptionTranslation.portallink
```

**约定来源（实测，不是猜的）**：解包实例里已装的模组，
`waveycapes`、`appleskin`、`bubbleworld` 的 lang 文件里都在用
`modmenu.descriptionTranslation.<modid>`。所以这套键在当前实例确实生效。

> 只加 `zh_cn.json` 就够（英文用户看到 `fabric.mod.json` 原文）；
> 本工程两个文件都写了同名键，是为了让中英键集合始终对齐，便于以后核对缺漏。
---

# 11. NeoForge 移植（1.21.1 与 26.3）— 2026-11-16

同一份业务逻辑现在有三个构建目标。**核心算法（`core/`）、配置、编号、扫描策略完全共用**，
只有「加载器胶水层 + 渲染层」按代际不同。

| 目标 | 工程目录 | MC | NeoForge | FML | Java | 产物 |
| --- | --- | --- | --- | --- | --- | --- |
| Fabric | `source\portallink` | 26.3 | — | — | 25 | `portallink-1.16.3.jar` |
| **NeoForge 26.3** | `source\portallink-neoforge-26.3` | 26.3 | `26.3.0.103` | 12.x | 25 | `portallink-26.3-neoforge-1.16.3.jar` |
| **NeoForge 1.21.1** | `source\portallink-neoforge-1.21.1` | 1.21.1 | `21.1.266` | 4.0.45 | 21 | `portallink-1.21.1-neoforge-1.16.3.jar` |

已部署（回读 SHA256 一致）：

* `E:\@26.3\.minecraft\versions\26.3-NeoForge\mods\`
* `E:\@26.3\.minecraft\versions\1.21.1-NeoForge\mods\`

## 11.1 构建命令

```powershell
cd E:\Project\portallink\source\portallink-neoforge-26.3     # 或 ...-1.21.1
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot'   # 1.21.1 工程用 jdk-21.0.13.21
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
E:\Project\portallink\toolchain\gradle-9.7.1\bin\gradle.bat build --console=plain
```

`build` 在依赖已缓存时约 5 秒；首次需要 neoform 反编译（26.3 约 20 秒，1.21.1 约 90 秒）。

## 11.2 实测：两次移植的差异（用真实 jar / 真实实例核过）

### 26.3 NeoForge：只换胶水层，渲染用「调试渲染器发 gizmo」

| 事项 | 事实 |
| --- | --- |
| 世界渲染钩子 | **没有** `RenderLevelStageEvent` 的等价「立即模式」入口；正解是 `RegisterDebugRenderersEvent` 注册 `DebugRenderer.SimpleDebugRenderer`，在 `emitGizmos(...)` 里调 `Gizmos.*` |
| 该钩子是否每帧都跑 | 是。`LevelExtractor` **无条件**调用 `DebugRenderer#emitGizmos`；`DebugRenderer` 只对**原版自己的**渲染器按 F3 条目过滤 |
| gizmo 是否需要寿命 | **不需要**。原版 `PoiDebugRenderer` / `VillageSectionsDebugRenderer` / `ChunkDebugRenderer` 的字节码里都没有 `persistForMillis`，逐帧重发即可；因此半透明面**不会**叠副本、不会闪 |
| 唯一用 gizmo 画半透明面的前提 | 钩子必须是「逐帧重发」型；Fabric 那支是「保留式收集器」，所以那边必须走提交式网格 |
| HUD | `RegisterGuiLayersEvent.registerAboveAll(Identifier, GuiLayer)`，`GuiLayer#render(GuiGraphicsExtractor, DeltaTracker)` |
| 按键 | `new KeyMapping(name, InputConstants.Type.KEYBOARD, InputConstants.KEY_P, new KeyMapping.Category(id))` + `event.registerCategory(...)`；分类语言键是 `key.category.portallink.main`（单数） |
| 配置目录 | `FMLPaths.CONFIGDIR.get()` |
| 客户端命令 | `RegisterClientCommandsEvent#getDispatcher()` → `CommandDispatcher<CommandSourceStack>`，回显 `sendSuccess(Supplier<Component>, boolean)` |
| 模组列表里的设置入口 | `modContainer.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new PortalLinkConfigScreen(parent))`，且必须 `FMLEnvironment.getDist().isClient()` 守卫 |

### 1.21.1 NeoForge：渲染层整体重写（上一代）

| 事项 | 事实 |
| --- | --- |
| 标识符 | `ResourceLocation`（不是 `Identifier`）；`ResourceKey#location()`（不是 `identifier()`） |
| 世界渲染钩子 | `RenderLevelStageEvent`，取 `Stage.AFTER_ENTITIES`；**姿势栈是单位阵**，必须自己 `pose.translate(-cam.x, -cam.y, -cam.z)` |
| 顶点 | 手写 `VertexConsumer`。线用 `RenderType.lines()`（`POSITION_COLOR_NORMAL`，**漏 `setNormal` 静默不画**）；面用 `QUADS` |
| 可复用的原版助手 | `LevelRenderer.renderLineBox(pose, consumer, AABB, r,g,b,a)`、`DebugRenderer.renderFloatingText(pose, buffers, text, x,y,z, color, scale, centered, bgAlpha, seeThrough)`（内含相机平移 + 朝向相机旋转） |
| 穿墙 | 没有 gizmo 的 `setAlwaysOnTop`，自建 `RenderType.create(..., NO_DEPTH_TEST, ...)` |
| `debugFilledBox()` 不能用来画面 | 它是 `TRIANGLE_STRIP`；任意四边形必须自己按 `QUADS` 顺序发四个顶点 |
| HUD | `LayeredDraw.Layer#render(GuiGraphics, DeltaTracker)`，`RegisterGuiLayersEvent.registerAboveAll(ResourceLocation, Layer)` |
| 输入枚举 | `InputConstants.Type.KEYSYM`（26.x 才叫 `KEYBOARD`） |
| 高度 API | `getMinBuildHeight()` / `getMaxBuildHeight()`（26.x 是 `getMinY()` / `getMaxY()`） |
| `ChunkPos` | 没有 `containing(BlockPos)` / `x()` / `z()`；用 `getMinBlockX()` 或自己 `>> 4` |
| `CycleButton` | `builder(Function<T,Component>)`，初始值靠 `.withInitialValue(...)` |
| 打开界面 | `Minecraft#setScreen`（26.x 是 `setScreenAndShow`） |
| 聊天 | `LocalPlayer#displayClientMessage`（`sendSystemMessage` 在 1.21.1 也存在） |
| 事件总线 | `@EventBusSubscriber` 只有 `value`(Dist) / `modid`，**没有 bus 参数**；实现了 `IModBusEvent` 的进 mod 总线，其余进 game 总线，同一个类可以混写 |

## 11.3 真实实例验证证据（不是「编译通过」）

用 `tools\launch-instance.ps1` 直接按实例 json 起客户端（见 11.5），`--quickPlaySingleplayer` 进世界：

**26.3-NeoForge（真实存档「新的世界 (3)」，含真实下界门）**

```
[Portal Link/]: [probe] world renderer active: eye=(56.5, -62.8, 40.5) enabled=true empty=true ...
[Portal Link/]: [ranges] eye=(56.5, -61.4, 40.5) columns=1 entriesHere=1 entriesOther=1
  otherKnown=true authoritative=true | group=overworld@86,-63,114/the_nether@11,1,14
  owner=11,2,14 half=129 x=-34..225 z=-14..245 y=-63..318
```

换到另一扇门附近时输出 `half=17`（`16 × 8 + 接触余量`），与 `LinkageMath` 的公式一致；
`authoritative=true` 说明走的是服务端 POI 索引。**两种半边长都出现过**，说明两个分支都被执行。

**1.21.1-NeoForge（夹具世界 `portprobe`，数据包放了一扇 2×3 传送门）**

```
[Portal Link/]: [probe] world renderer active: eye=(-25.5, 55.0, -54.5) enabled=true empty=true ...
[Portal Link/]: [ranges] eye=(-25.5, 56.6, -54.5) columns=1 entriesHere=1 entriesOther=0
  otherKnown=false authoritative=true | group=solo:overworld@0,100,0 owner=0,101,0
  half=129 x=-129..130 z=-129..129 y=-63..319
```

两次都**没有**本模组的 WARN/ERROR，`[ranges]` 各只出现 1 次（空闲重扫不再刷屏）。

## 11.4 已知限制（不要当成 bug 重查）

* **本次会话里截图取不到游戏画面**：从这个会话启动的客户端窗口一直是纯白（渲染线程正常在
  `FramerateControl.limitDisplayFPS` 里跑，日志与逻辑都正常）。因此**视觉确认没有做**，
  上面的证据全部是日志侧的机读量。要目视确认，请在用户自己启动的实例里按 P 看范围。
* **`runClient` 的自动 quick-play 已从 build.gradle 撤掉**，避免影响日常开发；验证请用 11.5 的脚本。

## 11.5 真机启动器 `tools\launch-instance.ps1`

按实例 json 拼命令行（等价于启动器），用于「验证部署后的 jar」而不是「验证工程源码」。

```powershell
.\tools\launch-instance.ps1 -Instance 26.3-NeoForge -QuickPlayWorld '新的世界 (3)' -NoEarlyWindow
.\tools\launch-instance.ps1 -Instance 1.21.1-NeoForge -QuickPlayWorld portprobe -NoEarlyWindow
.\tools\launch-instance.ps1 -Instance 26.3-NeoForge -DryRun      # 只打印命令行
```

三个实测坑（脚本里已处理，别踩回去）：

1. **JVM 参数文件里不能放游戏参数**：`java @file` 会把文件里的一切当 JVM 选项，出现
   `Unrecognized option: --username`。文件只放 JVM 参数，主类与游戏参数走命令行。
2. **`-Dfml.earlyprogresswindow=false`**：FML 的早期进度窗口是**另一个 OS 窗口**，会盖住真窗口
   并显示为纯白，截图全是白的。验证时关掉它。
3. **实例 json 里的空占位符要成对丢弃**（`--width ${resolution_width}`），并且要丢掉
   `--demo`；否则以试玩模式启动。

## 11.6 环境事实（本机新增，踩过）

| 事实 | 判据 / 处理 |
| --- | --- |
| `maven.neoforged.net` 直连被重置 | DNS 落到 `1755624453.rsc.cdn77.org`（195.181.172.x），`curl` 直连 `http_code=000`；走本机 `127.0.0.1:10808` 代理则 200（0.45 s）。已在 `%USERPROFILE%\.gradle\gradle.properties` 写 `systemProp.http(s).proxyHost/Port` |
| 26.3 工程的 `downloadAssets` 要 JDK 21 | Gradle 只可靠地自动发现启动它的那个 JDK；两个 JDK 都写进 `org.gradle.java.installations.paths` |
| **`neoforge.mods.toml` 的 minecraft 版本范围必须按工程填** | 1.21.1 工程一开始从 26.3 抄了 `[26.3,26.4)`，结果真实 1.21.1 实例直接拒绝加载：`Mod portallink requires minecraft 26.3 or above, and below 26.4 / Currently, minecraft is 1.21.1`。现在两工程都用 `gradle.properties` 的 `minecraft_version_range` 展开 |
| 判断「toml 主导的拒绝加载」比看 crash 快 | 判据：`logs/latest.log` 里出现 `Mod portallink requires minecraft ...`；`jar_check.py` 只查 `loaderVersion`，**查不出** mc 版本范围，要单独解包核对 |

---

# 12. 修 2026-11-16 的三个反馈（26.3 双范围 / 1.21.1 无标签 / 下界范围像在跟随）

## 12.1 1.21.1 传送门上没有文字 — 根因是「相机被减了两次」

`DebugRenderer#renderFloatingText` 自己会 `translate(x - cameraX, y - cameraY, z - cameraZ)`，
然后交给 `Font#drawInBatch` 的矩阵。我原来把它放在 `pose.translate(-cam)` 里调用，于是标签
最终落到 `world - 2*camera`，直接飞出屏幕 —— 表现就是「门上什么都没有」，而且不报任何错。

**实测判据**（本次新增 `[pose]` 探针，一次会话一行）：

```
[pose] stage=minecraft:after_entities translation=(0.0, 0.0, 0.0) camera=(-25.6, 54.6, -54.5) inCameraSpace=false
```

`AFTER_ENTITIES` 的姿势栈确实是单位阵，所以几何体必须自己减相机（原来那行是对的）；
但**文字助手不需要，它自己会减**。改法：几何体在 `-cam` 的姿势里画，标签在 `popPose()` 之后
用**恒等姿势 + 世界坐标**画。新增 `[labels]` 探针证明该路径确实跑到且姿势正确：

```
[labels] areaLabels=1 portalLabels=1 poseAtIdentity=true
```

区域编号 `#N` 也挪到了这条标签通道里（`PortalRangeRenderer.drawAreaLabels`），否则它同样会被减两次。

## 12.2 「下界显示范围跟着玩家移动」 — 通高半透明罩子贴脸，不是坐标错

先把数据层排除掉：注视点每帧变化时，`[ranges]` 里的 `owner` / `x..` / `z..` 一个字节都没变，
姿势矩阵也证明几何体是世界坐标。真正的原因是**观感**：下界侧的范围只有约 17 格宽，而侧壁原本
铺满整个可建高度（0..256），叠加 `alwaysOnTop`（穿透地形）后，无论站在哪里四面墙都在视野里
——**看起来就像一个跟着人走的罩子**。这正是 `mod_context` 第 6.2.4 节记录过的老问题。

改法（两个 NeoForge 工程一致）：

| 元素 | 改前 | 改后 |
| --- | --- | --- |
| 半透明侧壁 | 整个柱体 `bottomY..topY`（下界 256 格） | `ownerY - zoneDownHeight .. ownerY + zoneUpHeight`（默认 -4/+12，**锚在门上**） |
| 地面 | 无 | 在表面底部画一层半透明地板（`zoneFillAlpha`，双面），读作「地上的一片区域」 |
| 线框（角柱 + 上下环） | 通高 | **仍通高**，所以「搜索不考虑 Y、整根柱体都算」的语义仍然看得见 |

`fullHeightZones` 保留原意（`true` = 要回旧的全高罩子）。

**踩坑（重要）**：改代码里的默认值对已存在的 `config/portallink.json` **无效**，而且我第一
次改完立刻启动客户端，客户端退出时用内存里的旧值 `save()` 把文件**又写回 true**。正确顺序是
「先关游戏 → 再改 json → 再启动」。本次已把两个实例的配置都改成 `false` 并回读确认：

```
[1611月2026 05:37:15] [ranges] ... y=-63..318 surface=-2..14      # 26.3，门在 y=2
[1611月2026 05:39:16] [ranges] ... y=-63..319 surface=97..113     # 1.21.1，门在 y=100
```

`surface=` 是本次新加的诊断字段：它就是侧壁/地板实际的高度区间，和 `y=`（线框范围）区分开。
**判据**：`surface` 必须贴着门的 Y 且不随玩家 Y 变化；`y` 才是整根柱体。

## 12.3 26.3「每个门渲染出两个范围」

先排除了「几何体被发两遍」这个假设，并留下判据：

```
[rate] emit/s=29.9   （同屏 30 fps；世界加载前是 60.0）
```

钩子**每帧只跑一次**，所以不是重复发射，只能是**规划层产出了两个 marker**。

按代码路径，一个门出现两个范围只可能来自 `RangePlanner` 的第二条分支（本维度门自己那一份
“将新建传送门”的兜底范围）。该分支现在只在**对端一个可链接的门都没有**时才画：

```
otherDimensionHasLinks = 对端存在任意 hasMatch 的条目
if (!otherDimensionHasLinks) { ... 才画本维度兜底范围 ... }
```

理由：对端一旦有门能指过来，它的范围已经回答了“在这里建门会连到哪”，本维度再画一份就是
同一问题的第二个框（也就是反馈里的「下界的在这里渲染一个、主世界的自己又渲染一个」）。
只有一扇孤门、对端什么都没有时，兜底范围仍有意义，此时保留。

⚠ **这一条没有被复现验证**：用户报的位置上（`新的世界 (3)` 门旁）改前改后都是 `columns=1`，
我构造不出「对端有链接 + 本维度未链接」的组合（数学上两边对称，见 6.2.7 的推导）。
因此 `12.3` 是按语义收敛的改动，不是按复现修掉的 bug。若仍出现双框，用下面两行日志即可定位：

```
[ranges] columns=2 ...
  | src=other ...   ← 对端的门映射到这里（应当有）
  | src=local ...   ← 本维度门自己的兜底范围（不应与上一行同时出现）
[entries] | <门键>=LINKED-><对端键> | ...
```

## 12.4 本次新增的常驻诊断（廉价、每次变化只打一行）

| 前缀 | 何时打 | 用途 |
| --- | --- | --- |
| `[probe]` | 每会话一次 | 渲染钩子是否被调用（B 类静默失败的唯一凭证） |
| `[pose]` | 每会话一次（1.21.1） | 姿势栈是否已内含相机 |
| `[labels]` | 每会话一次（1.21.1） | 标签通道是否跑到、姿势是否为恒等 |
| `[ranges]` | 列集合变化时 | `owner/half/x../z../y../surface=` 全部机读 |
| `[entries]` | 列集合变化时 | 每个门的状态与指向，双框问题的直接证据 |

## 12.5 追加：26.3 的「区域编号根本发不出来」（`billboardTextOverBlock` 参数顺序）

读 26.3 反编译源码（`build/moddev/artifacts/minecraft-patched-26.3.0.103-sources.jar` 的
`net/minecraft/gizmos/Gizmos.java`）时发现签名是：

```java
public static GizmoProperties billboardTextOverBlock(String text, BlockPos pos, int row,
                                                    int color, float scale)
```

**第三个参数是 `row`（行号），不是颜色。** 它内部是
`Vec3.atLowerCornerWithOffset(pos, 0.5, 1.3 + row * 0.2, 0.5)`。

我原来把颜色塞进了 `row`：

```java
Gizmos.billboardTextOverBlock("#" + id, pos, PortalPalette.withAlpha(rgb, 0xFF), 0xB0000000, scale)
```

于文字被放到 `1.3 + 0xFFxxxxxx * 0.2` ≈ **8.6 亿格高空**，永远看不见 —— 区域中央的 `#N`
从来没有出现过，而且不报任何错。修法：`(text, pos, 0, colour, scale)`。

> 参考实现 `临时文件/`（realmbridge）里也是同样写错的（`("#"+id, pos, colour, 0xB0000000, 1.0F)`），
> 所以「照着能跑的参考抄」在这条上抄到了同一个 bug。**判据：读签名，不要读调用点。**

新增 `[labels]` 探针（两个工程各一份）确认编号确实被发出：

```
26.3  : [labels] areaLabels=1 (row=0, colour=area colour)
1.21.1: [labels] areaLabels=1 portalLabels=1 poseAtIdentity=true
```

## 12.6 「两个范围」为什么看起来像两个：两个框本来就几乎重合

把两处候选几何算出来放在一起（用户存档里的真实数字：主世界门 A=(86,-63,114)，
下界门 G=(11,2,14)）：

| 框 | 中心 | 半边长 | 覆盖范围 |
| --- | --- | --- | --- |
| 对端的门（G 映射回主世界） | (88, 112) | 129 | `x=-34..225 z=-14..245` |
| 本维度兜底（A 自己） | (86, 114) | 129 | `x=-43..215 z=-15..243` |

两者只差 9 格 —— 因为 `A/8*8 ≈ A`。所以「本维度兜底范围」一旦被画出来，就与对端范围**几乎完全
重合**，看上去正是「同一个门画了两个范围」（一个略大、略微错位，叠在一起像重影）。

这也解释了为什么反馈里能同时看到「下界的在这里对应渲染一个」和「主世界的自己也在自己维度渲染了
一个」——**它们是同一条链接的两种画法**。

## 12.7 现在的保证：一对门只可能有一个范围（按构造）

`RangePlanner` 的两条分支现在是**互斥**的：

| 对端数据状态 | 画什么 | 数量 |
| --- | --- | --- |
| 对端存在任意 `hasMatch` 的条目 | 只画「对端的门映射到这里」的范围（`src=other`） | 1 |
| 对端一个可链接的门都没有 | 只画本维度门自己的兜底范围（`src=local`） | 1 |

对端扫描**要么**给出一个能指回来的门（此时本维度的门必然也有匹配，兜底分支也不触发），
**要么**什么都没给出（此时没有 `src=other` 可画）。两种状态不可能同时出现，所以那对几乎重合的
框再也不会同时被画。

⚠ 仍未复现：用户报的位置上，改前改后都是 `columns=1`、两个门都 `LINKED`。上面 12.7 是**按构造**
排除，不是按复现修掉。若仍见双框，`[ranges]` 里会同时出现 `src=other` 与 `src=local`，一行定位。

## 12.8 试过但不可行的验证手段（不要再试）

想用「纯 Java 离线跑 `RangePlanner`」来免开游戏验证，写了个 `main` 直接 new `BlockPos` /
`Level.OVERWORLD`：编译通过，但运行期 `BlockPos`→`Level` 的静态初始化会碰到
`net.neoforged.neoforge.attachment.AttachmentHolder`，进而要求
`FMLEnvironment.isProduction()` → `IllegalStateException: There is no current FML Loader`。

**结论：核心类虽然「纯逻辑」，但引用了 `Level`/`BlockPos`，无法脱离 FML 引导独立运行。**
要离线断言只能引 JUnit + 自造 stub（工作量大且会与真实类漂移），所以验证统一走真机日志
（`[ranges]` / `[entries]` / `[labels]` / `[pose]`），这也是本工程一直采用的方式。

## 12.9 双框的硬保证：`RangePlanner.onePerLinkGroup`

12.7 的「互斥」依赖对端数据状态的分支判断；为了让结论不依赖任何时序/截断假设，在规划器的
最后又加了一道**按链接组去重**（`onePerLinkGroup`）：

```java
// 一个链接组 = 一条链接 = 每个维度只能有一块范围
for (RangeMarker marker : markers) {
    RangeMarker previous = byGroup.get(marker.groupKey());
    if (previous == null || (!previous.existing() && marker.existing())) {
        byGroup.put(marker.groupKey(), marker);   // 真实存在的门优先于「将新建」兜底
    }
}
```

之所以这条一定命中：反馈里那对框的 `groupKey` **相同**（都是 `overworld@86,-63,114/the_nether@11,2,14`
那条链接），而它们只差 9 格；`cutIntoBands` 不会给同一组产两个 marker（它是按组归并的），
所以这对框只可能来自「对端分支 + 本维度兜底分支」，两边同组 → 去重后只剩一个。

配套的**运行期不变式**（两个工程的渲染器里都有）：

```
[invariant] N areas for M link groups - duplicate area per link
```

`markers.size() != distinct(groupKey)` 就报警。它只在违反时出声，所以「静默」才是有意义的证据；
本次两轮真机运行都没有出现该行。

> 取舍说明：`onePerLinkGroup` 与 12.7 的分支互斥**功能重叠**。保留两条是有意的——
> 分支互斥表达语义（对端已知就不画兜底），组去重兜底所有我还没想到的数据形状
> （例如某侧扫描被截断导致同一链接两侧不对称）。两者都不改变单门/孤门的显示。

## 12.10 第三轮反馈：26.3 仍双层 / 多余水平层 / 1.21.1 下界仍像跟随

### （A）26.3 的「双层」不是我们的 bug：实例里还装着一个同类模组

`E:\@26.3\.minecraft\versions\26.3-NeoForge\mods` 里有 **`realmbridge-1.0.0.jar`** ——
就是 `临时文件/` 那个旧 NeoForge 参考工程。它自己也画传送门范围：地板扇形 + 侧壁 + 边界线 + `#N`，
而且它的 `baseY = floor(playerY / 8) * 8 + 4` **是按玩家位置算的**（逐 8 格跳）。

因此 26.3 里每个门有两个框、并且其中一个看起来会跟着人走，是两套渲染叠加。
截图可以对照：贴地那套是 realmbridge（按玩家 Y），高处那道横带是我们的。

**处置**：我试过把它改名禁用，NeoForge 立刻警告
`realmbridge (version 1.0.0 -> MISSING)`（世界/mod 列表记录了这个模组），为不擅自改动玩家的
存档环境，已**原样恢复**。留谁由用户决定：本模组是它的移植升级版，建议二选一。

### （B）「多余的水平渲染一层」= 我上一轮加的地板，已删除

上一轮把表面裁到 `ownerY ± zoneUp/Down` 并在底部加了一层地板。问题是 **Y 不跨维度**：
主世界视角下 owner 是下界门（y=2），于是那块地板被放在 y=-2，而玩家在 y≈-56 ——
头顶 54 格处凭空多出一层半透明平面。

而且这个裁剪在下界视角会算出**非法带**：owner 是主世界门（y=-62），
`max(0, -62-4)=0` 到 `min(255, -62+12)=-50` —— 上界低于下界，墙是反的。

**改法**：删掉地板；侧壁恢复为整根柱体（`bottomY..topY`，与 Fabric 版一致，因为 Y 本身没有意义）；
`surfaceBottom/surfaceTop` 与 `fullHeightZones` 对表面的作用一并移除。

### （C）新规则：人站在柱体里就不画半透明面

```java
if (config.showZoneWalls && !eyeInside(marker, eye)) { walls(...); }   // 人在柱体内 → 只画线框
```

理由：柱体是**通高**的，人一站进去，四面墙就在身后或贴身；此时半透明面不再读作“那边有个体积”，
而读作“一层贴着我走的膜”。下界侧范围只有约 17 格宽，人几乎总在里面 —— 这正是「跟着玩家跑」
的来源。线框（4 根通高角柱 + 上下环）保留，所以范围本身仍然看得见、而且是世界锚定的。

`[ranges]` 新增 `surface=wall|wire` 判据：

| 实测 | 结果 |
| --- | --- |
| 26.3（玩家在柱体外，eye=(-58,-32,159)） | `surface=wall` |
| 1.21.1（玩家在柱体内，eye=(-20,59,-55)，柱体 x=-129..130 z=-129..129） | `surface=wire` |

### （D）本轮验证到哪一步

* 26.3：`[ranges] ... surface=wall`、`[labels] areaLabels=1`、零 WARN/ERROR
* 1.21.1：`[probe]`、`[ranges] ... surface=wire`、`[labels] areaLabels=1 poseAtIdentity=true`、零 WARN/ERROR
* 部署 sha：26.3 `02F37515E746`，1.21.1 `31E506A3BF9A`（与构建输出一致）

**仍未目视确认**：本会话的客户端窗口不呈现画面，以上全部是日志侧机读证据。

## 12.11 拿到真实画面之后（第四轮）

### 终于能看见画面了：让游戏自己截帧

这台机器上从脚本启动的客户端**桌面窗口不合成**（截屏全白），但游戏自身的帧缓冲是好的。
于是加了受系统属性控制的截图钩子：

```
-Dportallink.capture=3        # 进世界后每 3 秒截一张，共 3 张
```

- 26.3：`Screenshot.grab(Minecraft, boolean)`
- 1.21.1：`Screenshot.grab(File, RenderTarget, Consumer)`
- **必须在客户端 tick 里调用**，不能在渲染帧中途调：在 `emitGizmos` 里调会截到「本帧还没画完」的
  缓冲区，HUD 和叠加都不在里面（第一版就踩了这个坑，截出来的图没有 HUD）。
- 启动器加了 `-ExtraJvmArguments` 透传参数。

结果（26.3，禁用 realmbridge 后）：画面里只有**我们一套**叠加 —— HUD `传送门: 1 个 / 已链接 #2 …
→ #1 …`、门框小方框、门上标签，**没有第二层、没有悬空水平面**。

### 26.3 的「双层 + 水平层」定性

`realmbridge-1.0.0.jar` 也在画，它的 `ZoneRenderer` 里有：

```java
double y0 = zone.baseY();                      // baseY = floor(playerY/8)*8+4，按玩家算
Gizmos.rect(v(a,y0), v(b,y0), v(c,y0), v(c,y0), GizmoStyle.fill(fill));   // 水平地板
```

所以 26.3 的两个症状都指向它：双层 = 两套叠加；水平层 = 它的地板（而且随玩家每 8 格跳）。
我们的地板已删（上一轮），并加了「检测到 realmbridge 就警告一次」的诊断。

### 1.21.1 的「下界范围跟着玩家跑」：几何不动，数据会错维度

用夹具把玩家传送进下界，两侧日志对比：

```
15:33:22 主世界 eye=(-19.5, 69.6, -47.5)  group=solo:overworld@0,100,0 half=129 x=-129..130
15:33:26 下界   eye=(  6.5, 59.6,   6.5)  group=solo:overworld@0,100,0 half=129 x=-129..130
```

几何的 `x/z/half` 一格没变（所以不是坐标跟着相机走），但**下界里画的是主世界那扇门的范围**，
且用的是主世界半径 129（下界该是 17）。这正是"看起来乱跑/不固定"的来源：框画在了错误的维度坐标上。

修了两处（两个工程都有）：

1. `ClientPortalLinkState.tick`：维度变化时 `reset()`，不再把上一个维度的快照继续画出来。
2. `RangePlanner.plan`：两条分支都加维度守卫 —— `otherEntries` 只收非本维度条目，
   `entries` 只收本维度条目。陈旧/混合快照再也不会被画成框。

⚠ 仍未定论：夹具里两扇门都在 `x=z=0`（只差 Y），无法用坐标区分维度；而且下界那扇门可能
根本没放下，导致 `entries` 里出现的是主世界门。下一轮要换一个**坐标明显不同**的夹具
（如主世界 320,100,320 / 下界 40,56,40）才能判定守卫是否真的生效。

### 12.12 夹具踩过的坑（省得重踩）

- `/fill` 单次上限 32768 方块。想挖 81×31×81 的洞会静默失败，玩家被埋在下界岩里 → 截图全黑。
- 用 `--uuid` 决定加载哪份 playerdata；换 UUID 会落到世界出生点，而不是上次的位置。
- 沙箱里 `keybd_event` 注入的 F2 到不了 GLFW，截图只能由模组自己做。

## 12.13 第五轮：26.3 已可用截图证明只有一套；1.21.1 还差一处

### 26.3：修好了，有画面证据

`realmbridge-1.0.0.jar` 从 `26.3-NeoForge\mods` 移除（改名 `.jar.disabled`，可随时恢复）后，
用 `-Dportallink.capture=2` 截帧：

```
截图 2026-11-16_15.46.05.png：
  HUD  「传送门: 1 个 / 已链接 #2 86,-62,114 → #1 11,2,14（1）」
  世界 门框小方框 + 门上标签 + 通高线框的两条竖边（左右各一条）
  没有第二个框，没有悬空水平面
```

所以 26.3 的「双层」= realmbridge 叠加，「多余水平层」= 它的 `baseY` 地板 + 我们上一轮已删的地板。

> 关键教训：`Screenshot.grab` 要在**客户端 tick** 里调，不能在渲染阶段中途调。前者拿到上一帧
> 完整画面（含 HUD 与世界叠加），后者拿到画到一半的缓冲（没有 HUD）。
> 另外首次截图要等首次扫描落地（12 s），否则截到的帧里叠加还没画。

### 1.21.1：跨维度数据仍未定论

已加两处守卫（两工程都编译部署通过）：

1. `ClientPortalLinkState.tick` 维度变化时 `reset()`。
2. `RangePlanner.plan` 两条分支都校验 `entry.dimension() == snapshot.sourceDim()`。

但夹具日志仍在**下界**显示：

```
[ranges] eye=(45.5, 59.6, 45.5) ... src=local group=solo:overworld@0,100,0 owner=0,101,0 half=129 x=-129..130
[entries] | overworld@0,100,0=WILL_CREATE->none
```

即：在下界扫描到的是**主世界的门**（0,100,0），按主世界半径 129 画出 257 格宽的框。
两处守卫没有拦住它，说明这个 entry 的 `dimension()` 等于 `sourceDim()` —— 也就是
`PortalSnapshot` 的 `sourceDim` 本身在下界时仍是 overworld，或者 `entries` 里被塞进了它维度条目。

**下一轮的切入点（已缩小到一处）**：在 `PortalScanService.build` 返回前打印
`sourceDim / source.dimension() / other.dimension()` 三者，确认是谁标错了维度。
这一条日志就能定位，不需要再猜。

### 12.14 夹具反复踩坑记录

- `portprobe` 世界是早期生成的，里面残留着 (0,100,0) 那扇旧门；新写的数据包 `init` 在新坐标
  放门**没有生效**（日志里始终只有 0,100,0 那扇）。判断"数据包有没有跑"要看坐标有没有变，
  不能看有没有门。
- `/fill` 单次上限 32768 方块，超了静默失败。
- 截图/传送都要等扫描窗口覆盖到门，否则日志里是空快照。

## 12.15 收尾：1.21.1 下界问题定性并修好（有日志证据）

### 根因（三层，全部实测）

1. **扫描本身是对的**。新增 `[scan]` 一行，直接打出两个列表各属哪个维度：

```
[scan] sourceDim=minecraft:the_nether source=minecraft:the_nether(1 found)
       other=minecraft:overworld(0 found) entries=1 otherEntries=0
```

2. **传送瞬间会画上一维度的快照**（真正的「乱跑」来源）：

```
16:05:46 主世界  group=solo:overworld@0,100,0   half=129
16:05:51 下界    group=solo:overworld@0,100,0   half=129   ← 错帧：在 17 格宽的下界画 257 格框
16:05:54 下界    group=solo:the_nether@40,56,40  half=17    ← 4 秒后自己纠正
```

3. **几何坐标不随玩家移动**（唯一能解释「固定」的判据）：

```
16:05:54 eye=(45.5, 51.6, 45.5)  x=23..58 z=23..57
（之后 eye 被传送到 52,58,52 与 36,58,36，`[ranges]` 未再打印 → 坐标一格没变）
```

### 修法（三道守卫，任一道单独都能挡住）

| 位置 | 改动 |
| --- | --- |
| `ClientPortalLinkState.tick` | `level.dimension()` 变化即 `reset()`，旧快照立刻作废 |
| `RangePlanner.plan` | 两条分支都校验 `entry.dimension() == snapshot.sourceDim()` |
| `PortalWorldRenderer` | 渲染前若 `snapshot.sourceDim() != client.level.dimension()`，直接当空处理 |

第三道是关键：它不依赖扫描何时完成，**任何**跨维度快照都不可能被画出来。

### 修后复测

```
16:10:57 主世界  overworld@0,100,0   half=129
16:11:22 下界    the_nether@40,56,40 half=17     ← 直接正确，中间不再有错帧
```

### 夹具的两个真坑（这次才挖到底）

- **`execute in the_nether run setblock` 在下界区块未加载时必然失败**（"position not loaded"）。
  玩家还在主世界时放门永远放不上。正确顺序：先 `tp` 进下界 → 再 `fill`/`setblock` → 再就位。
- **`gradle runServer` 的 stdin 命令不下发**（`RedirectStandardInput` 被 Gradle 吃掉），
  所以「用控制台放门」这条路不通；能用的只有数据包函数。
- `Server: Address already in use` 会让服务器初始化失败，而日志只显示 `Failed to initialize server`；
  真正的 `BindException` 在 `tmp\srv-out.log` 里，不在 `latest.log`。

## 12.16 第六轮：内部看不到颜色、下界仍在移动

### 两个症状同一个原因：我上一轮加的那条「站在柱体里就不画半透明面」

上一轮为了解决「贴身罩子像在跟着人走」，我加了：

```java
if (config.showZoneWalls && !eyeInside(marker, eye)) { walls(...); }   // 人站里面就不画
```

这条带来两个副作用：

1. **内部看不到颜色**。下界范围只有 17 格宽，玩家几乎永远在里面，所以永远只画线框。
2. **看起来还在移动**。`eyeInside` 是柱体边界的硬判定，人一走过边界，半透明面就整片出现/消失，
   这个"闪"比框本身移动更像"跟着我跑"。

**修法**：删掉这条抑制，墙永远画；并让墙**两面都可见**。

### 26.3 的 `Gizmos.rect` 单绕序只朝一面

`RectGizmo.emit` 对 fill 只发 `primitives.addQuad(a, b, c, d, color)` 一次，一个绕序 ——
从背面看就是剔除掉。所以 26.3 必须每面发**两次、相反绕序**：

```java
wall(...);                       // 一个绕序
rect(dx,dy,dz, cx,cy,cz, bx,by,bz, ax,ay,az);   // 反绕序，内面可见
```

1.21.1 本来就用的 `PortalRenderSupport.quadBothSides`（两个绕序都发），所以那边只需删抑制。

### 实测证据

**26.3（下界，柱体内）** `2026-11-16_17:48:03.png`：

```
[ranges] eye=(11.2, 2.1, 20.9) owner=86,-62,114 half=17 x=-7..28 z=-3..31 walls=both
```

画面：HUD + 门框 + 门上标签 + **整片橙色半透明墙面**（从内部看到）。

**1.21.1（主世界，柱体内）** `2026-11-16_17:57:50.png`：视野被橙色墙面整片覆盖。

**1.21.1（下界，三个位置）** 几何坐标一格未变：

```
18:00:15 eye=(52.5, 59.6, 52.5)  x=23..58 z=23..57 half=17
18:00:24 eye=(36.5, 59.6, 36.5)  x=23..58 z=23..57 half=17    ← 玩家移动 16 格，框不动
```

### 诊断字段变化

`[ranges]` 末尾的 `surface=wall|wire` 换成 `walls=both|off`：现在不再有"画不画"的分支，
只有配置开没开。

### 夹具补遗

- 传送点要抬到安全高度（下界 y≈72 是空区），否则玩家落进熔岩，截图全是死亡界面。
- 加 `gamerule doImmediateRespawn true`，否则死亡界面会一直挡住后续截图。

## 12.17 第七轮：1.21.1 崩溃/卡死的根因（POI 强制加载）+ 墙面的距离衰减

### 崩溃与卡顿：`ensureLoadedAndValid(radius=256)`

用户日志的关键行：

```
[scan] ... （每 1 秒一次）
Can't keep up! Is the server overloaded? Running 3524ms or 70 ticks behind
```

`ServerPortalScanner.scan` 里原本是：

```java
poiManager.ensureLoadedAndValid(level, center, radius);   // radius = hostRadius = 256
```

`hostRadius = max(destinationRadius, 128) + 128 = 256`，而 `ensureLoadedAndValid` 会**同步加载并生成**
半径内的区块 —— 512×512 方块约 1000 个区块，**每次扫描**都做，且跑在服务端线程上。
单次扫描 3.5 秒 → 服务器每 tick 落后 → 表现为卡死/"崩溃"。

**修法**：强制加载半径限幅到 48 格：

```java
private static final int POI_ENSURE_RADIUS = 48;
poiManager.ensureLoadedAndValid(level, center, Math.min(radius, POI_ENSURE_RADIUS));
```

远处的 POI 若尚未加载，`getInSquare` 仍会返回已索引的部分，只是该次结果标为 `complete=false`
（本来就是这个语义）。**搜索半径不变**，所以判定逻辑没有改变。

**实测**（用户存档 `新的世界`，下界）：

```
修前：[scan] ...（无耗时字段，单次 3.5 秒），Can't keep up 出现
修后：[scan] 0 ms | sourceDim=the_nether source=the_nether(1 found) other=overworld(1 found) ...
      Can't keep up 计数 = 0
```

`[scan]` 现在带耗时（毫秒），是这条修复的常驻判据。

### 「范围还在移动」：几何是静止的，缺的是深度线索

同一会话里 `[ranges]` 只打印过一次，说明 marker 从未变化：

```
[ranges] eye=(-32.2, 52.4, -1.6) ... owner=26,-62,46 half=17 x=-14..20 z=-12..23
```

配合上一轮夹具的三点位移（52→36，`x=23..58` 不变），可以确定**框本身不动**。
看起来在动的原因是：四面半透明墙把视野整片包住，又没有远近差异，走动时像一层贴脸的膜。

**修法**：每面墙的不透明度按**相机到该墙的距离**衰减（近处保持原值，远处降到约 1/4），
从而产生视差 —— 走近一面墙它变实，对面那面变淡，读起来就是一个固定的体积。

```java
double fade = Math.min(1.0, distance / WALL_FADE_BLOCKS);   // 24 格
int alpha = round(zoneWallAlpha * (1.0 - 0.75 * fade));
```

26.3 与 1.21.1 都改（26.3 每面单独 `GizmoStyle.fill`，1.21.1 每面单独 ARGB）。

### 另外发现：用户世界里有两对门，两个下界范围互相重叠

```
[ranges] ... columns=2
 | src=other group=overworld@240,-63,-342/... half=17 x=12..47 z=-60..-26
 | src=other group=overworld@241,-63,-207/... half=17 x=12..47 z=-44..-9
```

两扇主世界门映射到下界同一片 x 区间、z 相差十几格，两个框重叠。走动时其中一个可能因为
离开扫描窗口而消失/出现，这也会被读成"在动"。若用户仍反馈移动，先确认是不是这两个框。

## 12.18 第八轮：`#3` 标签跑到范围下方 + 撤掉透明度衰减

### `#3` 在范围下方：标签用的是「门自己的 Y」

`PortalRangeRenderer.areaLabel` 原本：

```java
int y = marker.ownerPos().getY() + 2;     // 门自己的 Y
```

跨维度时会错得很远：下界视角下范围的主人是**主世界门**（本例 y=-62），于是标签被放到
**y=-60**，而那个维度底部是 y=0 —— 数字掉到地底，用户看到的就是"范围下方有个 #3"。

**修法**：把标签 Y 夹进本维度的高度带，并跟随视线：

```java
private static double labelY(RangeMarker marker, Vec3 eye) {
    double min = marker.bottomY() + 2.0;
    double max = marker.topY() - 2.0;
    if (max < min) return min;
    return Math.max(min, Math.min(max, eye.y + 2.0));
}
```

下界带 `y=1..255`、玩家 y≈48.5 → 标签落在 **y≈50.5**，就在视野里。

### 撤掉墙面的距离衰减（它自己会被读成「在动」）

上一轮为了给"贴身膜"加视差，让每面墙的不透明度随相机距离变化。副作用是：**人一走动，
整片墙的明暗就在变**，这本身就会被读成"范围在动"。所以改回**统一不透明度**（`zoneWallAlpha`），
箱子在视觉上彻底不动。

### 未改变的事实：几何确实不随玩家移动

用户日志里三次下界采样，坐标完全一致：

```
19:44:19  eye=(-32.2, 50.8, -1.6)  owner=26,-62,46 half=17 x=-14..20 z=-12..23
19:45:06  eye=(  3.5, 42.6, -0.2)  owner=26,-62,46 half=17 x=-14..20 z=-12..23
（中间穿越过主世界：half=129 x=-102..157，属于另一维度的正确映射）
```

也就是说：玩家在 x 方向移动了 35 格，框一格没动。**如果仍觉得在动，需要区分是"平移"还是"忽隐忽现"，
两者的原因完全不同。**

### 用户日志里其它噪音（与本模组无关）

`swiftmap` 在维度切换时会抛 `ClosedByInterruptException / ClosedChannelException`
（`TilePipeline` 关闭时与 flush 竞争），`ironfarm` 每 3 秒打印扫描统计 —— 这些都不是本模组的输出。
本模组在这些会话中的 WARN/ERROR 计数为 0。

## 12.19 第九轮：`#3` 巨大（已修）与「框水平平移」（待判定）

### `#3` 特别巨大：标签跟着视线走

上一轮把标签 Y 改成 `clamp(eye.y + 2, band)`，本意是"别掉到地底"。副作用：标签在**范围中心**、
高度却贴着眼睛，人一走到中心就等于把字贴到脸上 —— 用户看到的就是"特别巨大的 #3"。

**修法**：标签不再跟随视线，回到「门自己的高度 + 2」，并且只在**该高度属于本维度带**时才画：

```java
private static boolean labelBelongsHere(RangeMarker marker) {
    int ownerY = marker.ownerPos().getY();
    return ownerY >= marker.bottomY() && ownerY <= marker.topY();
}
```

理由：高度不跨维度，跨维度范围的主人高度在这个维度没有意义（下界里主人是 y=-62 的主世界门）。
这类范围的世界内编号不再绘制 —— 顶部状态栏本来就写着 `已链接 #6 … → #4 …`，信息不丢。

### 「框整个水平平移」：几何数据不动，问题在渲染层

用户日志三次采样（位置各不相同，坐标完全一致）：

```
19:44:19  eye=(-32.2, 50.8, -1.6)  half=17  x=-14..20  z=-12..23
19:45:06  eye=(  3.5, 42.6, -0.2)  half=17  x=-14..20  z=-12..23
20:32:31  eye=(-29.0, 48.5, 0.3)   half=17  x=-14..20  z=-12..23
```

即 X 方向移动 35 格、Z 方向 12 格，框的坐标一格未变。**所以问题只可能在渲染层的相机处理。**

已排除/待排除：

| 假设 | 判定 |
| --- | --- |
| 规划器跟随玩家 | 已排除（三次采样坐标一致） |
| 明暗变化被读成移动 | 已排除（本轮撤掉了距离衰减，用户也确认"没有明暗变化"） |
| 陈旧快照 | 已排除（`reset()` + 三维度守卫，进入下界首帧即为 `the_nether`） |
| 相机平移被应用两次 | 待验证。若成立，物体会以**两倍速度远离**，与"跟着走"不符 |

**下一步需要的最小观察**（本环境无法自行走动）：站在框外、不移动、只转视角 ——
若框边缘相对地形（例如岩浆湖岸、荧石柱）保持固定，则渲染是对的，剩余是观感；若框整体随视角
平移滑过地形，则确认是相机矩阵问题，届时改 `PortalWorldRenderer` 的 `translate(-eye)` 为
「不translate + 世界坐标」再测一次即可二分定位。

## 12.20 第十轮：A/B 实测判定「框是否跟着相机走」

### 方法

用夹具把相机放在两个位置、**保持同一朝向**，各截一帧，然后比较叠加层在屏幕上的横向重心：

- A 点 (45, 58, 45)，B 点 (35, 58, 55)，相距约 14 格；
- 截图由模组自己从帧缓冲读回（`-Dportallink.capture=N`），避开桌面合成问题；
- 重心用橙色像素的加权平均（阈值 R>150、G 60..200、B<120、R-B>60）。

结果：

```
21.16.54  orange=46019  centroidX=686.0     ← A 点
21.16.58  orange=45505  centroidX=682.6
21.17.02  orange=45132  centroidX=679.9
21.17.06  orange=45089  centroidX=680.1
21.17.10  orange=45067  centroidX=680.1
21.17.49  orange=50630  centroidX=646.7     ← 传到 B 点后
```

### 结论

相机横移 14 格后，叠加层在屏幕上移动了 **约 33 像素**（686 → 647），期间几何坐标完全没变。

**如果框是黏在相机上的，这个数字应该纹丝不动** —— 因为相对相机的位置不变。它在动，说明顶点确实是在世界坐标里、
按相机做反向偏移的，也就是**框没有跟着相机走**。

（同一位置那几帧有 686→680 的缓慢漂移，是因为夹具把玩家 tp 到空中后在下落，视线高度变化导致透视微移。）

### 那为什么仍然「看起来在平移」

因为在净高度 382 格（`y=-63..319`）、宽度 257 格的通高半透明盒子里，屏幕上只有四片**没有边界**的色块：
穿透地形渲染（`alwaysOnTop`）让它们盖住一切参照物，眼睛找不到任何"静止的坐标"来对比，走动时的透视变化
就被读成墙在滑。26.3 之所以"看起来正常"，是因为它那边框更小、场景不同，观感问题不突出。

### 本轮修法：给墙面钉上世界坐标的竖向导引线

每 8 格一根竖线（`GUIDE_SPACING = 8`），沿四面墙从 band 底部一直到顶部，颜色取边缘色的 1/3：

```java
for (double x = x0 + GUIDE_SPACING; x < x1; x += GUIDE_SPACING) {
    line(x, yDown, z0 → x, yUp, z0);
    line(x, yDown, z1 → x, yUp, z1);
}
```

它们是**固定在世界坐标里**的，走动时能直接看出「墙没动，是我在动」。两个工程都加了。

### 附带修掉的夹具 bug（影响我之前几轮的截图）

`captureIfDue` 原来只判断 `client.level != null`，而**地形加载中 level 已经存在**，于是截图全落在
"正在加载地形"的画面里 —— 这也是前面几轮截图经常对不上日志的原因。现在要求**叠加层已就绪**
（快照非空）才允许截图。

## 12.21 第十一轮：读了 playbook，确认了 D2，并修掉自己造成的回归

### 先修回归：主世界开始动，是我改了配置

为了做 A/B，我把 `manualCameraOffset` 写成了 `false` 并留在实例配置里。这个开关一关，几何就完全不减相机，
于是主世界（相机 XZ 很大）也整体偏移 —— 这就是"以前主世界不移动，现在主世界也动了"。
**已改回 `true` 并部署。**

### playbook 的 D2 条目正好描述这个现象

`references/silent-failures.md` D2「标记位置偏了 / 跟着相机跑 —— 相机平移做了两次或零次」：

| 场景 | 结论 |
| --- | --- |
| 26.x gizmo | 吃世界坐标，**引擎自己减相机**，不要自己减 |
| 1.21.1 `AFTER_ENTITIES` | 姿态栈是单位阵，**必须自己减** |
| 1.21.1 `BEFORE_ENTITIES` | **已经含相机平移**，不要再减 |

判据原文：*打印你提交的坐标，和 `camera.position()` 对比。如果差值等于相机位置，说明你多减了一次。*

**这解释了之前那个矛盾**：如果真多减一次，偏移量恰好等于相机位置 ——
主世界相机 XZ 达数十格，框被推得早已离开视野，所以"看起来不动"；下界相机 XZ 接近 0，
偏移很小，框仍在视野里只是位置偏了，于是"看起来在平移"。两者是同一个 bug 的两种表现，
而我之前把主世界的"不动"误当成了正确。

### 当前实现的对照

本工程用的是 `AFTER_ENTITIES` + 自己减相机，与 D2 表格第二行一致；
`[pose]` 实测 `translation=(0.0, 0.0, 0.0)`、`inCameraSpace=false`，也支持"引擎没有替我们减"。

### 新增常驻诊断（D2 判据）

```
[submit] manualOffset=true first-marker=(x, y, z) cam=(cx, cy, cz)
```

一次一行。若 `first-marker` 与 `cam` 的差恰好等于 `cam`，就是多减了一次；
同时这行会打印开关状态，避免再把"配置改动"误判成"代码改动"。

### 下一步的判定依据

请在下界走动时抓连续几行 `[ranges]`：
若 `owner=` 在几行之间发生变化（例如从 `overworld@26,-63,46` 变成另一扇门），
说明是**门集合随扫描窗口抖动**（用户世界里有两对门，`columns=2` 出现过），
那属于数据层，需要固定锚点；若 `owner=` 始终不变而依旧看着在动，则是渲染层，继续按 D2 排查。

## 12.22 D2 判据实测：相机没有多减，问题在数据层的门集合抖动

### 先说我把配置改坏造成的回归

为做 A/B 我把 `manualCameraOffset` 写成 `false` 并留在实例配置里，导致几何完全不减相机 ——
主世界相机 XZ 大，整体偏移立刻可见，于是"主世界也开始动"。**已改回 `true` 并重新部署。**

### D2 判据的实测输出

```
[pose]   stage=minecraft:after_entities translation=(0.0, 0.0, 0.0) inCameraSpace=false
[ranges] eye=(-16.5, 35.0, -28.6) ... owner=26,-62,46 half=17 x=-14..20 z=-12..23
[submit] manualOffset=true first-marker=(-14, 1.0, -12) cam=(-16.48, 35.00, -28.58)
```

按 playbook `silent-failures.md` D2 的判据：

- 提交的坐标 `(-14, 1, -12)` **就是标记的世界坐标**（与 `[ranges]` 的 `x=-14..20 z=-12..23` 一致）；
- 它与相机 `(-16.48, 35.00, -28.58)` 的差是 `(2.5, -34.0, 16.6)`，**不等于相机位置本身**；
- 姿势栈 `translation=(0,0,0)`，即引擎没有替我们减相机。

⇒ **相机只减了一次，变换是正确的。** 渲染层可以排除。

### 于是只剩数据层：门集合随扫描窗口抖动

用户世界里有两对门（此前出现过 `columns=2`）。扫描窗口以玩家为中心，
走动时窗口边缘的门会进出，`otherCenters` 随之变化，被选中作 owner 的主世界门可能换成另一扇 ——
框就会在两处之间跳，读起来正是"整体平移"。

**缓解**：`RESCAN_DISTANCE` 由 16 提到 **48** 格（原值与 vanilla 的搜索步长相同，导致窗口每走十几格就重算一次）。
重扫间隔仍是 3 秒，扫描成本已降到 0~1 ms，所以放宽位移阈值不会漏更新。

### 下一步的唯一判据

走动时抓连续几行 `[ranges]`：

- `owner=` **发生变化** → 数据层抖动，需要固定锚点（下一轮做滞回：owner 变化后锁定若干秒）；
- `owner=` **始终不变** → 回到渲染层，继续按 D2/D4 查历史副本与提交次数。

## 12.23 按用户指路读 ironfarm 的字节码：相机来源取错了

### 用户给的参考工程

`https://github.com/heiheids/iron-farm-design-wheelchair`，本地实例里就装着
`ironfarm-neoforge-1.21.1-0.53.0.jar`（同版本、同加载器、渲染在真机上正常）。
反编译 `com/heishandashu/ironfarm/neoforge/render/WorldOverlayRenderer`：

```
64: Minecraft.getInstance()
67:   .gameRenderer
70:   .getMainCamera()          ← 相机来源
77:   .getPosition().toVector3f()
86: poseStack.pushPose()
92-107: Vector3f.x/y/z 各一次 fneg      ← translate(-cam.x, -cam.y, -cam.z)
108: poseStack.translate(FFF)
     → renderIcons / renderBedLines / renderPanicLines / renderSpawnBoxes / renderGolemDetectBoxes
200: poseStack.popPose()
204: NeoRenderBuffers.flush()
```

`renderGolemDetectBoxes` 里画的就是「村民检测铁傀儡的范围」：直接取
`VillagerReport.pos()` 的 `BlockPos3.x/y/z`（**绝对世界坐标**）交给 `drawBox`，**不做任何减相机**。

### 结论：结构和我们一致，只有相机来源不同

| 项 | ironfarm | 本工程（改前） |
| --- | --- | --- |
| 阶段 | `AFTER_ENTITIES` | 同 |
| 姿势 | `pushPose → translate(-cam) → 画世界坐标 → popPose` | 同 |
| 相机来源 | **`gameRenderer.getMainCamera()`** | **`event.getCamera()`** |

`fneg` 的三次出现说明它同样是 `-cam`，所以**偏移方向和次数都没有差别**，唯一的差别是**取哪个相机对象**。

### 实测：两个来源确实不是同一个值

同一位置、相邻两次运行：

```
event.getCamera()      → eye=(3.5, 42.6, -0.2)
getMainCamera()        → eye=(4.8, 42.6, -1.2)
```

X/Z 各差约 1.3 / 1.0 格。**减掉一个与引擎实际使用值不同的相机位置，等于给每个顶点加了一个固定偏移**，
而这个偏移随玩家移动而变 —— 正是「整体水平平移、无明暗变化」。

### 修法

`PortalWorldRenderer` 里两处相机读取（`[probe]` 与主渲染路径）全部改为
`client.gameRenderer.getMainCamera()`，与 ironfarm 完全一致。

改后运行：`errs=0`，`[pose] translation=(0,0,0) camera=(4.77, 42.62, -1.24)`、
`[submit] first-marker=(-14, 1.0, -12)` 与 `[ranges] x=-14..20 z=-12..23` 一致。

> 教训：**同一版本、同一渲染阶段、在真机上正常的邻居模组，是最高优先级的参考**。
> 这一次的差异（`event.getCamera()` vs `getMainCamera()`）在文档和推断里都看不出来，只有对比字节码才现形。

## 12.24 第十四轮：加「双约定对照框」，把观感问题变成二选一

### 为什么改用它

前十三轮的结论链：几何数据恒定（日志多次采样）→ D2 判据排除"多减一次" → 相机来源已对齐 ironfarm。
但用户仍报告整体平移，而我在这个环境里**没有可用的输入通道**，无法自己走动观察，
所以再有推理也只是继续猜。

### 做法：同一组棱边，两种相机约定，两种颜色

| | 正常叠加层（橙色） | 对照框（白色） |
| --- | --- | --- |
| 坐标 | 世界坐标 | 坐标里已预减相机 `world - eye` |
| 姿势 | `translate(-eye)` | 不 translate（单位阵） |
| 位置 | `PortalRangeRenderer.edges` | `PortalRangeRenderer.renderReferenceBoxes` |

两种写法在数学上等价：**都正确就完全重合**。若橙色在动、白色不动 → 错在"改姿势"这条；
若白色在动、橙色不动 → 错在"改坐标"这条；若两者一起动 → 两种约定都被引擎再补了一次相机。

配置项 `compareCameraModes`（默认 `true`，纯诊断，验证完可关）：

```json
"compareCameraModes": true
```

实现要点：对照框画在 `popPose()` **之后**，用单位阵 + 预减坐标，走同一条 `RenderType.lines`。

### 本轮实测

```
[pose]   translation=(0.0, 0.0, 0.0)  camera=(4.77, 42.62, -1.24)
[ranges] eye=(4.8, 42.6, -1.2)  owner=26,-62,46  half=17  x=-14..20  z=-12..23
[submit] manualOffset=true  first-marker=(-14, 1.0, -12)  cam=(4.77, 42.62, -1.24)
```

运行无 WARN/ERROR（`errs=0`），截图里橙色墙体的 8 格导引线与白色对照棱边同时出现。

### 待用户回答的一个问题

**走动时，是橙色整体平移、白色整体平移，还是两者一起平移？**

这一个观察能直接定位到"姿势错 / 坐标错 / 两者都被再补一次"，不需要再猜。
