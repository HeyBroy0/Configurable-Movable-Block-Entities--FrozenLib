# Resolver Stage 1 checkpoint (patch bundle)

Target branch: `Configurable-Policy-GUI-Test-1`
Purpose: replace manual companion insertion in `PistonStructureResolverMixin` with FrozenLib's
`DETERMINE_BLOCK_STICKINESS` and `TRY_STICK_BLOCKS_TOGETHER` resolver events.

## Contents
- `src/main/java/dev/heybroy0/frozenpistonprobe/FrozenPistonProbe.java` — registers the new helper.
- `src/main/java/dev/heybroy0/frozenpistonprobe/MultiBlockStickiness.java` — new resolver-event integration.
- `src/main/resources/frozen_piston_probe.mixins.json` — removes the resolver Mixin entry.
- `REMOVALS.md` — describes the old resolver Mixin to delete.

## Apply
Copy the included files into the matching paths on `Configurable-Policy-GUI-Test-1`, then delete:
`src/main/java/dev/heybroy0/frozenpistonprobe/mixin/PistonStructureResolverMixin.java`

This is a *patch/checkpoint bundle*, not a complete repository archive. The connected GitHub integration is read-only, and this environment could not download a full GitHub source archive. No remote files were modified.

## Important caveats
- This is a proposed source change and has not been compiled or run in Minecraft.
- It intentionally handles only configured MOVABLE states with `BED_PART` or `DOUBLE_BLOCK_HALF` properties. That includes doors and beds, but can include other double-block plants if explicitly configured MOVABLE.
- FrozenLib event direction semantics are taken from its documented callback contract: direction is from `previousState` to `nextState`.
- The code should be compile-checked against the exact pinned FrozenLib artifact before in-game testing. In particular, confirm that the pinned artifact exposes the same event API as the current FrozenLib source.
- This only fixes resolver membership/obstruction processing. It does not fix the separate landing-time companion/support update issue (Stage 2).
