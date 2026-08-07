# MicroTimingReplay (MTIR)

A Fabric mod for **recording** micro-timing events on the server over a period of time (block updates, shape updates, scheduled ticks, block events, piston movements, entity movement/spawning, etc.) and later **step-by-step replaying** them — you can go forward, backward, jump to any step, and inspect the Java call stack at the moment each event was produced.

---

## Table of Contents
- [Core Concepts](#core-concepts)
- [Event Node Overview](#event-node-overview)
- [Quick Start](#quick-start)
- [Command Overview](#command-overview)
- [Profile & Area Management](#profile--area-management)
- [Purple Dye Selection Tool](#purple-dye-selection-tool)
- [Recording](#recording)
- [Replay](#replay)
- [Timeline UI & Call Stack](#timeline-ui--call-stack)
- [Game Rules](#game-rules)
- [Data Storage Locations](#data-storage-locations)
- [Notes](#notes)

---

## Core Concepts

**Profile**  
A container for one recording session. Each profile has its own name, one or more **Areas**, all recorded tick frames, plus associated world backups and call-stack files.

**Area**  
An axis-aligned bounding box + the dimension it belongs to. **Only events that fall inside an area are recorded**. A profile can have multiple areas and can span dimensions.

If a profile has no areas at all, **no spatial filtering is applied** (every event in the entire world is recorded) — the data volume becomes enormous; this is not recommended.

**Frame / Event / Step**  
Recording is sliced into frames by game tick (TickFrame). Inside each frame is an **event tree**:

`Phase → Queue → Update → Leaf events (add scheduled tick / entity move / ...)`

---

## Event Node Overview

Every row on the timeline is a node. Below are all node types ordered by the levels of the event tree, together with the corresponding locations in Minecraft source code.

### Phase — outermost layer
Major execution phases inside one game tick, ordered according to vanilla’s actual execution order. Color is uniformly **light purple**.

| Timeline display | Internal name | Meaning | Corresponding vanilla |
|---|---|---|---|
| Dimension tick phase | `LevelTickPhase` | Complete tick of one dimension, outermost container | `MinecraftServer.tickChildren` → `ServerLevel.tick()` |
| Chunk tick | `ChunkTickPhase` | Chunk load/unload and chunk random events | `ServerChunkCache.tick(...)` |
| Ice & snow | `IceAndSnowPhase` | Freezing, snow accumulation, precipitation checks | `ServerLevel.tickPrecipitation(...)` |
| Random tick | `RandomTickPhase` | Random ticks (crop growth, leaf decay, etc.) | `BlockState.randomTick(...)` |
| Scheduled tick phase | `ScheduledTickPhase` | Execution of the scheduled-tick queue | `LevelTicks.tick(...)` inside `ServerLevel.tick` |
| Block event phase | `BlockEventPhase` | Execution of the block-event queue | `ServerLevel.runBlockEvents()` |
| Entity tick phase | `EntityTickPhase` | Iterate all entities and tick them | `EntityTickList.forEach(...)` |
| Block entity phase | `BlockEntityPhase` | Iterate all block entities and tick them | `Level.tickBlockEntities()` |
| Dragon fight | `DragonFightPhase` | Ender Dragon fight logic (End dimension only) | `EnderDragonFight.tick()` |
| Player tick phase | `PlayerTickPhase` | Process player connections and received packets; block changes caused by player actions occur here | `MinecraftServer.tickConnection()` |
| Async task phase | `AsyncTaskPhase` | Server processing of asynchronous tasks | `MinecraftServer.waitUntilNextTick()` |

> Phases themselves have no coordinates, therefore **no marker blocks are spawned in the world**.

### Queue layer — individual pending items
A concrete pending item inside a phase. `forward/backward queue` stops at this layer. Color is **yellow** (has children) or **red** (empty — nothing was triggered).

| Timeline display | Internal name | Meaning | Corresponding vanilla |
|---|---|---|---|
| Execute block scheduled tick | `ExecuteBlockTick` | Execute one scheduled tick of a block | `ServerLevel.tickBlock(...)` |
| Execute fluid scheduled tick | `ExecuteFluidTick` | Execute one scheduled tick of a fluid | `ServerLevel.tickFluid(...)` |
| Execute block event | `ExecuteBlockEvent` | Execute one block event | `ServerLevel.doBlockEvent(...)` |
| Entity tick | `entityTick` | Tick of a **single** entity | `ServerLevel.tickNonPassenger(...)` |
| Block entity tick | `blockEntityTick` | Tick of a **single** block entity | `TickingBlockEntity.tick()` |

### Update layer
Propagation of block updates. Colors: neighbor update **red**, shape update **cyan**.

| Timeline display | Internal name | Meaning | Corresponding vanilla |
|---|---|---|---|
| Neighbor update | `NeighbourUpdate` | One neighbor update (`neighborChanged`) | `NeighborUpdater.executeUpdate(...)` |
| Shape update | `ShapeUpdate` | One shape update (`updateShape`) | `NeighborUpdater.executeShapeUpdate(...)`, `CollectingNeighborUpdater.shapeUpdate(...)` |

### Action events

| Timeline display | Internal name | Color | Meaning | Corresponding vanilla |
|---|---|---|---|---|
| Place block / Place block (failed) | `setBlock` | Green / Red | One `setBlock`. **This is a scope node** — the block updates it triggers are its children. Hover to see block-state diff and flag-bit breakdown; when it returns `false` it is shown in red and is **not** re-applied during replay | `Level.setBlock(pos, state, flags, limit)` |
| Add block event | `addBlockEvent` | Yellow | Enqueue a block event | `Level.blockEvent(...)` |
| Add scheduled tick | `addScheduleTick` | Yellow | Enqueue a scheduled tick. Hover to see trigger tick, priority and sub-order | `LevelTicks.schedule(...)` |
| Post game event | `postGameEvent` | Dark cyan | Emit a game event (sculk sensor vibration information) | `ServerLevel.gameEvent(...)`, `GameEventDispatcher` |
| Received game event | `receivedGameEvent` | Dark cyan | A sculk sensor received a vibration. Also marks the **vibration source** position | `VibrationSystem.Ticker.receiveVibration` |
| Piston spawn / Piston remove | `movingPiston` | Light purple | Appearance or disappearance of a moving-piston block entity | `Level.setBlockEntity(...)`, `PistonMovingBlockEntity.finalTick` |
| Piston move | `movingPistonTick` | Light purple | Progress of a moving piston each tick; during replay it is simulated with a block-display entity | `PistonMovingBlockEntity.tick(...)` |
| Entity spawn / Entity remove | `entitySpawn` | Green / Dark red | An entity enters or leaves the recorded area (includes real spawn/death) | `ServerLevel.addEntity`, `Entity.onRemoval`, and movement across area boundaries |
| Entity move | `entityMove` | Gold | One entity displacement; records previous/next coordinates and velocity | `Entity.move(MoverType, Vec3)` |
| Invisible step | `invisibleStep` | White | Fallback type of the `BlockPosEvent` base class; should not appear under normal circumstances | — |

### About colors

| Color | Meaning |
|---|---|
| Light purple | Phase / piston |
| Yellow | Queue items, enqueue actions |
| Red | Neighbor update; **empty** queue / block-entity tick; or **failed** setBlock |
| Cyan | Shape update |
| Green | Successful setBlock, entity spawn |
| Gold | Entity tick / entity move |
| Dark cyan | Game event (post / receive) |
| Gray | Currently **exiting** a scope (EXIT node) |

A trailing `∅` (empty-set symbol) indicates an **empty node** that produced no child events.

---

## Quick Start

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

Recording stops automatically after 20 gt (or you can stop it manually with `/mtr record stop`).

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

## Command Overview

All commands are rooted at `/mtr` and require **operator permission**.

```
/mtr
├─ profile
│  ├─ create <name>
│  ├─ delete <name>
│  ├─ info <name>
│  ├─ migrate <name>
│  └─ area
│     ├─ add <name> <pos1> <pos2> [area_name]
│     ├─ remove <name> <area_name>
│     └─ modify
│        ├─ pos <name> <area_name> <pos1> <pos2>
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
├─ screen [page]
├─ forward <unit> [amount]
├─ backward <unit> [amount]
├─ jump <step>
├─ dump <step>
└─ auto
├─ stop
└─ <direction> <unit> <delay> <steps>
```

---

## Profile & Area Management

### `/mtr profile create <name>`
Create a new profile. The name may only contain `A-Z a-z 0-9 _ . + -`.  
Fails if a profile with the same name already exists.  
After creation an `info` is automatically executed (enters area-editing mode and shows the preview).

### `/mtr profile delete <name>`
Delete a profile. **Irreversible.**

### `/mtr profile migrate <name>`
**Copy** a profile that is stored in the global directory (`./config/mrt_profiles` etc., outside any world-folder) into the current world.

### `/mtr profile info <name>`
**Area-preview toggle command**

- First execution: sets the profile as the “currently viewed profile”, marks all of its areas in the current dimension with glass block-display entities, and prints statistics (creation time, recorded tick count, frame count, area count).  
  **Also activates the [Purple Dye Selection Tool](#purple-dye-selection-tool).**
- Second execution exits the viewing state; the selection tool is disabled accordingly.

> This state is **server-wide unique**.

### `/mtr profile area add <name> <pos1> <pos2> [area_name]`
Add an axis-aligned area to the profile. The dimension is taken from **the dimension the command executor is currently in**.  
`pos1` / `pos2` support relative (`~ ~ ~`) and local coordinates, but **must refer to already-loaded blocks**.  
When `area_name` is omitted an auto-incrementing numeric name (`1`, `2`, `3`…) is assigned. Duplicate names fail.

### `/mtr profile area remove <name> <area_name>`
Remove the specified area. `area_name` has tab-completion.

### `/mtr profile area modify pos <name> <area_name> <pos1> <pos2>`
Change the two corner coordinates of an existing area (dimension stays the same).

### `/mtr profile area modify rename <name> <area_name> <new_name>`
Rename an area. Fails if the new name is already taken.

---

## Purple Dye Selection Tool

While `/mtr profile info <name>` is **active**, holding a **purple dye**:

| Action | Effect |
|---|---|
| Left-click a block | Set **Pos 1** |
| Right-click a block | Set **Pos 2** |
| **Sneak + Right-click** | Confirm and save the current selection as a new area (auto-numbered) |
| **Sneak + Left-click** | Clear the current selection |

When only one point has been set, the other point **follows your crosshair in real time** (hits a block within 4 blocks, otherwise uses the look-vector end point) and a translucent block-display entity previews the selection dynamically.

---

## Recording

### `/mtr record <name> <ticks>`
Start recording.

> **Important**: If the profile already contains recorded data, executing `record` again will **append** to it rather than overwrite from the beginning. To start over, first run `/mtr record clear <name>`.  
> **Important**: Running `/tick freeze` beforehand gives better control over the recording.

### `/mtr record stop`
Manually stop recording.

### `/mtr record clear <name>`
Clear all frame data of the profile. Area configuration is retained.  
Refused while the profile is currently being recorded or replayed.

---

## Replay

### `/mtr replay start <name>`
Backup the **current** world state inside the areas as `<name>_replay.dat` (used to restore your live world when replay ends).  
Then restore the areas to the state they had **at the start of recording** using `<name>_record.dat`.  
The cursor is placed at step 0; advance it with the commands below.

### `/mtr replay stop`
Restore the `_replay` backup (bring your live world back), clear all marker entities and replay entities, and close BossBars / sidebars of all subscribers.

### `/mtr replay forward <unit> [amount]`
### `/mtr replay backward <unit> [amount]`
Advance / go back by the specified **unit**. `amount` defaults to `1` when omitted.

| unit | Meaning |
|---|---|
| `ticks` | Advance `amount` **game ticks**, executing every event that occurred during them |
| `steps` | Advance `amount` **visible steps** (affected by `step_ignore_updates` / `step_ignore_exiting`) |
| `phase` | Advance to the next **phase** boundary (scheduled-tick phase / block-entity phase …) |
| `queue` | Advance to the next **queue** boundary (block event / scheduled tick / fluid tick / entity tick / block-entity tick …) |
| `updates` | Advance to the next **update** (neighbor update / shape update) boundary |

When going backward each event is **applied in reverse** (setBlock restores the old state, entity spawn becomes a removal, etc.).

### `/mtr replay jump <step>`
Jump directly to the given **step number** (the number shown as `[#N↗]` on the timeline UI).

### `/mtr replay auto <direction> <unit> <delay> <steps>`
Automatic stepping: every `<delay>` game ticks, take one step in `<direction>` (`forward` / `backward`) using `<unit>`, for a total of `<steps>` steps.

> Works very well together with flashback!

### `/mtr replay auto stop`
Stop automatic stepping.

### `/mtr replay subscribe` / `unsubscribe`
Subscribe / unsubscribe from the replay **BossBar** and **sidebar timeline**.

---

## Timeline UI & Call Stack

### `/mtr replay screen [page]`
Open a **dialog-style timeline**, 40 entries per page. Each row contains:

- `[TickN]` — the game tick the event belongs to; a separator is drawn at tick boundaries
- Indentation + `▶` (enter a parent scope) or `→` (leaf event)
- Event description; **hover to see the full information** of that event (coordinates, dimension, block-state diff, setBlock flag-bit breakdown, etc.)
- The row under the current cursor is **bold + underlined**
- Trailing `[$]` — click to view the **call stack** of that step
- Trailing `[#N↗]` — click to **jump to that step**

Bottom buttons: `◀ Prev` / `Next ▶` / `✕ Close`.

### `/mtr replay dump <step>`
Open the **Java call-stack** dialog for the given step. Stack frames are color-coded by package / class / method / file:line and placed in a multi-line text box for easy **select-all & copy**; the title line supports click-to-copy and hover preview of the first 20 lines.

Call stacks are captured **at recording time** when each event is constructed and stored together with the profile in `mtr_stacktrace/<name>.dat`. Frames belonging to the mod itself and mixin-synthesized methods are filtered out.

---

## Game Rules

All located under the **MISC** category of `/gamerule`; all are booleans.

| Game rule | Default | Effect |
|---|---|---|
| `microtimingreplay:skip_empty_phase` | `true` | Discard **phase nodes that produced no children** during recording |
| `microtimingreplay:skip_empty_queue` | `true` | Discard **empty queue nodes** during recording |
| `microtimingreplay:skip_empty_update` | `true` | Discard **empty update nodes** during recording (these are extremely numerous; keeping the rule on is recommended) |
| `microtimingreplay:skip_empty_entity_tick` | `false` | Discard **entity ticks that produced no children** during recording (recommended to turn off if the scene contains entities) |
| `microtimingreplay:skip_empty_block_entity_tick` | `true` | Discard **block-entity ticks that produced no children** during recording |
| `microtimingreplay:step_ignore_updates` | `true` | When using `forward/backward steps`, **skip** block updates so they do not count as a step |
| `microtimingreplay:step_ignore_exiting` | `true` | When using `forward/backward steps`, **skip** “exit-scope” events |
| `microtimingreplay:skip_delta_changes` | `true` | During replay rendering **only mark the current step**; when turned off every position crossed by the jump is marked (useful for large jumps to see the overall picture, but can also become messy…) |

The first five rules (`skip_empty_*`) affect the **size of the recorded data**; changes only apply to subsequent recordings. The last three only affect **replay feel and display** and can be changed at any time.

---

## Data Storage Locations

All under the **Fabric config directory** (single-player `.minecraft/config/`, dedicated server `config/`):

| Path | Content |
|---|---|
| `config/mtr_profiles/<world-key>/<name>.dat` | Profile body of the current world: area definitions + all event frames (compressed NBT) |
| `config/mtr_backups/<world-key>/<name>_record.dat` | World snapshot of the areas at the start of recording |
| `config/mtr_backups/<world-key>/<name>_replay.dat` | Live-world snapshot taken just before replay started |
| `config/mtr_stacktrace/<world-key>/<name>.dat` | Call stack of every step |

> Data is automatically isolated per world. `<world-key>` is generated from the world path and contains a readable world-folder name plus a short hash; different worlds can safely use profiles with the same name.

---

## Notes

- **Run `/tick freeze` before recording.** The mod never freezes or unfreezes time by itself. Replay is unaffected; stepping works correctly even without freezing.
- **Replay truly modifies the world.** `replay start` restores the areas to the recording start state; `replay stop` restores your live world. If the server **crashes or is force-killed** during replay, the `_replay` backup is not restored automatically and your world stays at the recording-start state — in that case simply run `/mtr replay start` again and then a normal `stop` to recover.
- **Non-player entities inside the areas are backed up and restored.** On restore the entities inside the areas are first cleared, then rebuilt from the backup.
- **No areas = no filtering.** A profile without any areas records every event in the whole world; use with caution.
- `subscribe`, `unsubscribe`, `screen` and `dump` **must be executed by a player** (they either open a UI for the player or bind to a player). All other commands can be run from the console or command blocks.
- The “currently viewed” state of `/mtr profile info` is a single server-wide variable; simultaneous operations by multiple players will interfere with each other.

---

## License
MIT License [LICENSE](LICENSE).
