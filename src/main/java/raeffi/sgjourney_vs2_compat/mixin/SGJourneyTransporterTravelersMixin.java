package raeffi.sgjourney_vs2_compat.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.povstalec.sgjourney.common.sgjourney.TransporterConnection;
import net.povstalec.sgjourney.common.sgjourney.transporter.Transporter;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import raeffi.sgjourney_vs2_compat.TransportHelper;

import java.util.List;

@Mixin(targets = "net.povstalec.sgjourney.common.sgjourney.transporter.SGJourneyTransporter", remap = false)
public class SGJourneyTransporterTravelersMixin
{
    @Inject(method = "transportTravelers", at = @At("HEAD"), cancellable = true)
    private void rangeCheckBeforeTransport(
            MinecraftServer server,
            TransporterConnection connection,
            Transporter receivingTransporter,
            List<Entity> travelers,
            CallbackInfo ci)
    {
        Transporter self = (Transporter)(Object) this;

        if (TransportHelper.isWithinRange(server, self, receivingTransporter)) return;

        Component message = Component.literal("Transport failed: target is out of range (max "
                        + (int)TransportHelper.MAX_TRANSPORTER_RANGE + " blocks)")
                .withStyle(ChatFormatting.RED);

        // Notify travelers in the beam
        for (Entity traveler : travelers)
        {
            if (traveler instanceof ServerPlayer player)
                player.displayClientMessage(message, true);
        }

        // Notify nearby players not in the beam
        ServerLevel level = self.getLevel(server);
        Vec3 pos = TransportHelper.getWorldSpacePosition(server, self);
        if (level != null && pos != null)
        {
            level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos, pos).inflate(8))
                    .stream()
                    .filter(p -> !travelers.contains(p))
                    .forEach(p -> p.displayClientMessage(message, true));
        }

        ci.cancel();
    }
}