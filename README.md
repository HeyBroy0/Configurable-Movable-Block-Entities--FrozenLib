# Frozen Piston Probe

A deliberately small Minecraft 26.3 Fabric test mod to evaluate FrozenLib's piston block-entity movement support before changing the main Configurable Movable Block Entities mod.

## What it tests

### Target blocks

- Sculk sensor
- Calibrated sculk sensor
- Sculk shrieker
- Sculk catalyst
- Lectern

### Block-entity controls

- Chest
- Trapped chest
- Dispenser
- Crafter
- Chiseled bookshelf
- All vanilla wooden shelf variants (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, bamboo, crimson, warped, and poplar)

The inventory/data controls help separate a general block-entity transfer problem from lifecycle bugs specific to sculk or lecterns. Crafter, chiseled bookshelf, and the wooden shelf variants are added movement tests even though their contents are not all timer-driven.

### Vanilla scheduled-state baselines

- Grindstone (no block entity)
- Redstone lamp
- Target block
- Observer
- Stone button
- Stone pressure plate
- Redstone torch

These blocks do not use the block-entity push policy. They are included to compare ordinary piston state transitions and scheduled tick expiry.

## How the prototype is wired

- Applies configured piston reactions through `BlockBehaviour.BlockStateBase.getPistonPushReaction`, so `PistonBaseBlock.isPushable` and `PistonStructureResolver` see the same reaction. It also uses `PistonEvents.DETERMINE_BLOCK_ENTITY_PUSH_RESULT` to avoid FrozenLib's block-entity veto when a configured block is movable or breakable.
- Uses `PistonEvents.ON_MOVING_BLOCK_SET` to log the block, destination position, and whether a block entity is present when FrozenLib reports its moving-block set path.
- Adds the test blocks to `frozenlib:has_pushable_block_entity`. This tag is important: FrozenLib's piston mixin uses it when deciding whether to skip normal block-entity removal side effects, and its fallback pushability logic also consults the tag. The tag is merged with `replace: false` so other mods' entries are not intentionally replaced.
- Adds a generic landing-path compatibility Mixin. If FrozenLib's moving piston has saved carried block-entity NBT, the probe ORs `PistonBaseBlock.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` into the landing `Level.setBlock` flags. FrozenLib remains responsible for restoring the block entity from its saved NBT and firing `ON_MOVING_BLOCK_SET`.
- Captures pending block and fluid ticks while the source position is cleared to air with `Block.UPDATE_MOVE_BY_PISTON`, before the source state and queue entries are lost. It associates the captured payload with the destination moving-piston entity using its movement direction, stores the tick appointments on that entity, and queues them at the landing position before placement. The payload preserves the absolute trigger time, priority, and sub-tick ordering. Pre-queuing is intentional: a landing neighbor update may try to schedule a fresh full-delay tick, and the scheduler deduplicates ticks by type and position.

## Build

Requires JDK 25 and Gradle 9.8.1. Minecraft 26.3 uses the unobfuscated Loom plugin (`net.fabricmc.fabric-loom`), so dependencies use standard Gradle `implementation` configurations; `modImplementation` belongs to the older remapping Loom setup and causes the reported “Could not find method modImplementation()” error. The included GitHub Actions workflow builds the project with those versions. Once Gradle is installed locally, run:

```sh
gradle build
```

The jar is written to `build/libs/`. Install the probe, Fabric API, and FrozenLib in the test instance. Keep the main Movable Block Entities mod disabled for the first run so the two piston implementations do not overlap.

FrozenLib is resolved from the Modrinth Maven repository using the published Minecraft 26.3 Fabric version ID `lcUrAjbb` (`3.0-mc26.3-fabric`): `maven.modrinth:9KawNmQc:lcUrAjbb`. This uses the version published on Modrinth rather than assuming the separately listed CurseForge version string is also present on Modrinth. See https://modrinth.com/mod/frozenlib/version/lcUrAjbb and https://github.com/FrozenBlock/FrozenLib.

## Configuration and in-game test screen

Press **P** in a world to open the policy editor. The key can be rebound under **Options → Controls → Key Binds → Frozen Piston Probe**. Enter a registry ID such as `minecraft:observer`, `minecraft:obsidian`, or `minecraft:sculk_sensor`; press **Tab** to cycle through up to five matching registry IDs; choose **Load settings** to read that block's current movement/tick policies into the controls; cycle the values, then choose **Apply + Save**. The **Open config file** button opens `config/frozen-piston-probe.properties` with the operating system's default handler for file links. Changes apply immediately in the current game process. The editor is intended for single-player testing; it does not send changes to a remote server.

The UI intentionally accepts any registered block ID so you can test special cases without rebuilding. Use a disposable world: forcing unusual blocks to move or break can produce destructive or unsupported states.

Example configuration:

```properties
movement.minecraft\:observer=vanilla
tick.minecraft\:observer=vanilla
movement.minecraft\:obsidian=breakable
tick.minecraft\:sculk_sensor=transfer
```

The backslash before `:` is normal escaping emitted by Java's `Properties` writer. In the GUI, enter the ordinary ID without a backslash.

### Movement policy

- `vanilla` — use the block's original piston reaction.
- `movable` — override the reaction to `PUSH_PULL`, allowing ordinary push/pull handling where vanilla's other piston checks allow it.
- `breakable` — override the reaction to `POPPED`, making the resolver treat the block as one to destroy on a push.
- `immovable` — override the reaction to `IMMOVEABLE`.

The reaction override applies to the block state's piston-reaction method, so the pushability check and structure resolver use a consistent value. For block entities with an explicit probe policy, `movable` and `breakable` return FrozenLib `SUCCESS`; `immovable` and `vanilla` return `FAIL`. This is intentional: `vanilla` must not fall through to FrozenLib's tag-based movable-block-entity fallback. Blocks without an explicit probe policy still return `PASS` so the probe does not override other mods' FrozenLib callbacks. The `movable` and `breakable` modes also bypass the vanilla `destroySpeed == -1` veto inside the piston pushability check, so they can deliberately override blocks normally considered unbreakable. World/build-height/border checks, piston limits, and extended-piston restrictions remain in force. The resolver now attempts to include companion halves for two-part blocks such as doors and beds; it rejects a piston operation when a companion destination is blocked. Because Minecraft finalizes each moving-piston placeholder separately, the landing hook also stages a matching, non-block-entity companion half without neighbor updates before the current half lands. This is experimental: test doors/beds in both piston directions, against slime/honey chains, and near obstructions, because the resolver and landing hooks have not been validated in a live game in this environment.

Movement defaults are bundled in `frozen-piston-probe-defaults.properties` and copied into the editable config when the mod first runs. This keeps the initial per-block policies visible in the same file users edit in-game. The former Java block list is now used only to migrate old short keys such as `sculk_sensor=allow`; active movement and tick settings come from the properties file and registry-ID entries such as `movement.minecraft\:observer`. An explicit registry-ID key takes precedence over a legacy short key.

### Scheduled-tick policy

- `vanilla` — do not remove or transfer source ticks. Vanilla leaves the appointment at the original position; the normal block-type check determines whether it still has an effect.
- `transfer` — remove matching block/fluid ticks from the source queue and carry them to the destination, preserving absolute trigger time, priority, and sub-tick order.
- `duplicate` — retain the source tick and also queue a copy at the destination on landing. The scheduler deduplicates by tick type and position, so it cannot create two independent appointments for the same type at one position.

The bundled defaults explicitly configure tick transfer for the sample blocks, with observers set to `vanilla` because excluding observers from tick transfer restored the redstone test machine. Other blocks default to `transfer` unless an entry is present. The UI can override the policy. Tick policy applies to matching block ticks and the source block's current fluid type when one is present.

## Recommended test sequence

1. Create a backup or use a disposable test world.
2. Test each block with `movable`, in both piston extension and retraction. Put a named book on the lectern, items in the chest/dispenser, and activate each sculk block before moving it where possible.
3. For sensors, test normal and calibrated activation before and after movement. Confirm it can hear later vibrations, deactivate correctly, and continue producing expected redstone output.
4. For the shrieker, test before and after movement. The probe now adds the block-entity-side-effect skip flag only to the landing `setBlock` operation when FrozenLib's moving piston contains saved carried-block-entity NBT. Look for the debug message `Preserving carried block entity during piston landing`. If the crash is gone, still verify shrieker activation and vibration behavior; avoiding the exception does not prove the whole lifecycle is correct.
5. In the policy editor, compare `movable`, `breakable`, `immovable`, and `vanilla` on a safe test block. Confirm both push and pull behavior, and check that breakable blocks are destroyed only when vanilla piston rules permit it.
6. Test the chest, dispenser, crafter, chiseled bookshelf, and the wooden shelf variants as block-entity controls. Move the lamp, target, observer, button, pressure plate, and torch as vanilla scheduled-state baselines.
7. Repeat with destination positions across a chunk boundary, then save/reload while a block is moving if possible. Test single-block moves separately from slime/honey-chain pushes.

## Interpreting logs

`Push decision` logs show which mode and `PushResult` FrozenLib received. `Moving-block callback` logs show whether the callback was reached and whether a block entity was present at the callback's destination position. These logs are diagnostic signals, not proof that a sculk block's internal vibration lifecycle is healthy; the in-game behavior tests remain necessary.

## Generic landing guard

The reported crash occurred before FrozenLib's `ON_MOVING_BLOCK_SET` callback: Minecraft called `SculkShriekerBlockEntity.preRemoveSideEffects` with `minecraft:moving_piston` as the supplied state. This revision tests a broader lifecycle guard that checks FrozenLib's saved carried-block-entity NBT and only then adds `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` to the landing `Level.setBlock` call. It does not globally change normal block placement/removal, and it does not assume every block entity has a `shrieking`-style property. This is an experiment and still requires a CI build plus in-game regression testing.

## Known limitations

- This is a test harness, not a release-ready replacement mod.
- It uses the FrozenLib API and tag rather than copying FrozenLib implementation code.
- Grindstone is not in the configurable list because it has no block entity and does not reach this event.
- This 0.1.9 policy/UI and paired-block landing revision has not been compiled or tested in Minecraft in this environment. The available runtime has JDK 21 and no Gradle executable; build it with JDK 25 and Gradle 9.8.1, then test each policy in a disposable world before relying on it.
- FrozenLib is GPL-3.0-only. Review licensing before publishing or bundling this prototype.

### Minecraft 26.3 compatibility note

The probe uses `BuiltInRegistries.BLOCK.getKey(block)` for block registry identifiers, matching the Minecraft 26.3 mappings used by this project. This revision uses MixinExtras to wrap the piston landing call; supported Fabric Loader versions supply MixinExtras at runtime.


## 0.1.3 scheduled-tick transfer experiment

This experiment captures pending **block ticks whose type matches the block at the source position** and pending **fluid ticks whose type matches the source fluid state** immediately before a position is replaced with the moving-piston placeholder. Captured ticks are attached to the moving piston block entity, saved with it, and queued at the landing position with their original absolute trigger time, priority, and sub-tick order. As game time continues during motion, a tick that becomes overdue executes promptly after the moved block lands.

Ticks for other types at the same coordinates are intentionally not transferred. Vanilla scheduled ticks name a type and a position, and Minecraft validates the type when the tick runs. A tick for a different block at the source position is not part of the moved block's timer.

The capture operates on the pending per-chunk queues. A tick already collected by the level scheduler for the current tick may be outside those queues; this first probe does not try to migrate an in-flight tick. Cross-chunk scheduling is delegated to the level scheduler rather than writing directly into the destination chunk's queue. A tick whose type differs from the source block is deliberately left behind, where vanilla's scheduled-tick type check will discard it if that type is no longer present.

### Added test blocks

- State-expiry targets: sculk sensor, calibrated sculk sensor, sculk shrieker, lectern.
- Block-entity controls: chest, trapped chest, dispenser, crafter, chiseled bookshelf, wooden shelf variants.
- Vanilla behavior baselines: grindstone, redstone lamp, target, observer, stone button, stone pressure plate, redstone torch. The baseline blocks are logged by the moving-block callback but are not controlled by the block-entity push policy.

### Testing schedule restoration

Test both piston extension and sticky retraction. For timers, begin movement shortly after the block schedules its expiry, then confirm it expires after approximately the remaining delay rather than becoming stuck or resetting to a full delay. Repeat with a destination across a chunk boundary and save/reload while the block is moving if possible. Also test a scheduled tick for another type at the source coordinates; the probe should not attach that unrelated tick to the moved block.


## 0.1.4 null-payload hardening

The 0.1.3 experiment could crash when a moving-piston block entity had no captured scheduled ticks: the Mixin-added tick lists were null for that instance, and constructing a payload called `List.copyOf(null)`. This revision treats absent tick lists as empty and guards serialization, so moving an ordinary block without captured timers does not fail simply because no payload was attached. Scheduled-tick transfer behavior is otherwise unchanged. This is a crash fix based on the reported stack trace; it still requires a CI build and in-game verification.


## 0.1.5 source-position tick capture correction

In 0.1.4, the capture hook ran when a `MOVING_PISTON` placeholder was installed. Vanilla piston movement clears the real source positions to air first using `Block.UPDATE_MOVE_BY_PISTON`, and writes destination placeholders afterward; the placeholder's old state is commonly air, so that hook could miss the moved block's scheduled expiry. This revision captures matching ticks from the source-clear operation and associates them with the moving entity at `destination - movementDirection`. It skips source-piston entities so the piston-head/base placeholder cannot take an adjacent block's tick payload. The reported stuck states use scheduled block ticks in vanilla, so this is the first targeted correction to validate. Build and test before relying on it; ticks already collected into the level scheduler's current-tick work list remain outside this experiment's capture path.


## 0.1.6 capture-before-mutation correction

The 0.1.5 source-capture hook only observed source positions when vanilla wrote air with `UPDATE_MOVE_BY_PISTON`. For ordinary piston chains, most moved source positions are overwritten by a different moving-piston placeholder, so the hook did not capture those blocks' ticks. This revision wraps the first `PistonStructureResolver.getToPush()` call in `PistonBaseBlock.moveBlocks` and captures matching scheduled ticks from every resolved source position before any positions are changed. The old source-clear Mixin was removed. Build and test this revision before relying on it; the experiment still cannot capture ticks that the level scheduler has already collected into its current-tick work list.


## 0.1.7 fast-finalTick landing guard

The 0.1.6 crash report showed the shrieker failing during `PistonMovingBlockEntity.finalTick`, reached through a block event. That path lands via `Level.setBlockAndUpdate`, not the earlier `tick` method's wrapped `Level.setBlock`, so it did not receive the skip-side-effects flag. The existing `LevelChunkSetBlockStateMixin` is now repurposed to add `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` only when the old state is `minecraft:moving_piston` and the new state has a block entity. This covers the immediate finalTick path without changing ordinary block placements/removals.

The 0.1.6 branch configuration also omitted `PistonBaseBlockMixin` from `frozen_piston_probe.mixins.json`, so the resolver-time scheduled-tick capture code was not active. This version registers that Mixin. Finally, the old source-clear capture is removed: the resolver hook captures the push list before positions mutate, and the later empty capture could erase the earlier payload. Build and test this revision before relying on it.
