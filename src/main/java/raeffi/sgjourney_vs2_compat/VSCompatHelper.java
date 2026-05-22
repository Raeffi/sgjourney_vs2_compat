package raeffi.sgjourney_vs2_compat;

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

    public static boolean isVSLoaded()
    {
        return VS_LOADED;
    }

    /**
     * Returns true if the block at pos is managed by a VS ship.
     */
    public static boolean isOnShip(Level level, BlockPos pos)
    {
        if (!VS_LOADED) return false;
        return Internals.isOnShip(level, pos);
    }

    /**
     * Converts a world-space position into the ship-space of whatever ship
     * is managing the block at stargateBlockPos. No-op if VS is absent or
     * the block is not on a ship.
     */
    public static Vec3 worldToShipSpace(Level level, BlockPos stargateBlockPos, Vec3 worldPos)
    {
        if (!VS_LOADED) return worldPos;
        return Internals.worldToShipSpace(level, stargateBlockPos, worldPos);
    }

    /**
     * Returns true if the entity is riding a VS ship's physics entity
     * rather than a normal Minecraft entity.
     */
    public static boolean isRidingVSShip(Entity entity)
    {
        if (!VS_LOADED) return false;
        return Internals.isRidingVSShip(entity);
    }

    /**
     * Rotates a world-space direction vector into ship-space.
     * Unlike worldToShipSpace, this does NOT apply translation —
     * correct for velocity and look angle vectors.
     */
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
    // Inner class isolates VS imports — only class-loaded when VS is present
    // -----------------------------------------------------------------------
    private static final class Internals
    {
        static boolean isOnShip(Level level, BlockPos pos)
        {
            try
            {
                if (getShipManagingPos == null)
                {
                    Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                    getShipManagingPos = utils.getMethod("getShipManagingPos", Level.class, BlockPos.class);
                }
                return getShipManagingPos.invoke(null, level, pos) != null;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        static Vec3 worldToShipSpace(Level level, BlockPos stargateBlockPos, Vec3 worldPos)
        {
            try
            {
                if (getShipManagingPos == null)
                {
                    Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                    getShipManagingPos = utils.getMethod("getShipManagingPos", Level.class, BlockPos.class);
                }

                Object ship = getShipManagingPos.invoke(null, level, stargateBlockPos);
                if (ship == null) return worldPos;

                if (getTransform == null)
                    getTransform = ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);

                if (getWorldToShip == null)
                    getWorldToShip = transform.getClass().getMethod("getWorldToShip");
                Object matrix = getWorldToShip.invoke(transform);

                Class<?> vector3dClass = Class.forName("org.joml.Vector3d");
                if (transformPosition == null)
                    transformPosition = matrix.getClass().getMethod("transformPosition", vector3dClass);

                Object vec = vector3dClass
                        .getConstructor(double.class, double.class, double.class)
                        .newInstance(worldPos.x(), worldPos.y(), worldPos.z());

                transformPosition.invoke(matrix, vec);

                double x = (double) vector3dClass.getMethod("x").invoke(vec);
                double y = (double) vector3dClass.getMethod("y").invoke(vec);
                double z = (double) vector3dClass.getMethod("z").invoke(vec);

                return new Vec3(x, y, z);
            }
            catch (Exception e)
            {
                return worldPos;
            }
        }

        static boolean isRidingVSShip(Entity entity)
        {
            Entity vehicle = entity.getVehicle();
            if (vehicle == null) return false;
            return vehicle.getClass().getName().startsWith("org.valkyrienskies");
        }

        static Vec3 worldToShipDirection(Level level, BlockPos stargateBlockPos, Vec3 worldDir)
        {
            try
            {
                if (getShipManagingPos == null)
                {
                    Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                    getShipManagingPos = utils.getMethod("getShipManagingPos", Level.class, BlockPos.class);
                }

                Object ship = getShipManagingPos.invoke(null, level, stargateBlockPos);
                if (ship == null) return worldDir;

                if (getTransform == null)
                    getTransform = ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);

                if (getWorldToShip == null)
                    getWorldToShip = transform.getClass().getMethod("getWorldToShip");
                Object matrix = getWorldToShip.invoke(transform);

                Class<?> vector3dClass = Class.forName("org.joml.Vector3d");

                // transformDirection applies rotation+scale but NOT translation — correct for directions
                if (transformDirection == null)
                    transformDirection = matrix.getClass().getMethod("transformDirection", vector3dClass);

                Object vec = vector3dClass
                        .getConstructor(double.class, double.class, double.class)
                        .newInstance(worldDir.x(), worldDir.y(), worldDir.z());

                transformDirection.invoke(matrix, vec);

                double x = (double) vector3dClass.getMethod("x").invoke(vec);
                double y = (double) vector3dClass.getMethod("y").invoke(vec);
                double z = (double) vector3dClass.getMethod("z").invoke(vec);

                return new Vec3(x, y, z);
            }
            catch (Exception e)
            {
                return worldDir;
            }
        }

        static Vec3 shipToWorldSpace(Level level, BlockPos stargateBlockPos, Vec3 shipPos)
        {
            try
            {
                if (getShipManagingPos == null)
                {
                    Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                    getShipManagingPos = utils.getMethod("getShipManagingPos", Level.class, BlockPos.class);
                }

                Object ship = getShipManagingPos.invoke(null, level, stargateBlockPos);
                if (ship == null) return shipPos;

                if (getTransform == null)
                    getTransform = ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);

                // Ship-to-world is the inverse of world-to-ship
                if (getShipToWorld == null)
                    getShipToWorld = transform.getClass().getMethod("getShipToWorld");
                Object matrix = getShipToWorld.invoke(transform);

                Class<?> vector3dClass = Class.forName("org.joml.Vector3d");
                if (transformPosition == null)
                    transformPosition = matrix.getClass().getMethod("transformPosition", vector3dClass);

                Object vec = vector3dClass
                        .getConstructor(double.class, double.class, double.class)
                        .newInstance(shipPos.x(), shipPos.y(), shipPos.z());

                transformPosition.invoke(matrix, vec);

                double x = (double) vector3dClass.getMethod("x").invoke(vec);
                double y = (double) vector3dClass.getMethod("y").invoke(vec);
                double z = (double) vector3dClass.getMethod("z").invoke(vec);

                return new Vec3(x, y, z);
            }
            catch (Exception e)
            {
                return shipPos;
            }
        }

        static Vec3 shipToWorldDirection(Level level, BlockPos stargateBlockPos, Vec3 shipDir)
        {
            try
            {
                if (getShipManagingPos == null)
                {
                    Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                    getShipManagingPos = utils.getMethod("getShipManagingPos", Level.class, BlockPos.class);
                }

                Object ship = getShipManagingPos.invoke(null, level, stargateBlockPos);
                if (ship == null) return shipDir;

                if (getTransform == null)
                    getTransform = ship.getClass().getMethod("getTransform");
                Object transform = getTransform.invoke(ship);

                if (getShipToWorld == null)
                    getShipToWorld = transform.getClass().getMethod("getShipToWorld");
                Object matrix = getShipToWorld.invoke(transform);

                Class<?> vector3dClass = Class.forName("org.joml.Vector3d");
                if (transformDirection == null)
                    transformDirection = matrix.getClass().getMethod("transformDirection", vector3dClass);

                Object vec = vector3dClass
                        .getConstructor(double.class, double.class, double.class)
                        .newInstance(shipDir.x(), shipDir.y(), shipDir.z());

                transformDirection.invoke(matrix, vec);

                double x = (double) vector3dClass.getMethod("x").invoke(vec);
                double y = (double) vector3dClass.getMethod("y").invoke(vec);
                double z = (double) vector3dClass.getMethod("z").invoke(vec);

                return new Vec3(x, y, z);
            }
            catch (Exception e)
            {
                return shipDir;
            }
        }

        // Cached reflected methods - looked up once, reused every call
        private static java.lang.reflect.Method getShipManagingPos;
        private static java.lang.reflect.Method getTransform;
        private static java.lang.reflect.Method getWorldToShip;
        private static java.lang.reflect.Method transformPosition;
        private static java.lang.reflect.Method transformDirection;
        private static java.lang.reflect.Method getShipToWorld;
    }
}