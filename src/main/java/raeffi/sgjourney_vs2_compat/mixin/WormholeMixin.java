package raeffi.sgjourney_vs2_compat.mixin;

import raeffi.sgjourney_vs2_compat.VSCompatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.povstalec.sgjourney.common.events.custom.SGJourneyEvents;
import net.povstalec.sgjourney.common.sgjourney.StargateConnection;
import net.povstalec.sgjourney.common.sgjourney.StargateInfo;
import net.povstalec.sgjourney.common.sgjourney.Wormhole;
import net.povstalec.sgjourney.common.sgjourney.stargate.Stargate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "net.povstalec.sgjourney.common.sgjourney.Wormhole", remap = false)
public abstract class WormholeMixin
{
    @Shadow protected Map<Integer, Vec3> entityLocations;
    @Shadow protected abstract void deconstructEvent(MinecraftServer server, Stargate initialStargate, Entity traveler, boolean disintegrated);
    @Shadow public abstract void handleReverseWormhole(MinecraftServer server, Stargate initialStargate, Entity traveler);

    // Cooldown after teleport — entity ID → game time of teleport
    // Prevents instant re-teleport on the destination side
    private static final Map<Integer, Long> TELEPORT_COOLDOWN = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TICKS = 10L;

    @Inject(method = "wormholeEntity", at = @At("HEAD"), cancellable = true)
    private void vsShipWormholeEntity(
            MinecraftServer server,
            StargateConnection connection,
            Stargate initialStargate,
            Stargate destinationStargate,
            StargateInfo.WormholeTravel twoWayWormhole,
            Map<Integer, Vec3> entityLocations,
            Entity traveler,
            CallbackInfoReturnable<Boolean> cir)
    {
        if (!VSCompatHelper.isVSLoaded()) return;

        ServerLevel level = initialStargate.getLevel(server);

        Vec3 gateShipPos = initialStargate.getPosition(server);
        if (level == null || gateShipPos == null) return;

        BlockPos gateShipBlockPos = BlockPos.containing(gateShipPos.x(), gateShipPos.y(), gateShipPos.z());
        if (!VSCompatHelper.isOnShip(level, gateShipBlockPos)) return;

        Vec3 gateWorldPos = VSCompatHelper.shipToWorldSpace(level, gateShipBlockPos, gateShipPos);

        long currentTime = level.getGameTime();
        Long lastTeleport = TELEPORT_COOLDOWN.get(traveler.getId());
        if (lastTeleport != null)
        {
            if (currentTime - lastTeleport < COOLDOWN_TICKS)
            {
                Vec3 relPos = toWorldSpaceStargateCoords(level, gateShipBlockPos, initialStargate, server,
                        traveler.position().subtract(gateWorldPos), true);
                entityLocations.put(traveler.getId(), relPos);
                cir.setReturnValue(false);
                return;
            }
            else
            {
                TELEPORT_COOLDOWN.remove(traveler.getId());
            }
        }

        if (traveler.isPassenger() && !VSCompatHelper.isRidingVSShip(traveler))
        {
            Vec3 relPos = toWorldSpaceStargateCoords(level, gateShipBlockPos, initialStargate, server,
                    traveler.position().subtract(gateWorldPos), true);
            entityLocations.put(traveler.getId(), relPos);
            cir.setReturnValue(false);
            return;
        }

        Vec3 worldOffset  = traveler.position().subtract(gateWorldPos);
        Vec3 centerOffset = traveler.getBoundingBox().getCenter().subtract(gateWorldPos);

        Vec3 relativePosition = toWorldSpaceStargateCoords(
                level, gateShipBlockPos, initialStargate, server, worldOffset, true);
        Vec3 oldRelativePos = this.entityLocations.get(traveler.getId());

        if (oldRelativePos == null)
        {
            // First tick we see this entity — store position and wait for next tick.
            // getDeltaMovement() doesn't include walking velocity so we can't use it.
            entityLocations.put(traveler.getId(), relativePosition);
            cir.setReturnValue(false);
            return;
        }

        Vec3 relativeMomentum = relativePosition.subtract(oldRelativePos);

        boolean withinRadius = centerOffset.lengthSqr() <= Wormhole.INNER_RADIUS_SQR;
        boolean crossedPlane = oldRelativePos.x() > 0
                && relativePosition.x() < 0
                && relativeMomentum.x() < 0;

        if (withinRadius && crossedPlane)
        {
            Wormhole.playWormholeSound(traveler.level(), traveler);

            if (twoWayWormhole == StargateInfo.WormholeTravel.ENABLED ||
                    (twoWayWormhole == StargateInfo.WormholeTravel.CREATIVE_ONLY &&
                            traveler instanceof Player player &&
                            (player.isCreative() || player.isSpectator())))
            {
                Vec3 safeRelativePosition = new Vec3(-0.5, relativePosition.y(), relativePosition.z());

                Vec3 relativeLookAngle;
                if (relativeMomentum.length() > 0.001)
                    relativeLookAngle = relativeMomentum.normalize();
                else
                    relativeLookAngle = new Vec3(-1, 0, 0);

                if (!SGJourneyEvents.onWormholeTravel(server, initialStargate, destinationStargate,
                        traveler, twoWayWormhole) &&
                        destinationStargate.receiveTraveler(server, connection, initialStargate, traveler,
                                safeRelativePosition, relativeMomentum, relativeLookAngle) != null)
                {
                    TELEPORT_COOLDOWN.put(traveler.getId(), currentTime);
                    deconstructEvent(server, initialStargate, traveler, false);
                    cir.setReturnValue(true);
                    return;
                }
            }
            else
            {
                handleReverseWormhole(server, initialStargate, traveler);
            }
        }

        entityLocations.put(traveler.getId(), relativePosition);
        cir.setReturnValue(false);
    }

    /**
     * Equivalent to Stargate.toStargateCoords() but rotates the gate's basis vecto

    /**
     * Converts a world-space entity position into gate-local coordinates,
     * correctly accounting for the ship's current rotation.
     *
     * The normal path (toStargateCoords) rotates using the gate's baked
     * ship-local facing direction, which is wrong once the ship has rotated.
     * Instead we:
     *   1. Compute the world-space offset from the gate origin.
     *   2. Rotate that offset from world space into ship space using the
     *      ship's live world→ship transform.
     *   3. Feed the now-ship-local offset into toStargateCoords, which then
     *      applies the gate's own facing rotation — and those are consistent
     *      because both are now in the same (ship-local) frame.
     */
    private Vec3 worldOffsetToGateLocal(
            ServerLevel level,
            BlockPos gateBlockPos,
            Vec3 gateWorldPos,
            Vec3 entityWorldPos,
            MinecraftServer server,
            Stargate initialStargate)
    {
        // Step 1: world-space offset
        Vec3 worldOffset = entityWorldPos.subtract(gateWorldPos);

        // Step 2: rotate into ship space (direction only — no translation)
        Vec3 shipLocalOffset = VSCompatHelper.worldToShipDirection(level, gateBlockPos, worldOffset);

        // Step 3: apply gate's own facing rotation (ship-local → gate-local)
        return initialStargate.toStargateCoords(server, shipLocalOffset, true);
    }

    /**
     * Equivalent to Stargate.toStargateCoords() but rotates the gate's basis vectors
     * from ship-local space into world space first, so it works correctly at any
     * ship rotation.
     */
    private Vec3 toWorldSpaceStargateCoords(ServerLevel level, BlockPos gateShipBlockPos,
                                            Stargate gate, MinecraftServer server, Vec3 worldOffset, boolean scale)
    {
        Vec3 worldFwd   = VSCompatHelper.shipToWorldDirection(level, gateShipBlockPos, gate.getForward(server));
        Vec3 worldUp    = VSCompatHelper.shipToWorldDirection(level, gateShipBlockPos, gate.getUp(server));
        Vec3 worldRight = VSCompatHelper.shipToWorldDirection(level, gateShipBlockPos, gate.getRight(server));

        double x = worldOffset.dot(worldFwd);
        double y = worldOffset.dot(worldUp);
        double z = worldOffset.dot(worldRight);

        if (scale)
        {
            double r = gate.getInnerRadius();
            return new Vec3(x, y / r, z / r);
        }
        return new Vec3(x, y, z);
    }
}