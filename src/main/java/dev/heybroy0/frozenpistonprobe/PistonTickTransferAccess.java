package dev.heybroy0.frozenpistonprobe;

public interface PistonTickTransferAccess {
    void frozenPistonProbe$setTickPayload(PistonTickTransfer.Payload payload);

    PistonTickTransfer.Payload frozenPistonProbe$getTickPayload();

    PistonTickTransfer.Payload frozenPistonProbe$takeTickPayload();
}
