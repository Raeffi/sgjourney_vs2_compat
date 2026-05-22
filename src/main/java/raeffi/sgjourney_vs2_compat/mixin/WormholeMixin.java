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
        Vec3 gatePos = initialStargate.getPosition(server);
        if (level == null || gatePos == null) return;

        BlockPos gateBlockPos = BlockPos.containing(gatePos.x(), gatePos.y(), gatePos.z());
        if (!VSCompatHelper.isOnShip(level, gateBlockPos)) return;

        // Fix 2: Skip entities that just teleported through this gate
        long currentTime = level.getGameTime();
        Long lastTeleport = TELEPORT_COOLDOWN.get(traveler.getId());
        if (lastTeleport != null)
        {
            if (currentTime - lastTeleport < COOLDOWN_TICKS)
            {
                // Still in cooldown — just update position and bail
                Vec3 shipSpacePos = VSCompatHelper.worldToShipSpace(level, gateBlockPos, traveler.position());
                Vec3 relPos = initialStargate.toStargateCoords(server, shipSpacePos.subtract(gatePos), true);
                entityLocations.put(traveler.getId(), relPos);
                cir.setReturnValue(false);
                return;
            }
            else
            {
                TELEPORT_COOLDOWN.remove(traveler.getId());
            }
        }

        // Regular passengers (e.g. riding a minecart) still can't enter
        if (traveler.isPassenger() && !VSCompatHelper.isRidingVSShip(traveler))
        {
            Vec3 shipSpacePos = VSCompatHelper.worldToShipSpace(level, gateBlockPos, traveler.position());
            entityLocations.put(traveler.getId(),
                    initialStargate.toStargateCoords(server, shipSpacePos.subtract(gatePos), true));
            cir.setReturnValue(false);
            return;
        }

        // Transform traveler position into ship-space
        Vec3 shipSpacePos = VSCompatHelper.worldToShipSpace(level, gateBlockPos, traveler.position());
        Vec3 relativePosition = initialStargate.toStargateCoords(server, shipSpacePos.subtract(gatePos), true);
        Vec3 oldRelativePos = this.entityLocations.get(traveler.getId());

        // Fix 3: If first tick near the gate, estimate the previous position
        // using the traveler's momentum so we don't miss fast-moving ships
        if (oldRelativePos == null)
        {
            Vec3 relativeDelta = initialStargate.toStargateCoords(
                    server, traveler.getDeltaMovement(), false);
            oldRelativePos = relativePosition.subtract(relativeDelta);
        }

        Vec3 relativeMomentum = relativePosition.subtract(oldRelativePos);

        boolean withinRadius = gatePos.distanceToSqr(shipSpacePos) <= Wormhole.INNER_RADIUS_SQR;
        boolean crossedPlane = oldRelativePos.x() > 0 && relativePosition.x() < 0 && relativeMomentum.x() < 0;

        if (withinRadius && crossedPlane)
        {
            Wormhole.playWormholeSound(traveler.level(), traveler);

            if (twoWayWormhole == StargateInfo.WormholeTravel.ENABLED ||
                    (twoWayWormhole == StargateInfo.WormholeTravel.CREATIVE_ONLY &&
                            traveler instanceof Player player &&
                            (player.isCreative() || player.isSpectator())))
            {
                // Force a fixed exit offset rather than clamping.
                // The natural x value varies wildly depending on ship speed —
                // a fast ship gives x = -2.0, a slow one gives x = -0.05.
                // Forcing -0.5 always places the player half a block in front
                // of the destination gate regardless of crossing speed.
                Vec3 safeRelativePosition = new Vec3(
                        -0.5,
                        relativePosition.y(),
                        relativePosition.z()
                );

                // Derive exit look angle from the crossing direction rather than
                // the player's actual look angle.
                // When a ship sweeps through a stationary player, their look angle
                // is unrelated to the gate — using it produces a reversed exit angle.
                // relativeMomentum.x() is always negative here (crossing condition),
                // so normalizing gives approx (-1, 0, 0) in gate-relative space.
                // At the destination gate, fromStargateCoords with mirror=true flips
                // this to +forward, meaning the player faces away from the gate. ✓
                Vec3 relativeLookAngle;
                if (relativeMomentum.length() > 0.001)
                    relativeLookAngle = relativeMomentum.normalize();
                else
                    relativeLookAngle = new Vec3(-1, 0, 0);

                if (!SGJourneyEvents.onWormholeTravel(server, initialStargate, destinationStargate, traveler, twoWayWormhole) &&
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
}