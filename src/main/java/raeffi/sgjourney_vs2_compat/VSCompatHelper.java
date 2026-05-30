package raeffi.sgjourney_vs2_compat;

import org.valkyrienskies.mod.common.VSGameUtilsKt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class VSCompatHelper
{
    private static final boolean VS_LOADED;

    static
    {
        boolean loaded = false;
        try
        {
            Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            loaded = true;
        }
        catch (ClassNotFoundException ignored) {}
        VS_LOADED = loaded;
    }

    private VSCompatHelper() {}

    public static boolean isVSLoaded()      { return VS_LOADED; }

    public static boolean isOnShip(Level level, BlockPos pos)
    {
        if (!VS_LOADED) return false;
        return Internals.isOnShip(level, pos);
    }

    public static Vec3 worldToShipSpace(Level level, BlockPos stargateBlockPos, Vec3 worldPos)
    {
        if (!VS_LOADED) return worldPos;
        return Internals.worldToShipSpace(level, stargateBlockPos, worldPos);
    }

    public static boolean isRidingVSShip(Entity entity)
    {
        if (!VS_LOADED) return false;
        return Internals.isRidingVSShip(entity);
    }

    public static Vec3 worldToShipDirection(Level level, BlockPos stargateBlockPos, Vec3 worldDir)
    {
        if (!VS_LOADED) return worldDir;
        return Internals.worldToShipDirection(level, stargateBlockPos, worldDir);
    }

    public static Vec3 shipToWorldSpace(Level level, BlockPos stargateBlockPos, Vec3 shipPos)
    {
        if (!VS_LOADED) return shipPos;
        return Internals.shipToWorldSpace(level, stargateBlockPos, shipPos);
    }

    public static Vec3 shipToWorldDirection(Level level, BlockPos stargateBlockPos, Vec3 shipDir)
    {
        if (!VS_LOADED) return shipDir;
        return Internals.shipToWorldDirection(level, stargateBlockPos, shipDir);
    }

    // -----------------------------------------------------------------------
    // Real VS2 API imports — only class-loaded when VS2 is actually present.
    // Requires vs-core and valkyrienskies-forge in your compileOnly dependencies.
    // -----------------------------------------------------------------------
    private static final class Internals
    {
        static boolean isOnShip(Level level, BlockPos pos)
        {
            return VSGameUtilsKt.getShipManagingPos(level, pos) != null;
        }

        static Vec3 worldToShipSpace(Level level, BlockPos stargateBlockPos, Vec3 worldPos)
        {
            var ship = VSGameUtilsKt.getShipManagingPos(level, stargateBlockPos);
            if (ship == null) return worldPos;

            var transform = ship.getTransform();
            var result = new org.joml.Vector3d(worldPos.x(), worldPos.y(), worldPos.z());
            transform.getWorldToShip().transformPosition(result);
            return new Vec3(result.x, result.y, result.z);
        }

        static boolean isRidingVSShip(Entity entity)
        {
            Entity vehicle = entity.getVehicle();
            if (vehicle == null) return false;
            // VS2 physics entities live in the org.valkyrienskies package
            return vehicle.getClass().getName().startsWith("org.valkyrienskies");
        }

        static Vec3 worldToShipDirection(Level level, BlockPos stargateBlockPos, Vec3 worldDir)
        {
            var ship = VSGameUtilsKt.getShipManagingPos(level, stargateBlockPos);
            if (ship == null) return worldDir;

            var transform = ship.getTransform();
            var result = new org.joml.Vector3d(worldDir.x(), worldDir.y(), worldDir.z());
            // transformDirection = rotation only, no translation — correct for directions
            transform.getWorldToShip().transformDirection(result);
            return new Vec3(result.x, result.y, result.z);
        }

        static Vec3 shipToWorldSpace(Level level, BlockPos stargateBlockPos, Vec3 shipPos)
        {
            var ship = VSGameUtilsKt.getShipManagingPos(level, stargateBlockPos);
            if (ship == null) return shipPos;

            var transform = ship.getTransform();
            var result = new org.joml.Vector3d(shipPos.x(), shipPos.y(), shipPos.z());
            transform.getShipToWorld().transformPosition(result);
            return new Vec3(result.x, result.y, result.z);
        }

        static Vec3 shipToWorldDirection(Level level, BlockPos stargateBlockPos, Vec3 shipDir)
        {
            var ship = VSGameUtilsKt.getShipManagingPos(level, stargateBlockPos);
            if (ship == null) return shipDir;

            var transform = ship.getTransform();
            var result = new org.joml.Vector3d(shipDir.x(), shipDir.y(), shipDir.z());
            transform.getShipToWorld().transformDirection(result);
            return new Vec3(result.x, result.y, result.z);
        }
    }
}