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