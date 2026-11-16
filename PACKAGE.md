# Portal Link 1.16.4 — 发布包说明 / Release Notes

作者 / Author：**黑山大叔** · 主页 <https://space.bilibili.com/19428259> · GitHub <https://github.com/heiheids>

一个 Minecraft **26.3 Fabric** 客户端模组：预览下界传送门的链接关系，避免"串门"——传送过去却被送到另一个传送门那边。

A Minecraft **26.3 Fabric** client mod that previews how Nether portals link across dimensions, so you are never routed to the wrong portal.

---

## 中文说明

### 它解决什么问题

原版按"就近搜索"决定你从哪扇门出来：主世界坐标 ÷8 落在下界，再在 **16 格方形**里找最近的已有门。
门一多就会出现"串门"——你从 A 门进，却从别人的 C 门出。

本模组把这个判定过程可视化：**每扇门在对面维度能"认领"多大范围、它实际连到哪扇门、谁在抢同一扇门**。

### 功能

| 功能 | 说明 |
| --- | --- |
| 认领范围 | 半透明通高柱体，标示"在此范围内新建的门会连到这扇门" |
| 配对配色 | 一整个链接配对共用一种颜色；范围、门框、标签、列表同色 |
| 串门告警 | 单向链接用固定红色并单独告警，避免与身份色混淆 |
| 传送门编号 | `#1 #2 #3`…，跨重扫、跨维度、跨重启稳定，不用记坐标 |
| 门标签 | 门上方两行：`#N 状态` 与 `→ #M x,y,z（距离）` |
| 状态列表 | 左上角紧凑列表，同一配对同色 |
| Mod Menu | 模组列表里显示中文名称与简介 |
| 中英双语 | 跟随游戏语言，可手动强制 |

### 按键

**只有一个键：`P`** —— 总开关，世界渲染与左上角列表一起开关。
设置界面从 **Mod Menu** 进入（模组列表 → Portal Link → 设置）。

### 安装

1. Minecraft **26.3** + **Fabric Loader ≥ 0.20.0** + **Fabric API**；
2. 必须使用 **Java 25**；
3. 把 jar 复制到 `.minecraft/mods/`；
4. 启动游戏，走到任意下界传送门旁边。

### 发布物校验

| 项目 | 值 |
| --- | --- |
| jar | `portallink-1.16.3.jar` |
| 大小 | 100,269 字节 |
| SHA256 | `E1C7C469F3BE4BAC8E0A6472B886F15F843DC4B0BF97D2E13761FCF61F70BCA2` |
| 构建环境 | MC 26.3 / Fabric Loader 0.20.1 / Fabric API 0.163.0+26.3 / Loom 1.18.4 / Java 25 |
| 游戏测试 | 5 个客户端游戏测试全部通过 |
| 语言键 | 中/英各 57 条，键集合对齐 |

### 已知限制（如实记录）

1. **多人服务器**下客户端读不到服务端 POI，也加载不了另一维度，因此另一维度通常显示"将新建/未知"——
   诚实地降级，**不伪造链接、不误报串门**。
2. **单人 / 局域网主机**走服务端 POI，与游戏实际传送判定同源，精确。
3. 搜索半径严格对齐原版：去下界 16 格、回主世界 128 格，**在 X/Z 方形内搜索，Y 参与排序但不设边界**。
4. 范围额外包含**接触面余量** `门宽/2 + 0.3` 格：原版由碰撞箱相交触发传送，玩家可站在门外 0.3 格。
5. 形状是**方形**，不是圆柱——原版搜索区是方形，门户面与玩家碰撞箱都是轴对齐矩形，
   矩形⊕方形仍是矩形，数学上不会产生圆。

---

## English

### What it solves

Vanilla picks your exit portal by nearest search: overworld coordinates ÷8 land in the Nether, then the
closest existing gate inside a **16 block square** wins. With several gates around, that means you can
enter portal A and come out of someone else's portal C.

This mod visualises that decision: **how much area each gate claims in the other dimension, which gate
it actually links to, and who is competing for the same gate.**

### Features

| Feature | Notes |
| --- | --- |
| Claim area | Translucent full height column: "a gate built inside this area links to this gate" |
| Pairing colours | One colour per link pair; area, frame, label and list all match |
| Link-steal warning | One-way links use a fixed red plus a chat warning, never confused with an identity colour |
| Portal numbers | `#1 #2 #3`… stable across rescans, dimensions and restarts — no coordinate memorising |
| Portal labels | Two lines above each gate: `#N status` and `→ #M x,y,z (distance)` |
| Status list | Compact top-left list, tinted per pairing |
| Mod Menu | Chinese and English name/description |
| Bilingual | Follows the game language, can be forced |

### Controls

**One key only: `P`** — the master switch, hiding and showing the world rendering *and* the top-left
list together. Settings live in **Mod Menu** (mod list → Portal Link → settings).

### Install

1. Minecraft **26.3** + **Fabric Loader ≥ 0.20.0** + **Fabric API**;
2. **Java 25** is required;
3. Copy the jar into `.minecraft/mods/`;
4. Start the game and walk up to any Nether portal.

### Build from source

```powershell
cd E:\Project\portallink
.\tools\build.ps1 build      # output: source\portallink\build\libs\portallink-1.16.3.jar
.\tools\build.ps1 gametest   # real client + real world + real dedicated server
```

`tools\build.ps1` locates JDK 25 and the in-project Gradle itself; no `JAVA_HOME` setup needed.
The source tree ships without `build/`, `.gradle-home/` and `toolchain/`; the first build downloads
about 360 MB of dependencies. If `services.gradle.org` is unreachable the wrapper already points at a
mirror, see `docs\mod_context.md` §1.

### Known limitations (recorded honestly)

1. On a **multiplayer server** the client cannot read server POIs or load the other dimension, so the
   other side usually shows "new portal / unknown". It degrades honestly: **no fabricated links and no
   false link-steal warnings.**
2. On **singleplayer / LAN host** the server POI is used, i.e. the same data the game itself teleports
   with, so results are exact.
3. Search radius matches vanilla exactly: 16 blocks into the Nether, 128 blocks back into the overworld,
   **searched as an X/Z square; Y takes part in ranking but has no bound**.
4. Ranges include a **contact margin** of `width/2 + 0.3` blocks: vanilla triggers a teleport through
   bounding-box intersection, so a player can stand 0.3 blocks outside the frame.
5. The shape is a **square, not a cylinder** — vanilla searches a square, the portal plane and the
   player's box are axis aligned rectangles, and rectangle ⊕ square is still a rectangle.

---

## 包内结构 / What is in the package

```
portallink-1.16.3/
├── PACKAGE.md                      本文件（中文 + English）
├── README.md                       使用说明（中英双语）/ usage (bilingual)
├── LICENSE                         许可证 / licence
├── mod/
│   └── portallink-1.16.3.jar       可直接放入 mods// drop into mods/
├── docs/
│   ├── mod_context.md              技术文档：环境事实、已验证 API、验证纪律、渲染踩坑
│   ├── 运行失败复盘.md              开发过程失败归因与改进规则 / failure retrospective
│   ├── 文件读取与索引规范.md        工程读取策略与索引结构
│   └── INDEX.md                    全部文件索引（路径 / 行数 / 用途 / 关键符号）
├── 下界门映射工程备份.txt            逐次改动记录（含每次操作前的备份说明）
└── source/
    └── portallink/                 完整可构建源码工程 / full buildable source project
        ├── src/main/               模组源码与资源 / sources and resources
        ├── src/gametest/           5 个客户端游戏测试（不进入发布 jar）/ tests
        ├── build.gradle            Loom 1.18.4
        ├── settings.gradle
        ├── gradle.properties       Minecraft / loader / API versions
        └── gradlew, gradlew.bat, gradle/wrapper/
```

jar 自检要点 / jar self-check：

- 含 `fabric.mod.json`、`assets/portallink/icon.png`、`assets/portallink/lang/{en_us,zh_cn}.json`；
- **不含** `com/heishan/portallink/gametest/`（测试类不进发布包）；
- `environment: client`，纯客户端模组 / client-only.

---

## 反馈 / Feedback

- 主页 / Home：<https://space.bilibili.com/19428259>
- GitHub：<https://github.com/heiheids>

问题反馈请附 `logs/latest.log` 与截图，并说明是单人还是服务器环境。
When reporting a problem please attach `logs/latest.log` and a screenshot, and say whether you are in
singleplayer or on a server.
