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

        // Transform traveler's world-space position into ship-space
        Vec3 shipSpacePos = VSCompatHelper.worldToShipSpace(
                level, transporterBlockPos, traveler.position());

        // transportPos() is the center of the ring platform (where entities land),
        // also in ship-space since it comes from a BlockPos
        Vec3 ringCenter = initialTransporter.transportPos(server);
        if (ringCenter == null) return;

        Vec3 relativePosition = initialTransporter.toTransporterCoords(
                server, shipSpacePos.subtract(ringCenter), true);

        // Transform momentum and look angle — direction vectors need rotation only
        Vec3 shipSpaceMomentum = VSCompatHelper.worldToShipDirection(
                level, transporterBlockPos, traveler.getDeltaMovement());
        Vec3 relativeMomentum = initialTransporter.toTransporterCoords(
                server, shipSpaceMomentum, false);

        Vec3 shipSpaceLook = VSCompatHelper.worldToShipDirection(
                level, transporterBlockPos, traveler.getLookAngle());
        Vec3 relativeLookAngle = initialTransporter.toTransporterCoords(
                server, shipSpaceLook, false);

        double innerRadius = initialTransporter.getInnerRadius();
        if (relativePosition.lengthSqr() <= innerRadius * innerRadius)
        {
            if (receivingTransporter.receiveTraveler(
                    server, connection, initialTransporter,
                    traveler, relativePosition, relativeMomentum, relativeLookAngle))
            {
                cir.setReturnValue(true);
                return;
            }
        }

        cir.setReturnValue(false);
    }
}