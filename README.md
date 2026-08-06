# MicroTimingReplay (MTR)

一个 Fabric 模组，用于**记录**服务端一段时间内的微时序事件（方块更新、形状更新、计划刻、方块事件、活塞移动、实体移动/生成等），并在之后**逐步回放**——可以前进、后退、跳转到任意一步，并查看每一个事件产生时的 Java 调用栈。

面向红石技术玩家 / 微时序（microtiming）分析场景。

| | |
|---|---|
| Minecraft | `26.1.2` |
| 加载器 | Fabric Loader `>=0.19.3` |
| 依赖 | Fabric API、Java 25 |
| 环境 | 服务端逻辑为主，客户端提供渲染修正 |

---

## 目录

- [核心概念](#核心概念)
- [事件节点一览](#事件节点一览)
- [快速上手](#快速上手)
- [指令总览](#指令总览)
- [Profile 与区域管理](#profile-与区域管理)
- [紫色染料选区工具](#紫色染料选区工具)
- [录制](#录制)
- [回放](#回放)
- [时间轴界面与调用栈](#时间轴界面与调用栈)
- [游戏规则](#游戏规则)
- [数据存放位置](#数据存放位置)
- [注意事项](#注意事项)

---

## 核心概念

**Profile（配置档）**
一次录制的容器。每个 profile 有自己的名字、若干**区域（Area）**、录制到的所有 tick 帧，以及配套的世界备份和调用栈文件。

**Area（区域）**
一个长方体范围 + 所属维度。**只有落在区域内的事件才会被记录**。一个 profile 可以有多个区域，可以跨维度。
如果一个 profile 一个区域都没有，则**不做任何范围过滤**（全世界所有事件都记）——数据量会非常大，不建议这样用。

**Frame / Event / Step**
录制以 tick 为单位切成帧（TickFrame），每帧内是一棵**事件树**：
`阶段(Phase) → 队列(Queue) → 更新(Update) → 叶子事件(setBlock / 实体移动 / ...)`

其中"队列"这一层除了真正的 Queue 事件，还包括**实体运算**（实体运算阶段里的单个实体）和**方块实体运算**（方块实体运算阶段里的单个方块实体）—— 它们在结构上不是队列，但在 `forward/backward queue` 步进时被当作同一层级对待。
回放时这棵树被**展平**成一条线性的动作序列，每个可见节点分配一个递增的 **step 编号**，`jump` / `dump` 指令用的就是这个编号。

**关于时间冻结**
本模组**不会自动冻结或解冻游戏时间**，时间控制权完全在你手里。

- **录制需要冻结。** `/mtr record` 依靠 `stepGameIfPaused` 精确推进指定的刻数。未冻结时世界照常流逝，你录到的是自然运行的时间而非受控步进，指令会给出提示。
- **回放不需要冻结。** 回放的每一步是直接把记录的状态写回世界的，不依赖世界刻推进，冻不冻结都能正确步进。冻结只是为了让周围环境别在你分析的时候继续变化——推荐但非必需。

---

## 事件节点一览

时间轴上的每一行都是一个节点。这里按事件树的层级列出全部类型，以及它们对应 Minecraft 源码里的什么位置。

### 阶段（Phase）— 最外层

一个游戏刻里的大块执行阶段，按 vanilla 的实际执行顺序排列。颜色统一为**浅紫**。

| 时间轴显示 | 内部名 | 含义 | 对应 vanilla |
|---|---|---|---|
| 维度运算阶段 | `LevelTickPhase` | 一个维度的完整 tick，最外层容器 | `MinecraftServer.tickChildren` → `ServerLevel.tick()` |
| 区块运算 | `ChunkTickPhase` | 区块加载/卸载与区块随机事件的驱动 | `ServerChunkCache.tick(...)` |
| 冰与雪 | `IceAndSnowPhase` | 结冰、积雪、降水判定 | `ServerLevel.tickPrecipitation(...)` |
| 随机刻 | `RandomTickPhase` | 随机刻（作物生长、树叶衰减等） | `BlockState.randomTick(...)` |
| 计划刻阶段 | `ScheduledTickPhase` | 计划刻队列的执行 | `ServerLevel.tick` 里的 `LevelTicks.tick(...)` |
| 方块事件阶段 | `BlockEventPhase` | 方块事件队列的执行| `ServerLevel.runBlockEvents()` |
| 实体运算阶段 | `EntityTickPhase` | 遍历所有实体做 tick | `EntityTickList.forEach(...)` |
| 方块实体阶段 | `BlockEntityPhase` | 遍历所有方块实体做 tick | `Level.tickBlockEntities()` |
| 龙战 | `DragonFightPhase` | 末影龙战斗逻辑（仅末地） | `EnderDragonFight.tick()` |
| 玩家运算阶段 | `PlayerTickPhase` | 处理玩家连接与收到的数据包，玩家操作引发的方块变更在此 | `MinecraftServer.tickConnection()` |
| 异步事件阶段 | `AsyncTaskPhase` | 服务端处理异步任务 | `MinecraftServer.waitUntilNextTick()` |

> 阶段本身没有坐标，因此**不会在世界里生成标记方块**。

### 队列层（Queue）— 单个待办项

阶段内部的一个具体待办项。`forward/backward queue` 就在这一层停。颜色**黄**（有子事件）或**红**（空，说明什么都没触发）。

| 时间轴显示 | 内部名 | 含义 | 对应 vanilla |
|---|---|---|---|
| 执行方块计划刻 | `ExecuteBlockTick` | 执行某个方块的一次计划刻 | `ServerLevel.tickBlock(...)` |
| 执行流体计划刻 | `ExecuteFluidTick` | 执行某个流体的一次计划刻 | `ServerLevel.tickFluid(...)` |
| 执行方块事件 | `ExecuteBlockEvent` | 执行某个方块事件 | `ServerLevel.doBlockEvent(...)` |
| 实体运算 | `entityTick` | **单个**实体的 tick | `ServerLevel.tickNonPassenger(...)` |
| 方块实体运算 | `blockEntityTick` | **单个**方块实体的 tick | `TickingBlockEntity.tick()` |

> 后两者结构上不是 vanilla 的队列，但在步进语义上和队列同级，所以归入这一层。详见[核心概念](#核心概念)。

### 更新层（Update）

方块更新的传播。颜色：邻居更新**红**，形状更新**青**。

| 时间轴显示 | 内部名 | 含义                                         | 对应 vanilla |
|---|---|--------------------------------------------|---|
| 邻居更新 | `NeighbourUpdate` | 一次 neighbor update（`neighborChanged`），邻居更新 | `NeighborUpdater.executeUpdate(...)` |
| 形状更新 | `ShapeUpdate` | 一次 shape update（`updateShape`），状态更新        | `NeighborUpdater.executeShapeUpdate(...)`、`CollectingNeighborUpdater.shapeUpdate(...)` |

### 动作事件 — 真正改变世界的那些

| 时间轴显示 | 内部名 | 颜色 | 含义                                                                                                  | 对应 vanilla |
|---|---|---|-----------------------------------------------------------------------------------------------------|---|
| 放置方块 / 放置方块(失败) | `setBlock` | 绿 / 红 | 一次 `setBlock`。**这是个作用域节点** ， 它触发的方块更新是它的子事件。悬停可看方块状态 diff 与 flag 位分解；失败（返回 `false`）时显示为红色，且回放时不会被重现 | `Level.setBlock(pos, state, flags, limit)` |
| 添加方块事件 | `addBlockEvent` | 黄 | 把一个方块事件**加入队列**                                                                                     | `Level.blockEvent(...)` |
| 添加计划刻 | `addScheduleTick` | 黄 | 把一个计划刻**加入队列**。悬停可看触发刻、优先级、子顺序                                                                      | `LevelTicks.schedule(...)` |
| 发布游戏事件 | `postGameEvent` | 深青 | 发出一个 game event（幽匿循声系统的信息）                                                                          | `ServerLevel.gameEvent(...)`、`GameEventDispatcher` |
| 收到游戏事件 | `receivedGameEvent` | 深青 | 幽匿感测体接收到振动。会同时标出**振动源**位置                                                                           | `VibrationSystem.Ticker.receiveVibration` |
| 活塞生成 / 活塞移除 | `movingPiston` | 浅紫 | 移动中的活塞方块实体出现或消失                                                                                     | `Level.setBlockEntity(...)`、`PistonMovingBlockEntity.finalTick` |
| 活塞移动 | `movingPistonTick` | 浅紫 | 移动中的活塞每一刻的推进进度，回放时用方块展示实体模拟                                                                         | `PistonMovingBlockEntity.tick(...)` |
| 实体生成 / 实体移除 | `entitySpawn` | 绿 / 深红 | 实体进入或离开录制区域（也包括真正的生成与死亡）                                                                            | `ServerLevel.addEntity`、`Entity.onRemoval`，以及跨区域边界移动 |
| 实体移动 | `entityMove` | 金 | 一次实体位移，记录前后坐标与速度                                                                                    | `Entity.move(MoverType, Vec3)` |
| 无形步骤 | `invisibleStep` | 白 | `BlockPosEvent` 基类的兜底类型，正常不该出现；出现了通常说明有人在酒吧点炒饭                                                      | — |

### 关于颜色

时间轴文本、BossBar、以及世界里的玻璃标记框共用同一套颜色，来自事件的 `getColor()`：

| 颜色 | 含义 |
|---|---|
| 浅紫 | 阶段 / 活塞 |
| 黄 | 队列项、入队动作 |
| 红 | 邻居更新；或**空的**队列/方块实体运算；或**失败的** setBlock |
| 青 | 形状更新 |
| 绿 | 成功的 setBlock、实体生成 |
| 金 | 实体运算 / 实体移动 |
| 深青 | 游戏事件（发布 / 接收） |
| 灰 | 正在**退出**某个作用域（EXIT 节点） |

节点末尾的 `∅（空集合符号）` 表示这是个**空节点** ,它没有产生任何子事件。

---

## 快速上手

```
/tick freeze
```
```
/mtr profile create test
```
```
/mtr profile area add test 100 60 100 120 80 120
```
```
/mtr record test 20
```
录制会在 20 gt 后自动停止（也可以 `/mtr record stop` 手动停止）。

```
/mtr replay start test
```
```
/mtr replay forward steps 1
```
```
/mtr replay screen
```
```
/mtr replay stop
```

---

## 指令总览

所有指令的根节点是 `/mtr`，需要 **管理员权限**（`COMMANDS_ADMIN`）。

```
/mtr
├─ profile
│  ├─ create <name>
│  ├─ delete <name>
│  ├─ info   <name>
│  └─ area
│     ├─ add    <name> <pos1> <pos2> [area_name]
│     ├─ remove <name> <area_name>
│     └─ modify
│        ├─ pos    <name> <area_name> <pos1> <pos2>
│        └─ rename <name> <area_name> <new_name>
├─ record
│  ├─ <name> <ticks>
│  ├─ stop
│  └─ clear <name>
└─ replay
   ├─ start <name>
   ├─ stop
   ├─ subscribe
   ├─ unsubscribe
   ├─ screen   [page]
   ├─ forward  <unit> [amount]
   ├─ backward <unit> [amount]
   ├─ jump     <step>
   ├─ dump     <step>
   └─ auto
      ├─ stop
      └─ <direction> <unit> <delay> <steps>
```

---

## Profile 与区域管理

### `/mtr profile create <name>`
新建一个 profile。名字只能使用 `A-Z a-z 0-9 _ . + -`（Brigadier 的 word 参数）。
若同名 profile 已存在则失败。创建后会自动执行一次 `info`（即进入区域标记显示状态）。

### `/mtr profile delete <name>`
删除 profile，同时删除它的世界备份和调用栈文件。**不可撤销。**

### `/mtr profile info <name>`
**开关式指令**——这是本模组交互的核心：

- 第一次执行：把该 profile 设为"当前正在查看的 profile"，在当前维度用玻璃方块展示实体标出所有区域，并打印统计信息（创建时间、录制 tick 数、帧数、区域数）。**同时激活[紫色染料选区工具](#紫色染料选区工具)。**
- 对同一个 profile 再执行一次：清除区域标记，退出查看状态，选区工具随之失效。

> 该状态是**全服唯一的**（静态变量），多人同时使用会互相覆盖。

### `/mtr profile area add <name> <pos1> <pos2> [area_name]`
给 profile 添加一个长方体区域，维度取自**指令执行者所在的维度**。
`pos1` / `pos2` 支持相对坐标（`~ ~ ~`）和局部坐标，但**必须是已加载的方块**。
`area_name` 省略时自动分配一个递增数字编号（`1`、`2`、`3`…）。名字重复则失败。

### `/mtr profile area remove <name> <area_name>`
删除指定区域。`area_name` 有 Tab 补全。

### `/mtr profile area modify pos <name> <area_name> <pos1> <pos2>`
修改已有区域的两个角点坐标（维度不变）。

### `/mtr profile area modify rename <name> <area_name> <new_name>`
重命名区域。新名字已被占用则失败。

---

## 紫色染料选区工具

在 `/mtr profile info <name>` 处于**激活状态**时，手持**紫色染料（Purple Dye）**：

| 操作 | 效果 |
|---|---|
| 左键点方块 | 设置 **Pos 1** |
| 右键点方块 | 设置 **Pos 2** |
| **潜行 + 右键** | 确认并保存当前选区为一个新区域（自动编号） |
| **潜行 + 左键** | 清空当前选区 |

只设置了一个点时，另一个点会**实时跟随你的准星**（4 格距离内取命中方块，否则取视线终点），并用半透明方块展示实体动态预览选区。

---

## 录制

### `/mtr record <name> <ticks>`

开始录制。做的事情：

1. 若该 profile **此前没有任何录制数据**（`ticksRecorded == 0`），先对所有区域做一次**世界快照备份**（方块 + 方块实体 + 非玩家实体），存为 `<name>_record.dat`。这是之后回放时恢复现场的基准。
2. 扫描所有区域内已存在的非玩家实体，记录为初始 `EntitySpawn` 事件。
3. 推进游戏 `<ticks>` 个刻（`stepGameIfPaused`）。
4. 到达目标 tick 后在服务端 tick 末尾**自动停止**并保存。

> **重要**：如果此前已经录过一段，再次执行 `record` 会**接着往后追加**，而不是从头覆盖。想从头来请先 `/mtr record clear <name>`。

> **重要**：请先 `/tick freeze`。未冻结时世界不会被本指令推进，录制虽然照常开始并会按 `<ticks>` 到点自停，但录到的是自然流逝的时间而非受控步进。此时指令会额外给出一条提示。

### `/mtr record stop`
手动停止录制，写入 profile 与调用栈文件。

### `/mtr record clear <name>`
清空该 profile 的所有帧数据、把 `ticksRecorded` 归零，并删除世界备份。区域配置保留。
该 profile 正在录制或回放中时拒绝执行。

---

## 回放

### `/mtr replay start <name>`

1. 把**当前**区域内的世界状态备份为 `<name>_replay.dat`（用于结束回放时还原你的现场）。
2. 用 `<name>_record.dat` 把区域还原到**录制开始时**的状态。
3. 把事件树展平成线性动作序列，加载调用栈文件。
4. 创建 BossBar 并自动让执行者订阅。

此时光标停在第 0 步，用下面的指令推进。

### `/mtr replay stop`
还原 `_replay` 备份（把你的现场还回来），清除所有标记实体与回放实体，关闭所有订阅者的 BossBar 与侧边栏。
可以由玩家、控制台或命令方块执行。

### `/mtr replay forward <unit> [amount]`
### `/mtr replay backward <unit> [amount]`

按指定**单位**前进 / 后退。`amount` 省略时为 `1`。

| unit | 含义 |
|---|---|
| `ticks` | 按**游戏刻**推进 `amount` 刻，执行这期间的所有事件 |
| `steps` | 推进 `amount` 个**可见步**（受 `step_ignore_updates` / `step_ignore_exiting` 影响） |
| `phase` | 推进到**下一个阶段**（Phase）边界 |
| `queue` | 推进到**下一个队列**边界。除了 Queue 事件本身（方块事件 / 计划刻 / 流体刻执行），**实体运算**与**方块实体运算**也按队列对待 |
| `updates` | 推进到**下一个更新**（Update，如邻居更新 / 形状更新）边界 |

后退时会**逆向应用**每个事件（setBlock 还原成旧状态、实体生成还原成移除，等等）。

> 拼错的 `unit` 会被拒绝并提示，不会误跑。请依赖 Tab 补全。

### `/mtr replay jump <step>`
直接跳转到指定的 **step 编号**（时间轴界面里 `[#N↗]` 显示的那个数字）。向前或向后都可以，模组会自动逐个应用/回滚中间的事件。

### `/mtr replay auto <direction> <unit> <delay> <steps>`
自动步进：每隔 `<delay>` 个服务端刻，朝 `<direction>`（`forward` / `backward`）方向按 `<unit>` 走一步，共走 `<steps>` 步。
走到头（某一步没能推进）会提前结束。

### `/mtr replay auto stop`
停止自动步进。

### `/mtr replay subscribe` / `unsubscribe`
订阅 / 取消订阅回放的 **BossBar** 和**侧边栏时间轴**。
BossBar 显示：`Replay [profile] Tick: 当前/总计 | Step: 当前/总计 | 当前所处的阶段/队列/更新`，颜色随当前事件类型变化。

---

## 时间轴界面与调用栈

### `/mtr replay screen [page]`
打开**对话框（Dialog）形式的时间轴**，每页 40 条。每行包含：

- `[TickN]` — 该事件所属的游戏刻，换刻处有分隔线
- 缩进 + `▶`（进入一个父级作用域）或 `→`（叶子事件）
- 事件描述，**悬停可查看该事件的完整信息**（坐标、维度、方块状态 diff、setBlock 的 flag 位分解等）
- 当前光标所在的那一行会**加粗 + 下划线**
- 行尾 `[$]` — 点击查看该步的**调用栈**
- 行尾 `[#N↗]` — 点击**跳转到该步**

底部有 `◀ Prev` / `Next ▶` / `✕ Close` 按钮。

### `/mtr replay dump <step>`
打开指定 step 的 **Java 调用栈**对话框。栈内容按包名 / 类名 / 方法名 / 文件行号分色显示，放在一个多行文本框里方便**全选复制**；标题行支持点击复制到剪贴板、悬停预览前 20 行。

调用栈在**录制时**由每个事件构造时抓取，随 profile 一起保存到 `mtr_stacktrace/<name>.dat`。模组自身的栈帧和 mixin 合成方法会被过滤掉。

---

## 游戏规则

全部位于 `/gamerule` 的 **MISC** 分类下，均为布尔值。

| 游戏规则 | 默认      | 作用                                                  |
|---|---------|-----------------------------------------------------|
| `microtimingreplay:skip_empty_phase` | `true`  | 录制时丢弃**没有产生任何子事件**的阶段节点                             |
| `microtimingreplay:skip_empty_queue` | `true`  | 录制时丢弃**空的队列节点**                                     |
| `microtimingreplay:skip_empty_update` | `true`  | 录制时丢弃**空的更新节点**（这类节点数量极多，建议保持开启）                    |
| `microtimingreplay:skip_empty_entity_tick` | `false` | 录制时丢弃**没有产生任何子事件的实体运算**（若场景里有实体，建议关闭）               |
| `microtimingreplay:skip_empty_block_entity_tick` | `true`  | 录制时丢弃**没有产生任何子事件的方块实体运算**                           |
| `microtimingreplay:step_ignore_updates` | `true`  | `forward/backward steps` 时**跳过** Update 类节点，不把它算作一步 |
| `microtimingreplay:step_ignore_exiting` | `true`  | `forward/backward steps` 时**跳过**"退出作用域"节点           |
| `microtimingreplay:skip_delta_changes` | `true`  | 回放渲染时**只标记当前这一步**；关闭后会把本次跨越的所有位置一起标出来（做大跨度跳转时便于看全貌） |

前五条（`skip_empty_*`）影响**录制产物的体积**，改动只对之后的录制生效。后三条只影响**回放时的手感与显示**，随时可改。

---

## 数据存放位置

均位于 **Fabric 配置目录**（单人为 `.minecraft/config/`，服务端为 `config/`）：

| 路径 | 内容 |
|---|---|
| `config/mtr_profiles/<name>.dat` | profile 本体：区域定义 + 全部事件帧（压缩 NBT） |
| `config/mtr_backups/<name>_record.dat` | 录制起点的区域世界快照 |
| `config/mtr_backups/<name>_replay.dat` | 回放开始前你的现场快照 |
| `config/mtr_stacktrace/<name>.dat` | 每个 step 对应的调用栈 |

> 这些目录是**全局的，不区分存档**。不同世界里的同名 profile 会互相覆盖；在 B 世界里回放 A 世界录的 profile，会把 A 的方块写进 B 的相同坐标。请给 profile 起有区分度的名字。

---

## 注意事项

- **录制前先 `/tick freeze`。** 模组不会自动冻结或解冻时间。回放不受影响，未冻结也能正确步进。
- **回放会真实修改世界。** `replay start` 会把区域还原到录制起点，`replay stop` 再还原回来。若在回放过程中**服务器崩溃或被强杀**，`_replay` 备份不会被自动还原，你的现场会停留在录制起点的状态——此时可以重新 `/mtr replay start` 再正常 `stop` 一次来恢复。
- **区域内的非玩家实体会被备份与还原。** 还原时先清空区域内实体，再从备份重建。
- **无区域 = 不过滤。** 没有区域的 profile 会记录全世界的事件，请谨慎。
- `subscribe`、`unsubscribe`、`screen`、`dump` **必须由玩家执行**（它们要么发给玩家一个界面，要么绑定到玩家）。其余指令控制台与命令方块均可执行。
- `/mtr profile info` 的"当前查看中"状态是全服共享的单一变量，多人同时操作会互相干扰。

---

## 许可证

见 [LICENSE](LICENSE)。
