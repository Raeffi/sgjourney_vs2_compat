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

        Vec3 gateForwardShip = destinationStargate.getForward(destinationLevel.getServer());
        Vec3 gateForwardWorld = VSCompatHelper.shipToWorldDirection(
                destinationLevel, gateBlockPos, gateForwardShip);
        Vec3 gateCenterWorld = VSCompatHelper.shipToWorldSpace(
                destinationLevel, gateBlockPos, gatePos);

        Vec3 worldPosition  = gateCenterWorld.add(gateForwardWorld);
        Vec3 worldMomentum  = VSCompatHelper.shipToWorldDirection(
                destinationLevel, gateBlockPos, destinationMomentum);
        Vec3 worldLookAngle = VSCompatHelper.shipToWorldDirection(
                destinationLevel, gateBlockPos, destinationLookAngle);

        Entity result = recursivePassengerTeleport(destinationLevel, destinationStargate,
                traveler, worldPosition, worldMomentum, worldLookAngle);
        playWormholeSound(destinationLevel, result);

        // Force client position sync for non-player entities.
        // moveTo() sets server-side position but doesn't send packets —
        // VS ships interfere with the normal entity tracker update so
        // clients never see the entity until a relog without this.
        forcePositionSync(destinationLevel, result);

        cir.setReturnValue(result);
    }

    private static void forcePositionSync(ServerLevel level, Entity entity)
    {
        if (entity == null) return;
        if (entity instanceof net.minecraft.server.level.ServerPlayer) return;

        try
        {
            // Send teleport packet to all tracking players
            level.getChunkSource().broadcastAndSend(
                    entity,
                    new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(entity)
            );

            // Force entity tracker refresh via reflection
            var chunkMap = level.getChunkSource().chunkMap;

            // f_140131_ is the SRG name for ChunkMap.entityMap
            java.lang.reflect.Field entityMapField = chunkMap.getClass()
                    .getDeclaredField("f_140131_");
            entityMapField.setAccessible(true);
            var entityMap = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>)
                    entityMapField.get(chunkMap);

            Object trackedEntity = entityMap.get(entity.getId());
            if (trackedEntity == null) return;

            // m_140443_ is the SRG name for TrackedEntity.updatePlayers
            java.lang.reflect.Method updatePlayers = trackedEntity.getClass()
                    .getDeclaredMethod("m_140443_", java.util.List.class);
            updatePlayers.setAccessible(true);
            updatePlayers.invoke(trackedEntity, level.players());
        }
        catch (Exception e)
        {
            // Non-fatal - entity will sync on next tracker update
        }
    }
}