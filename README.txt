Target branch: Configurable-Movement-fix

Merge this `src` folder into the repository root and replace:
`src/main/java/dev/heybroy0/frozenpistonprobe/MultiBlockStickiness.java`

No initializer change is needed. This branch already calls
`MultiBlockStickiness.register()` from `FrozenPistonProbe.java`.

The replacement adds adhesion between explicitly MOVABLE multiblock states
(using BED_PART or DOUBLE_BLOCK_HALF) and vanilla slime/honey blocks. The
existing companion-half check is retained. Other pairs involving one of
these multiblock states remain rejected; pairs not involving one still pass
through to vanilla and other FrozenLib callbacks.

This is a source replacement only; it has not been compiled or runtime-tested.
