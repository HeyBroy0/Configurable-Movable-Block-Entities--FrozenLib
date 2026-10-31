# Removal in Stage 1

Delete:
`src/main/java/dev/heybroy0/frozenpistonprobe/mixin/PistonStructureResolverMixin.java`

Also remove `"PistonStructureResolverMixin"` from `frozen_piston_probe.mixins.json`.

Reason: that Mixin manually appends companion positions after vanilla resolution and then only accepts replaceable destinations. This bypasses the resolver's normal push/destroy logic. The new event callbacks let FrozenLib/vanilla resolver logic discover companion blocks through its existing stickiness integration.
