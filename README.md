# Portal Link / 传送门链接

Preview Nether portal linkage in Minecraft **26.3 (Fabric)** and **NeoForge** (Minecraft **1.21.1** and **26.3**).

预览下界传送门的链接关系，避免"串门"——传送过去却被送到另一个传送门那边。

## 三个构建目标 / Three build targets

| 目标 | 目录 | 产物 |
| --- | --- | --- |
| Fabric 26.3 | `source\portallink` | `portallink-1.16.4.jar` |
| NeoForge 26.3 | `source\portallink-neoforge-26.3` | `portallink-26.3-neoforge-1.16.4.jar` |
| NeoForge 1.21.1 | `source\portallink-neoforge-1.21.1` | `portallink-1.21.1-neoforge-1.16.4.jar` |

三个目标共用同一套 `core/`（链接算法）、配置、传送门编号与扫描策略，差别只在加载器胶水层与渲染层。
构建方法、版本坐标与实测结论见 `docs\mod_context.md` 第 11 节。

---

作者：**黑山大叔**
主页：<https://space.bilibili.com/19428259>
GitHub：<https://github.com/heiheids>

---

## 这个模组做什么 / What it does

下界传送门的链接规则是"取最近"，而且主世界搜索半径是 128 格、下界只有 16 格（换算后等价于主世界 1024 格）。这就导致：

* 两个相距很远的主世界门，可能都连到同一个下界门；
* 从下界回来时，被送到另一个主世界门旁边。

本模组把这条链接关系直接画出来。

The mod renders the actual linkage: which portal sends you where, and which portals compete for the same destination.

## 渲染内容 / What is rendered

| 元素 | 含义 |
| --- | --- |
| 传送门方框 | 传送门方块实际范围（精确到方块） |
| 大范围立方体 | 该传送门"独占"的搜索范围，同范围内的门会互相抢 |
| 半透明水平面 | 原版搜索判定所在的平面（只按 X/Z 距离，忽略 Y） |
| 绿色/红色/黄色标签 | 已链接 / 会串门 / 将新建传送门 |
| 连线 + 幽灵框 | 对应传送门的位置（换算到当前维度坐标） |
| 左上角列表 | 可选的紧凑状态列表 |

标签颜色含义：

* 🟢 **已链接** —— 双向对称，正常往返；
* 🔴 **会串门** —— 单向链接，回来会落到别的门（有聊天提示）；
* 🟡 **将新建** —— 范围内没有门，原版会新建一个；
* ⚪ **未知** —— 另一维度未加载，无法判断。

## 操作 / Controls

| 按键 | 功能 |
| --- | --- |
| `B` | 开关全部标记 |
| `P` | 开关左上角状态列表 |
| `O` | 打开设置界面 |

命令：

```
/portallink          列出当前范围内的传送门链接
/portallink list     同上
/portallink rescan   重新扫描
/portallink toggle   开关模组
/portallink config   打开设置
```

## 为什么能算准 / Why the prediction matches the game

实现直接对齐原版 26.3 的 `PortalForcer#findClosestPortalPosition`：

1. 源坐标乘以两维度 `coordinateScale` 之比（主世界 1.0 / 下界 8.0）；
2. 目标点按目标维度世界边界裁剪（`WorldBorder#clampToBounds`）；
3. 在目标维度以 **16（去下界）/ 128（回主世界）** 为边长做 X/Z 方形搜索，**完全忽略 Y**；
4. 候选是**传送门方块**而非传送门中心，按三维距离平方排序，Y 升序作为并列条件。

数据来源优先级：

* **单人 / 局域网主机** —— 直接读服务端 POI 索引（`PoiManager`），和传送判定同源，结果精确；
* **多人服务器** —— 退回客户端已加载区块扫描，不触发任何区块加载、不向服务端发包；另一维度通常未加载，因此会明确标注"未知"。

## 配置 / Configuration

`config/portallink.json`，或直接按 `O` 在游戏内调整：

```json
{
  "language": "auto",
  "enabled": true,
  "renderRanges": true,
  "renderLabels": true,
  "renderConnectors": true,
  "renderGhosts": false,
  "alwaysOnTop": true,
  "maxRenderDistance": 256,
  "maxRenderedPortals": 32,
  "showStatusPanel": false,
  "warnOnConflict": true,
  "labelScale": 1.0
}
```

语言支持 `auto`（跟随游戏）/ `zh_cn` / `en_us`，可随时切换，无需重启。默认同时内置中英文。

## 安装 / Installation

1. Minecraft **26.3** + **Fabric Loader ≥ 0.20.0** + **Fabric API**；
2. Java **25**；
3. 把 `portallink-1.0.0.jar` 放进 `mods/`。

仅客户端模组，装在服务器上也不会生效。

## 依赖 / Dependencies

只依赖 **Fabric API**，没有引入任何其他第三方库。

## 测试覆盖 / Test coverage

工程内置 4 个 Fabric 客户端游戏测试，跑真实客户端 + 真实世界，不是 mock：

```powershell
cd E:\Project\portallink
.\tools\build.ps1 gametest
```

| 测试 | 覆盖内容 |
| --- | --- |
| `PortalLinkClientGameTest` | 建一对双维度传送门，断言扫描找到、状态为 `LINKED`、水平距离 ≤ 16；渲染 40 帧不崩；产出截图 |
| `PortalLinkConflictGameTest` | 两个主世界门相距 40 格、抢同一个下界门：近的 `LINKED`、远的 `CONFLICT`，且两者指向同一门下界门；`ConflictNotifier` 确实发出警告 |
| `PortalLinkUiGameTest` | 设置界面能开、**所有控件都在窗口内**（防布局溢出）、HUD 面板存活、`/portallink` 触发新扫描、`/portallink toggle` 翻转开关 |
| `PortalLinkMultiplayerGameTest` | 连接**真实专用服务器**：断言不做 `authoritative` 声称、客户端能扫到已加载区块里的门、另一维度未知时不谎报链接、不误报冲突 |

截图输出在 `build/run/clientGameTest/screenshots/`。

测试里的坐标一律从运行时 `spawn` / `getMinY()` / `getMaxY()` 推导，不写死世界高度（主世界与下界 Y 范围不同）。

## 兼容性 / Compatibility

- 与 Sodium / Iris 等渲染类模组无冲突（本模组不使用 mixin，只挂 Fabric 公开事件）；
- `fabric-api` 为必需依赖；请勿在 `mods/` 同时放多个版本的 fabric-api。

## 源码结构 / Source layout

```
com.heishan.portallink
├── PortalLink                     常量（ID、作者、链接）
├── PortalLinkInit                 服务端入口（仅日志）
├── core                          纯逻辑，不依赖渲染
│   ├── PortalRef                  传送门几何（轴、最小角、宽高）
│   ├── ScannedPortal              传送门 + 方块列表
│   ├── PortalFrameBuilder         方块连通分组 -> 矩形传送门
│   ├── PortalEntry                一个门的链接结果
│   ├── PortalSnapshot             一次扫描的完整结果
│   ├── DimensionPortals           单维度传送门集合
│   ├── LinkStatus                 链接状态枚举
│   ├── LinkageMath                坐标换算 / 搜索半径 / 排序
│   └── LinkageResolver            原版链接算法（纯函数）
├── client
│   ├── PortalLinkClient           客户端入口，负责接线
│   ├── ClientPortalLinkState      扫描结果缓存与调度（服务端线程投递）
│   ├── ConflictNotifier           串门聊天提示（每个门只提示一次）
│   ├── command/PortalLinkCommand  客户端命令
│   ├── gui/                       设置界面、整数/浮点滑块
│   ├── render/                    世界渲染、HUD、配色
│   └── scan/                      服务端 POI / 客户端区块扫描
├── config/PortalLinkConfig        JSON 配置
└── i18n/PortalI18n                语言查找（跟随游戏 + 手动强制）
```

游戏测试源码在 `src/gametest/java/...`，与主源码分开编译，**不会进入发布 jar**。

## 许可证 / License

MIT
