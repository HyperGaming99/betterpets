package fr.nocsy.mcpets.bettermob;

import eu.northsoft.bettermob.api.BetterMobAPI;
import eu.northsoft.bettermob.api.MobInfo;
import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.utils.debug.Debugger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Single access point to BetterMob for the whole plugin.
 * Replaces every direct MythicMobs call MCPets used to make.
 */
public final class BetterMobHook {

    private static final Map<UUID, Location> STOP_REGISTRY = new HashMap<>();

    private static Method signalMethod;
    private static Object signalTarget;
    private static boolean signalResolved;

    private BetterMobHook() {}

    /**
     * Whether BetterMob is installed and its API service is registered.
     */
    public static boolean isAvailable() {
        try {
            Class.forName("eu.northsoft.bettermob.api.BetterMobAPI");
            return Bukkit.getServicesManager().getRegistration(BetterMobAPI.class) != null;
        } catch (final ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    public static BetterMobAPI api() {
        return BetterMobAPI.get();
    }

    /**
     * Does a BetterMob mob with this id exist?
     */
    public static boolean mobExists(final String id) {
        return id != null && api().getMob(id).isPresent();
    }

    /**
     * Does a BetterMob skill with this id exist?
     */
    public static boolean skillExists(final String id) {
        if (id == null) return false;
        return api().getSkillIds().stream().anyMatch(s -> s.equalsIgnoreCase(id));
    }

    /**
     * Spawn a BetterMob mob, or null if it could not be spawned.
     */
    public static LivingEntity spawn(final String id, final Location location) {
        return api().spawn(id, location).orElse(null);
    }

    /**
     * Display name configured for the BetterMob mob behind this entity, or null.
     */
    public static String displayName(final Entity entity) {
        if (entity == null) return null;
        return api().getMobInfo(entity).map(MobInfo::displayName).orElse(null);
    }

    /**
     * Run a BetterMob skill with the pet as caster and the pet as trigger.
     * Mirrors the old MythicMobs "SkillTriggers.CUSTOM" call with the mob as trigger.
     * @return true if the skill exists and was started
     */
    public static boolean castSkill(final String skillId, final LivingEntity caster) {
        return castSkill(skillId, caster, caster);
    }

    public static boolean castSkill(final String skillId, final LivingEntity caster, final LivingEntity trigger) {
        if (skillId == null || skillId.isEmpty() || caster == null) return false;
        try {
            return api().runSkill(skillId, caster, trigger);
        } catch (final RuntimeException ex) {
            MCPets.getLog().log(Level.WARNING, "BetterMob skill " + skillId + " failed", ex);
            return false;
        }
    }

    /**
     * Send a signal to the mob, so its ~onSignal:NAME skills run.
     * The public BetterMob API has no signal method yet, so this uses
     * BetterMobAPI#signal if a future version adds it, and otherwise the internal MobManager.
     */
    public static boolean signal(final LivingEntity entity, final String signal, final LivingEntity sender) {
        if (entity == null || signal == null) return false;
        resolveSignal();
        if (signalMethod == null) {
            Debugger.send("§c[BetterMob] Signals are not supported by this BetterMob version.");
            return false;
        }
        try {
            final Object result = signalMethod.invoke(signalTarget, entity, signal, sender);
            return !(result instanceof Boolean b) || b;
        } catch (final ReflectiveOperationException ex) {
            MCPets.getLog().log(Level.WARNING, "Could not send BetterMob signal " + signal, ex);
            return false;
        }
    }

    private static void resolveSignal() {
        if (signalResolved) return;
        signalResolved = true;
        final BetterMobAPI api = api();
        // Preferred: an official API method, if BetterMob adds one
        try {
            signalMethod = api.getClass().getMethod("signal", LivingEntity.class, String.class, LivingEntity.class);
            signalTarget = api;
            return;
        } catch (final NoSuchMethodException ignored) {}
        // Fallback: BetterMobApiImpl#manager -> MobManager#signal(LivingEntity, String, LivingEntity)
        try {
            final Field field = api.getClass().getDeclaredField("manager");
            field.setAccessible(true);
            final Object manager = field.get(api);
            signalMethod = manager.getClass().getMethod("signal", LivingEntity.class, String.class, LivingEntity.class);
            signalTarget = manager;
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            MCPets.getLog().warning("BetterMob signals could not be hooked: " + ex.getMessage());
            signalMethod = null;
            signalTarget = null;
        }
    }

    /**
     * Make the entity walk to the destination (vanilla pathfinder).
     */
    public static void moveTo(final LivingEntity entity, final Location destination) {
        if (entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(destination, 1.0);
        }
    }

    /**
     * Stop the entity where it is. Does nothing if it is already stopped at this block.
     */
    public static void stop(final LivingEntity entity, final UUID owner) {
        final Location current = entity.getLocation();
        final Location last = STOP_REGISTRY.get(owner);
        if (last != null && last.getWorld() == current.getWorld()
                && last.getBlockX() == current.getBlockX()
                && last.getBlockY() == current.getBlockY()
                && last.getBlockZ() == current.getBlockZ()) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
        }
        STOP_REGISTRY.put(owner, current);
    }

    /**
     * Lower-case parameter lookup with aliases, as BetterMob lower-cases all keys.
     */
    public static String param(final Map<String, String> params, final String def, final String... keys) {
        for (final String key : keys) {
            final String value = params.get(key.toLowerCase(Locale.ROOT));
            if (value != null) return value;
        }
        return def;
    }

    public static double paramDouble(final Map<String, String> params, final double def, final String... keys) {
        final String value = param(params, null, keys);
        if (value == null) return def;
        try {
            return Double.parseDouble(value.trim());
        } catch (final NumberFormatException e) {
            return def;
        }
    }

    public static boolean paramBoolean(final Map<String, String> params, final boolean def, final String... keys) {
        final String value = param(params, null, keys);
        if (value == null) return def;
        return Boolean.parseBoolean(value.trim());
    }
}
