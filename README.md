# Frozen Piston Probe

A deliberately small Minecraft 26.3 Fabric test mod to evaluate FrozenLib's piston block-entity movement support before changing the main Configurable Movable Block Entities mod.

## What it tests

### Target blocks

- Sculk sensor
- Calibrated sculk sensor
- Sculk shrieker
- Sculk catalyst
- Lectern

### Positive controls

- Chest
- Trapped chest
- Dispenser

The controls help separate a general block-entity transfer problem from lifecycle bugs specific to sculk or lecterns. A grindstone is logged as a vanilla baseline; it has no block entity and is therefore not controlled by FrozenLib's block-entity push event.

## How the prototype is wired

- Uses `PistonEvents.DETERMINE_BLOCK_ENTITY_PUSH_RESULT` to apply each configured block's movement policy.
- Uses `PistonEvents.ON_MOVING_BLOCK_SET` to log the block, destination position, and whether a block entity is present when FrozenLib reports its moving-block set path.
- Adds the test blocks to `frozenlib:has_pushable_block_entity`. This tag is important: FrozenLib's piston mixin uses it when deciding whether to skip normal block-entity removal side effects, and its fallback pushability logic also consults the tag. The tag is merged with `replace: false` so other mods' entries are not intentionally replaced.
- Adds a generic landing-path compatibility Mixin. If FrozenLib's moving piston has saved carried block-entity NBT, the probe ORs `PistonBaseBlock.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` into the landing `Level.setBlock` flags. FrozenLib remains responsible for restoring the block entity from its saved NBT and firing `ON_MOVING_BLOCK_SET`. This tests a lifecycle-level fix instead of special-casing the shrieker's `shrieking` property.

## Build

Requires JDK 25 and Gradle 9.8.1. Minecraft 26.3 uses the unobfuscated Loom plugin (`net.fabricmc.fabric-loom`), so dependencies use standard Gradle `implementation` configurations; `modImplementation` belongs to the older remapping Loom setup and causes the reported “Could not find method modImplementation()” error. The included GitHub Actions workflow builds the project with those versions. Once Gradle is installed locally, run:

```sh
gradle build
```

The jar is written to `build/libs/`. Install the probe, Fabric API, and FrozenLib in the test instance. Keep the main Movable Block Entities mod disabled for the first run so the two piston implementations do not overlap.

FrozenLib is resolved from the Modrinth Maven repository using the published Minecraft 26.3 Fabric version ID `lcUrAjbb` (`3.0-mc26.3-fabric`): `maven.modrinth:9KawNmQc:lcUrAjbb`. This uses the version published on Modrinth rather than assuming the separately listed CurseForge version string is also present on Modrinth. See https://modrinth.com/mod/frozenlib/version/lcUrAjbb and https://github.com/FrozenBlock/FrozenLib.

## Configuration

On first launch, the mod creates `config/frozen-piston-probe.properties`.

Each block-entity target accepts one mode:

- `allow` — return FrozenLib `SUCCESS` for this block entity.
- `deny` — return FrozenLib `FAIL`; the piston should refuse to move the block.
- `pass` — return `PASS` and allow FrozenLib's tag / other callbacks to decide. Because the probe adds its targets to the FrozenLib tag, this mode generally exercises the tag fallback rather than forcing a denial.

Example:

```properties
sculk_sensor=allow
calibrated_sculk_sensor=allow
sculk_shrieker=allow
sculk_catalyst=allow
lectern=allow
chest=allow
trapped_chest=allow
dispenser=allow
```

Changes require a game/server restart. Invalid values are logged and replaced by the defaults.

## Recommended test sequence

1. Create a backup or use a disposable test world.
2. Test each block with `allow`, in both piston extension and retraction. Put a named book on the lectern, items in the chest/dispenser, and activate each sculk block before moving it where possible.
3. For sensors, test normal and calibrated activation before and after movement. Confirm it can hear later vibrations, deactivate correctly, and continue producing expected redstone output.
4. For the shrieker, test before and after movement. The probe now adds the block-entity-side-effect skip flag only to the landing `setBlock` operation when FrozenLib's moving piston contains saved carried-block-entity NBT. Look for the debug message `Preserving carried block entity during piston landing`. If the crash is gone, still verify shrieker activation and vibration behavior; avoiding the exception does not prove the whole lifecycle is correct.
5. Restart with one target at `deny` to confirm policy enforcement. Then set it to `pass` to inspect the FrozenLib tag fallback path.
6. Test a chest and dispenser as data-persistence controls; move a grindstone as the vanilla baseline.
7. Repeat after saving, exiting, and reloading the world. If feasible, also test a slime/honey-chain push separately from a single-block push.

## Interpreting logs

`Push decision` logs show which mode and `PushResult` FrozenLib received. `Moving-block callback` logs show whether the callback was reached and whether a block entity was present at the callback's destination position. These logs are diagnostic signals, not proof that a sculk block's internal vibration lifecycle is healthy; the in-game behavior tests remain necessary.

## Generic landing guard

The reported crash occurred before FrozenLib's `ON_MOVING_BLOCK_SET` callback: Minecraft called `SculkShriekerBlockEntity.preRemoveSideEffects` with `minecraft:moving_piston` as the supplied state. This revision tests a broader lifecycle guard that checks FrozenLib's saved carried-block-entity NBT and only then adds `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` to the landing `Level.setBlock` call. It does not globally change normal block placement/removal, and it does not assume every block entity has a `shrieking`-style property. This is an experiment and still requires a CI build plus in-game regression testing.

## Known limitations

- This is a test harness, not a release-ready replacement mod.
- It uses the FrozenLib API and tag rather than copying FrozenLib implementation code.
- Grindstone is not in the configurable list because it has no block entity and does not reach this event.
- The prototype has not been compiled or tested in Minecraft in the environment where it was generated. The local environment lacks JDK 25 and Gradle; CI is included to compile it in a suitable environment.
- FrozenLib is GPL-3.0-only. Review licensing before publishing or bundling this prototype.

### Minecraft 26.3 compatibility note

The probe uses `ResourceKey.identifier()` for block registry keys, matching the Minecraft 26.3 API. This revision uses MixinExtras to wrap the piston landing call; supported Fabric Loader versions supply MixinExtras at runtime.
