package raeffi.sgjourney_vs2_compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.povstalec.sgjourney.common.sgjourney.transporter.Transporter;

public class TransportHelper
{
    //Replaced with config File
    public static double MAX_TRANSPORTER_RANGE = 300.0;

    /**
     * Returns the world-space position of a transporter.
     * If it's on a VS ship, converts from ship-space to world-space.
     * If not on a ship (or VS not loaded), returns the position as-is.
     */
    public static Vec3 getWorldSpacePosition(MinecraftServer server, Transporter transporter)
    {
        Vec3 pos = transporter.getPosition(server);
        if (pos == null) return null;

        if (!VSCompatHelper.isVSLoaded()) return pos;

        ServerLevel level = transporter.getLevel(server);
        if (level == null) return pos;

        BlockPos blockPos = BlockPos.containing(pos.x(), pos.y(), pos.z());
        if (!VSCompatHelper.isOnShip(level, blockPos)) return pos;

        // transportPos() is in ship-space — convert the block center to world-space
        return VSCompatHelper.shipToWorldSpace(level, blockPos, pos);
    }

    /**
     * Checks whether two transporters are close enough to connect.
     * Converts both to world-space first so ship/ground/mixed pairs all work.
     */
    public static boolean isWithinRange(MinecraftServer server,
                                        Transporter a, Transporter b)
    {
        if (!a.getDimension().equals(b.getDimension())) return false;

        Vec3 posA = getWorldSpacePosition(server, a);
        Vec3 posB = getWorldSpacePosition(server, b);
        if (posA == null || posB == null) return false;

        return posA.distanceToSqr(posB) <= MAX_TRANSPORTER_RANGE * MAX_TRANSPORTER_RANGE;
    }
}