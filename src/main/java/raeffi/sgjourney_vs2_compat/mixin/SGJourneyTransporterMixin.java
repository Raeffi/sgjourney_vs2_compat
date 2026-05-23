package raeffi.sgjourney_vs2_compat.mixin;

import raeffi.sgjourney_vs2_compat.VSCompatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.povstalec.sgjourney.common.sgjourney.TransporterConnection;
import net.povstalec.sgjourney.common.sgjourney.Transporting;
import net.povstalec.sgjourney.common.sgjourney.transporter.Transporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.povstalec.sgjourney.common.sgjourney.transporter.SGJourneyTransporter", remap = false)
public class SGJourneyTransporterMixin
{
    /**
     * When this (destination) transporter is on a VS ship, transportPos()
     * returns ship-space coordinates. The relative position we receive is
     * world-space, so adding them produces garbage coordinates.
     * Convert transportPos to world-space first.
     */
    @Inject(method = "receiveTraveler", at = @At("HEAD"), cancellable = true)
    private void vsShipReceiveTraveler(
            MinecraftServer server,
            TransporterConnection connection,
            Transporter sendingTransporter,
            Entity traveler,
            Vec3 relativePosition,
            Vec3 relativeMomentum,
            Vec3 relativeLookAngle,
            CallbackInfoReturnable<Boolean> cir)
    {
        if (!VSCompatHelper.isVSLoaded()) return;

        Transporter self = (Transporter)(Object) this;

        ServerLevel level = self.getLevel(server);
        Vec3 thisPos = self.getPosition(server);
        if (level == null || thisPos == null) return;

        BlockPos blockPos = BlockPos.containing(thisPos.x(), thisPos.y(), thisPos.z());
        if (!VSCompatHelper.isOnShip(level, blockPos)) return;

        // transportPos() is ship-space — convert to world-space
        Vec3 transportPosShip = self.transportPos(server);
        if (transportPosShip == null) return;

        Vec3 transportPosWorld = VSCompatHelper.shipToWorldSpace(
                level, blockPos, transportPosShip);

        // Recompute destination with world-space center
        // fromTransporterCoords uses identity basis so it's a no-op,
        // but call it anyway to stay consistent with original logic
        Vec3 destinationPosition = transportPosWorld.add(
                self.fromTransporterCoords(server, relativePosition, true));
        Vec3 destinationMomentum = self.fromTransporterCoords(
                server, relativeMomentum, false);
        Vec3 destinationLookAngle = self.fromTransporterCoords(
                server, relativeLookAngle, false);

        Transporting.receiveTraveler(level, self, traveler,
                destinationPosition, destinationMomentum, destinationLookAngle);

        // moveTo() only updates server-side position — without this the client
        // interpolates from the old position to the new one over several ticks.
        // Players handle their own sync via teleportTo, only needed for others.
        if (!(traveler instanceof net.minecraft.server.level.ServerPlayer))
        {
            level.getChunkSource().broadcastAndSend(
                    traveler,
            );
        }

        cir.setReturnValue(true);
    }
}