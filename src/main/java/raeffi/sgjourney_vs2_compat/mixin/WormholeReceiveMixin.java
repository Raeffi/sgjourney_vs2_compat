package raeffi.sgjourney_vs2_compat.mixin;

import raeffi.sgjourney_vs2_compat.VSCompatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.povstalec.sgjourney.common.sgjourney.stargate.Stargate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.povstalec.sgjourney.common.sgjourney.Wormhole", remap = false)
public abstract class WormholeReceiveMixin
{
    @Shadow
    public abstract Entity receiveTraveler(ServerLevel destinationLevel, Stargate destinationStargate,
                                           Entity traveler, Vec3 destinationPosition, Vec3 destinationMomentum, Vec3 destinationLookAngle);

    @Shadow
    protected abstract Entity transportEntity(ServerLevel destinationLevel, Stargate destinationStargate,
                                              Entity traveler, Vec3 destinationPosition, Vec3 destinationMomentum, Vec3 destinationLookAngle);

    @Shadow
    protected abstract Entity transportPlayer(ServerLevel destinationLevel, Stargate destinationStargate,
                                              net.minecraft.server.level.ServerPlayer player, Vec3 destinationPosition, Vec3 destinationMomentum, Vec3 destinationLookAngle);

    @Shadow
    protected abstract Entity recursivePassengerTeleport(ServerLevel destinationLevel, Stargate destinationStargate,
                                                         Entity traveler, Vec3 destinationPosition, Vec3 destinationMomentum, Vec3 destinationLookAngle);

    @Shadow
    public static void playWormholeSound(net.minecraft.world.level.Level level, Entity traveler) {}

    @Inject(method = "receiveTraveler", at = @At("HEAD"), cancellable = true)
    private void vsShipReceiveTraveler(
            ServerLevel destinationLevel,
            Stargate destinationStargate,
            Entity traveler,
            Vec3 destinationPosition,
            Vec3 destinationMomentum,
            Vec3 destinationLookAngle,
            CallbackInfoReturnable<Entity> cir)
    {
        if (!VSCompatHelper.isVSLoaded()) return;

        Vec3 gatePos = destinationStargate.getPosition(destinationLevel.getServer());
        if (gatePos == null) return;

        BlockPos gateBlockPos = BlockPos.containing(gatePos.x(), gatePos.y(), gatePos.z());
        if (!VSCompatHelper.isOnShip(destinationLevel, gateBlockPos)) return;

        // Get the gate's forward vector and convert both position and
        // direction from ship-space to world-space
        Vec3 gateForwardShip = destinationStargate.getForward(destinationLevel.getServer());
        Vec3 gateForwardWorld = VSCompatHelper.shipToWorldDirection(
                destinationLevel, gateBlockPos, gateForwardShip);

        // Gate center in world-space
        Vec3 gateCenterWorld = VSCompatHelper.shipToWorldSpace(
                destinationLevel, gateBlockPos, gatePos);

        // Spawn exactly 1 block in front of the gate in world-space,
        // ignoring whatever fromStargateCoords computed for position
        Vec3 worldPosition = gateCenterWorld.add(gateForwardWorld);

        Vec3 worldMomentum  = VSCompatHelper.shipToWorldDirection(
                destinationLevel, gateBlockPos, destinationMomentum);
        Vec3 worldLookAngle = VSCompatHelper.shipToWorldDirection(
                destinationLevel, gateBlockPos, destinationLookAngle);

        Entity result = recursivePassengerTeleport(destinationLevel, destinationStargate,
                traveler, worldPosition, worldMomentum, worldLookAngle);
        playWormholeSound(destinationLevel, result);

        cir.setReturnValue(result);
    }
}