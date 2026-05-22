package raeffi.sgjourney_vs2_compat.mixin;

import raeffi.sgjourney_vs2_compat.VSCompatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.povstalec.sgjourney.common.sgjourney.TransporterConnection;
import net.povstalec.sgjourney.common.sgjourney.transporter.Transporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.povstalec.sgjourney.common.sgjourney.Transporting", remap = false)
public class TransportingMixin
{
    /**
     * Intercepts transportTraveler when the transporter is on a VS ship.
     * Transforms the traveler's world-space position into ship-space before
     * the radius check and coordinate conversion.
     * Falls through to original logic when VS is absent or not on a ship.
     */
    @Inject(method = "transportTraveler", at = @At("HEAD"), cancellable = true)
    private static void vsShipTransportTraveler(
            MinecraftServer server,
            TransporterConnection connection,
            Transporter initialTransporter,
            Transporter receivingTransporter,
            Entity traveler,
            CallbackInfoReturnable<Boolean> cir)
    {
        if (!VSCompatHelper.isVSLoaded()) return;

        ServerLevel level = initialTransporter.getLevel(server);
        Vec3 transporterPos = initialTransporter.getPosition(server);
        if (level == null || transporterPos == null) return;

        BlockPos transporterBlockPos = BlockPos.containing(
                transporterPos.x(), transporterPos.y(), transporterPos.z());

        if (!VSCompatHelper.isOnShip(level, transporterBlockPos)) return;

        Vec3 ringCenterShip = initialTransporter.transportPos(server);
        if (ringCenterShip == null) return;

        // ── Range check ──────────────────────────────────────────────────────
        // Use ship-space so detection works when the ship moves through a
        // stationary entity or the entity is standing on the ship.
        Vec3 shipSpacePos = VSCompatHelper.worldToShipSpace(
                level, transporterBlockPos, traveler.position());
        Vec3 shipSpaceOffset = shipSpacePos.subtract(ringCenterShip);
        Vec3 relativePositionCheck = initialTransporter.toTransporterCoords(
                server, shipSpaceOffset, true);

        double innerRadius = initialTransporter.getInnerRadius();
        if (relativePositionCheck.lengthSqr() > innerRadius * innerRadius)
        {
            cir.setReturnValue(false);
            return;
        }

        // ── Teleport vectors ─────────────────────────────────────────────────
        // Rings use identity basis so toTransporterCoords is a no-op —
        // relative position is just a raw offset added to the receiver's
        // world-space transportPos. We must use world-space here so the
        // receiver (land or ship) gets a correct destination.
        Vec3 ringCenterWorld = VSCompatHelper.shipToWorldSpace(
                level, transporterBlockPos, ringCenterShip);
        Vec3 worldOffset = traveler.position().subtract(ringCenterWorld);
        Vec3 relativePosition = initialTransporter.toTransporterCoords(
                server, worldOffset, true);

        // Momentum and look angle are direction vectors — no translation needed,
        // pass world-space directly since rings use identity basis
        Vec3 relativeMomentum = initialTransporter.toTransporterCoords(
                server, traveler.getDeltaMovement(), false);
        Vec3 relativeLookAngle = initialTransporter.toTransporterCoords(
                server, traveler.getLookAngle(), false);

        if (receivingTransporter.receiveTraveler(server, connection, initialTransporter,
                traveler, relativePosition, relativeMomentum, relativeLookAngle))
        {
//            // Always force sync when teleporting from a ship — the entity tracker
//            // loses sync regardless of whether the destination is a ship or land.
//            // Safe to fire even for land destinations, it's a no-op if nothing changed.
//            ServerLevel destLevel = receivingTransporter.getLevel(server);
//            if (destLevel != null)
//                forcePositionSync(destLevel, traveler);
//
            cir.setReturnValue(true);
            return;
        }
    }
}