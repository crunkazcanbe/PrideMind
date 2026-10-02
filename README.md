# PrideMind

One shared AI engine for Minecraft 1.12.2 mods: a scheduler, a call budget, answer memory, confidence-rated decisions, a tool system with an agent loop, and mob brains for every creature from every mod.

## About

PrideMind is the "brain" other mods can ask questions of. Instead of every mod talking to a language model on its own, they all go through one queue that remembers answers, respects a calls-per-second budget, serves the most important questions first, and always hands back a fallback when the model is busy, slow or missing. Nothing ever runs on, or blocks, the game's threads.

It was built for the Pride modpack, a Minecraft 1.12.2 pack of around 750 mods, but it has no hard dependencies and works in any 1.12.2 Forge or Cleanroom pack.

**The engine is OFF by default.** With `backend = off` no model is loaded and nothing is sent anywhere: every request instantly gets its fallback, and mob brains use their built-in reflexes. To use a model, point PrideMind at any OpenAI-compatible server (llama.cpp server, llama-swap, LiteLLM, Ollama's `/v1` endpoint, and so on) — see [Connecting a model](#connecting-a-model).

The first built-in user is **mob brains**: every creature near a player decides what it *wants* (attack, stalk, flee, call for help, group up, or carry on) when something happens. Vanilla or mod AI still does the walking and fighting; PrideMind only steers.

## Features

- **Shared request API** — `Mind.ask` (free text), `Mind.choose` (always one of a list of options), `Mind.decide` (option + reason + confidence + duration).
- **Priority queue** — five priorities (`BACKGROUND`, `LOW`, `NORMAL`, `HIGH`, `URGENT`); the most important job runs first, oldest first within a priority.
- **Budget** — a global cap on model calls per second across all mods and all workers.
- **Bounded queue** — when the queue is full, the least important waiting job gives way and gets its fallback.
- **Deadlines and fallbacks** — every request has a deadline; late jobs are skipped and answered with their fallback.
- **Answer memory** — requests with the same `cacheKey` inside their cache time are answered instantly from an LRU cache (up to 50,000 entries), including questions that were answered while another identical one was waiting.
- **Confidence decisions** — `Decision` objects tell the caller whether to act, blend, or ignore.
- **Tool system** — a catalogue of 41 built-in tools (perception, knowledge, memory, actions) that any mod can extend with `Tools.register` or `Tools.simple`.
- **Agent loop** — `Agent.run` lets the model call tools step by step (up to 12 steps) and then answer.
- **Permission levels** — tools are tagged `LOOK`, `NOTE`, `ACT` or `POWER`; callers are only offered tools at or below their level. Players get `ACT`, ops get `POWER`.
- **World memory** — named places and events stored per world in `<world>/pridemind/memory.json`.
- **Mob brains** — event-driven decisions for every `EntityCreature` from every mod, with cooldowns, range limits, shared answers across a horde, reflex fallbacks and a JSONL decision log.
- **Stats** — per-purpose counters (asked, from memory, answered by model with average ms, reflex, late, crowded, failed) shown by `/mind`.
- **Server-side only** — `acceptableRemoteVersions = "*"`; clients do not need it installed.

## Commands

All commands need permission level 2 (op).

| Command | What it does |
|---|---|
| `/mind` or `/mind stats` | Engine name, queue length, remembered answers, and per-purpose stats grouped by prefix (`mob`, `command`, …). |
| `/mind mobs` | What mobs within 32 blocks are currently thinking: intent, confidence, source, trigger and reason. |
| `/mind ask <text>` | Runs the agent with your question (8 steps, `HIGH` priority) using your tool level; prints each tool step and the answer. In-game only. |
| `/mind tools` | Lists every registered tool with its level and description. |
| `/mind forget` | Clears the answer memory. |
| `/mind reload` | Reloads `config/pridemind.cfg` and restarts the scheduler with the new settings. |

Tab completion is provided for the sub-commands.

## Config

File: `config/pridemind.cfg`. Use `/mind reload` to apply changes without a restart.

### `engine`

| Key | Default | Description |
|---|---|---|
| `backend` | `off` | `off` = no model; every request gets its fallback and mobs use reflexes (nothing loads). `server` = an OpenAI-style server. |
| `url` | `http://127.0.0.1:11434/v1` | Server address, ending in `/v1`. PrideMind posts to `<url>/chat/completions`. |
| `model` | `gemma4-e2b` | Model name on that server. |
| `key` | *(empty)* | API key, sent as `Authorization: Bearer <key>` if set. |

### `budget`

| Key | Default | Range | Description |
|---|---|---|---|
| `workers` | `1` | 1–16 | Questions asked at the same time. One local model = 1. |
| `callsPerSecond` | `3.0` | 0.1–100 | Most model calls per second, all mods together. Everything else waits, gets a remembered answer, or uses its fallback. |
| `maxQueue` | `256` | 8–100000 | Most questions waiting. When full, the least important one gives way. |

### `mobs`

| Key | Default | Range | Description |
|---|---|---|---|
| `enabled` | `true` | | Mob brains on/off. With `backend = off` they use built-in reflexes. |
| `cooldownSeconds` | `15` | 2–600 | A mob thinks again at most this often. |
| `range` | `48` | 8–256 | Only mobs this close to a player think. |
| `intentSeconds` | `12` | 2–120 | How long a decision lasts before the mob goes back to normal (when the model does not give its own duration). |
| `logDecisions` | `true` | | Append every decision to `logs/pridemind/decisions.jsonl` (useful for training a fast reflex model later). |
| `exclude` | `minecraft:ender_dragon`, `minecraft:wither`, `minecraft:armor_stand` | | Mobs that never get a brain. Registry names; `modid:*` excludes a whole mod. |

### Connecting a model

```
engine {
    S:backend=server
    S:url=http://127.0.0.1:8080/v1
    S:model=your-model-name
    S:key=
}
```

Any server that implements `POST /v1/chat/completions` with the OpenAI request/response shape works. Requests are non-streaming, with a 3 s connect timeout and a 60 s read timeout. Keep `workers = 1` for a single local model and raise `callsPerSecond` only if the server can keep up.

## API

All public classes are in `com.dogpound.pridemind.api`. Futures complete off the game thread — hop back with `server.addScheduledTask(...)` before touching the world.

### `Mind`

```java
// Free text. Completes with the answer, or the fallback.
Mind.ask(Mind.request("buddy.chat")
        .prompt("Say hi to the player in one sentence.")
        .priority(Mind.Priority.HIGH)
        .fallback("Hi!"))
    .thenAccept(answer -> server.addScheduledTask(() -> player.sendMessage(new TextComponentString(answer))));

// Always exactly one of the options (the fallback, or the first option, when unsure).
Mind.choose(Mind.request("mob.zombie").prompt(situation).cacheKey(key).fallback("ATTACK"),
            Arrays.asList("ATTACK", "FLEE", "IGNORE"))
    .thenAccept(choice -> ...);

// An option plus reason, confidence (0–1) and how long it should hold.
Mind.decide(Mind.request("quests.pick").prompt("..."), Arrays.asList("EASY", "HARD"))
    .thenAccept(d -> { if (d.act()) ...; else if (d.blend()) ...; });

Mind.thinking();   // true when a model is connected
Mind.stats();      // per-purpose counters
Mind.pick(text, options, fallback);  // first option named in a text, case-insensitive
```

- `choose` caps `maxTokens` at 8 and appends "Answer with exactly one of: …" to the prompt.
- `decide` raises `maxTokens` to at least 60 and asks for JSON `{"decision","reason","confidence","seconds"}`.

### `Request`

Every field has a default; set only what you need. The `purpose` string groups stats (`"mob.zombie"`, `"buddy.chat"`); `/mind` groups by the part before the first dot.

| Field / setter | Default | Meaning |
|---|---|---|
| `system(String)` | `"You are part of a Minecraft mod. Answer briefly."` | System prompt. |
| `prompt(String)` | `""` | User prompt. |
| `priority(Mind.Priority)` | `NORMAL` | `BACKGROUND`, `LOW`, `NORMAL`, `HIGH`, `URGENT`. |
| `deadline(long ms)` | `15000` | Give up and use the fallback if not answered in time (counted from creation). |
| `maxTokens(int)` | `64` | Max tokens for the reply. |
| `temperature(double)` | `0.4` | Sampling temperature. |
| `cacheKey(String)` | `null` | Same key inside the cache time = same answer without asking the model. `null` = never cached. |
| `cacheTtl(long ms)` | `60000` | How long a cached answer is kept. |
| `fallback(String)` | `null` | What the caller gets when the model can't answer. |

### `Decision`

Fields: `decision`, `reason`, `confidence`, `seconds` (0–120; 0 = caller's default), `source` (`"mind"`, `"reflex"` or `"told"`).

| Confidence | Method | Meaning |
|---|---|---|
| ≥ 0.7 | `act()` | Do it. |
| 0.4 – 0.7 | `blend()` | Do it briefly, mixed with normal behaviour. |
| < 0.4 | `ignore()` | Ignore and think again later. |

- No model, too busy, or late → the fallback at confidence 0.5 with source `reflex`.
- Model JSON without a `confidence` → 0.6.
- Reply that isn't valid JSON but names an option → that option at 0.55; names none → reflex at 0.5.

### Tools

A `Tool` has a `name()`, an `args()` summary for the model (e.g. `"x:int y:int z:int"`), a `description()`, a `level()` and `run(ToolContext, JsonObject)` returning anything Gson can serialise. Tools run on the server thread.

```java
// Short form
Tools.simple("reactor_status", "x:int y:int z:int", "Heat and output of a reactor.", Tool.Level.LOOK,
        (ctx, a) -> {
            BlockPos p = new BlockPos(a.get("x").getAsInt(), a.get("y").getAsInt(), a.get("z").getAsInt());
            return Collections.singletonMap("heat", readHeat(ctx.world, p));
        });

// Full form
Tools.register(new Tool() { /* name(), args(), description(), level(), run(...) */ });

// Call one directly (runs on the server thread, waits up to 5 s, never throws; returns JSON text)
String json = Tools.call(ToolContext.of(player), "find_blocks", argsJson);
```

`Tools.call` returns `{"error":...}` for unknown tools, tools above the caller's level, and exceptions. Results longer than 4000 characters are cut.

### `ToolContext` and levels

`ToolContext(server, world, player, at, allowed)` says who is asking (`player` may be `null` for a mob, quest or the server), where (`at`), and the highest level they may use. `ToolContext.of(player)` gives ops `POWER` and everyone else `ACT`.

| Level | Meaning |
|---|---|
| `LOOK` | Read-only: look at the world, recipes, players. |
| `NOTE` | Take notes: save and remove remembered places/events. |
| `ACT` | Harmless actions: messages, titles, sounds, particles, nudging mobs. |
| `POWER` | What an op could do with commands: blocks, items, teleport, time, weather, any command. |

### `Agent`

```java
Agent.run("buddy.help", "Where is the nearest diamond ore?", ToolContext.of(player), 8, Mind.Priority.HIGH)
     .thenAccept(r -> server.addScheduledTask(() -> {
         r.steps.forEach(s -> player.sendMessage(new TextComponentString(s)));
         player.sendMessage(new TextComponentString(r.answer));
     }));
```

The model sees the goal and the tools allowed at the caller's level, and replies with one JSON line: `{"tool":"<name>","args":{...}}` or `{"answer":"..."}`. Each tool result is added to the conversation. Steps are clamped to 1–12; when they run out the model is asked for a final answer. Every model call still goes through the scheduler (budget, priority, deadline 90 s per step). A plain-text reply is treated as the answer. With no model connected, `run` returns a message saying so.

## Built-in tools

Arguments are optional unless the tool can't work without them. Positions default to where the asker stands; player names default to the asker.

### Perception (`LOOK`)

| Tool | Args | What it returns |
|---|---|---|
| `block_at` | `x y z` | Block id/name/meta, whether it has machine data, hardness, light, loaded. |
| `find_blocks` | `block radius limit` | Nearest blocks whose id contains the text (radius default 16, max 48; limit max 50). |
| `scan_area` | `radius` | Counts of the 20 most common non-air blocks (radius default 8, max 24). |
| `environment` | `x y z` | Biome, temperature, rainfall, time, day, phase, rain/thunder, sky/block light, sees sky, dimension, moon phase, difficulty, surface Y. |
| `nearest_structure` | `type` | Nearest Village, Stronghold, Mineshaft, Monument, Mansion, Temple, Fortress or EndCity. |
| `find_entities` | `type radius limit hostile` | Entities nearby, nearest first, filtered by id/name text and hostility (radius default 24, max 96). |
| `threat_assessment` | `radius` | Hostile mobs with a danger score, overall level (LOW/MEDIUM/HIGH/EXTREME), and which directions are clear. |
| `player_state` | `name` | Position, health, hunger, saturation, armour, effects, held/offhand items, level, game mode, flying, spawn. |
| `inventory` | `name` | A player's inventory and ender chest, stacked by item name. |
| `players_online` | — | Every player online with dimension, position and health. |
| `container_at` | `x y z` | Contents of a chest, machine or any item-handler block, with empty-slot count. |
| `machine_status` | `x y z` | Energy (Forge Energy), fluid tanks, item slots, redstone power, and the six neighbouring blocks with `[items]`/`[energy]` tags. |
| `dimensions` | — | Every loaded dimension with player and entity counts. |

### Knowledge (`LOOK`)

The code walks the real recipe data; the model only explains it.

| Tool | Args | What it returns |
|---|---|---|
| `search_items` | `text limit` | Items from every mod whose id or name contains the text (limit max 60). |
| `item_info` | `item meta` | Name, mod, stack size, durability, food value, fuel ticks, tool classes, is-block, ore-dictionary names, smelting result, recipe count. |
| `recipes_for` | `item meta limit` | Crafting-table recipes that make the item, with ingredients, plus a smelting source if any. |
| `uses_of` | `item meta limit` | Crafting recipes that take the item. |
| `can_craft` | `item meta` | Whether the asker can craft it now from their inventory; lists what's missing per recipe. |
| `smelting` | `item meta` | Furnace result and XP. |
| `ore_dictionary` | `name limit` | Every item under an ore-dictionary name (e.g. `ingotCopper`). |
| `mods` | `text` | Installed mods matching the text, with versions (count only if no text). |
| `mob_types` | `text limit` | Entity ids from every mod containing the text. |

Items are given as `modid:name` (with optional `meta`); a display name also works as a last resort.

### Memory

Stored per world in `<world>/pridemind/memory.json` (up to 5000 notes). A new place with the same name by the same author replaces the old one.

| Tool | Level | Args | What it does |
|---|---|---|---|
| `remember_place` | `NOTE` | `name note x y z` | Save a named place. |
| `remember_event` | `NOTE` | `text x y z` | Write down something that happened (the world's diary). |
| `recall` | `LOOK` | `text limit` | Search places and events by words, newest first, with distance and compass direction. |
| `forget_place` | `NOTE` | `name` | Remove a saved place — your own, or any if you have `POWER`. |

Other mods can write events directly with `Memory.remember(ctx, kind, name, text, pos)`.

### Actions

| Tool | Level | Args | What it does |
|---|---|---|---|
| `say` | `ACT` | `text to` | Chat message to one player (default: the asker) or `to:"all"`. |
| `title` | `ACT` | `text subtitle to` | Big title text on a player's screen. |
| `play_sound` | `ACT` | `sound x y z volume pitch` | Play any sound by id at a spot. |
| `particles` | `ACT` | `type x y z count` | A puff of particles (count 1–200). |
| `mob_intent` | `ACT` | `entityId intent seconds why` | Tell a mob what it wants (`ATTACK`, `STALK`, `FLEE`, `CALL_HELP`, `GROUP_UP`, `IGNORE`) toward the asker. |
| `mob_move` | `ACT` | `entityId x y z speed` | Walk a mob to a spot with its own pathfinding. |
| `set_block` | `POWER` | `block meta x y z` | Place any block by id. |
| `give` | `POWER` | `item meta count to` | Give items (count up to 4 stacks); drops them if the inventory is full. |
| `teleport` | `POWER` | `x y z who` | Move a player within their dimension. |
| `summon` | `POWER` | `entity x y z` | Spawn any entity by id. |
| `effect` | `POWER` | `potion seconds level to` | Apply a potion effect. |
| `heal` | `POWER` | `who` | Fill health, hunger and saturation. |
| `set_time` | `POWER` | `time` | Set the time of day in every world. |
| `set_weather` | `POWER` | `weather seconds` | `clear`, `rain` or `thunder` (at least 30 s). |
| `run_command` | `POWER` | `command` | Run any server command (without the slash), as the asker or the server. Last resort. |

## Mob brains

Every `EntityCreature` that joins a world (and isn't excluded) gets a small extra AI task at priority 0. It does nothing until the mob has a fresh decision.

**Triggers** (server side only):

- the mob targets a player — "it spotted the player";
- a player hurts it — "got hurt by the player"; mobs of the same kind within 16 blocks also think — "a friend of its kind was attacked";
- every 2 seconds, creatures within 16 blocks of a non-spectator player — "the player came close";
- every 2 seconds, mobs within 32 blocks that target a player but can't see them — "it lost sight of the player".

A mob only thinks if it is within `range` of the player and its `cooldownSeconds` has passed.

**Intents.** Hostile mobs (anything implementing `IMob`) choose from `ATTACK`, `STALK`, `FLEE`, `CALL_HELP`, `GROUP_UP`, `IGNORE`; passive ones from `FLEE`, `GROUP_UP`, `IGNORE`.

- `ATTACK` sets the player as attack target and lets the mob's own AI fight.
- `CALL_HELP` does the same and also sets the target for idle mobs of the same kind within 24 blocks.
- `STALK` watches the player, backs off inside 8 blocks, closes in beyond 14, and holds in between.
- `FLEE` runs to a random spot away from the player.
- `GROUP_UP` walks to the centre of nearby mobs of the same kind.
- `IGNORE` clears a player attack target and carries on as normal.

Creative and spectator players are never targeted.

**Situation and shared answers.** What a mob knows is squeezed into buckets: mob type and mod, hostile or not, health (25/50/75/100 %), day or night, in water, distance word, player health bucket, what the player holds (nothing, sword, bow, tool, food, item), group size word, and the trigger. The bucket string is the answer-memory key (kept 120 s), so a whole horde in the same situation shares one model call.

**Priority and deadline.** Within 8 blocks → `HIGH`, within 20 → `NORMAL`, further → `LOW`. The deadline is half the cooldown.

**Confidence.** `ignore()` → nothing changes and the mob thinks again later. `blend()` → the decision holds for half its time (at least 2 s). `act()` → it holds for the model's `seconds`, or `intentSeconds`.

**Reflex fallback.** With no model, or when it's busy or late, the built-in reflex decides (confidence 0.5):

- Passive: hurt → `FLEE`; with 2+ of its kind within 12 blocks and the player not holding food → `GROUP_UP`; else `IGNORE`.
- Hostile: at ≤ 25 % health → `CALL_HELP` with 3+ friends, else `FLEE`; player at ≤ 25 % → `ATTACK`; hurt with 2+ friends → `CALL_HELP`; at ≤ 50 % health, alone, player armed → `STALK`; daytime and more than 12 blocks away → `STALK`; else `ATTACK`.

**Steering from other mods.** `MobBrains.command(mob, "FLEE", player, seconds, "why")` sets a decision directly (confidence 1, source `told`).

**Decision log.** With `logDecisions = true`, every decision is appended on a background thread to `logs/pridemind/decisions.jsonl`:

```json
{"t":1696000000000,"key":"minecraft:zombie|the player came close|100|n|close|100|a sword|alone|-","situation":"Mob: zombie (from the minecraft mod), hostile, 100% health, night, alone. A player is close with 100% health, holding a sword. What just happened: the player came close.","intent":"ATTACK","reason":"reflex","confidence":0.50,"source":"reflex"}
```

## Designed not to lag

- **Event-driven.** Mobs think only on triggers, at most once per cooldown, and only near players. No per-tick model calls.
- **Off the game thread.** Requests return a `CompletableFuture`; workers are low-priority daemon threads. Tools that touch the world are run on the server thread with a 5 s cap.
- **Answer memory.** Bucketed situations and `cacheKey`s mean repeated questions never reach the model.
- **Budget.** A global `callsPerSecond` pace across all workers.
- **Priority queue.** Close, urgent questions go first; a full queue drops the least important job.
- **Deadlines and fallbacks.** Stale jobs are skipped; every caller always gets an answer, and mobs fall back to reflexes.
- **Off by default.** With `backend = off` the scheduler answers every request instantly with its fallback.

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2860 or newer, or Cleanroom
- Server side only; clients don't need it (works in singleplayer too)
- Optional: an OpenAI-compatible chat-completions server to enable the model

## Install

1. Drop `PrideMind-1.12.2-0.1.0.jar` into the `mods` folder (on the server, or the client for singleplayer).
2. Start the game once to create `config/pridemind.cfg`.
3. Optional: set `backend = server`, `url` and `model`, then run `/mind reload`.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. The project targets Java 8 (ForgeGradle 3, MCP snapshot `20171003-1.12`).

## License

MIT License — © 2026 crunkazcanbe

## Credits

Made by crunkazcanbe, with Claude.
