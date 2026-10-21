package fr.nocsy.mcpets.bettermob;

import eu.northsoft.bettermob.api.BetterMobAPI;
import eu.northsoft.bettermob.api.MobInfo;
import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.mobs.MobProvider;
import fr.nocsy.mcpets.mobs.MobType;
import fr.nocsy.mcpets.utils.debug.Debugger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * BetterMob implementation. Only loaded when BetterMob is installed.
 * BetterMob registers its API service in its own onEnable, so nothing here touches
 * the API before MCPets' onEnable.
 */
public class BetterMobProvider implements MobProvider {

    private final Map<UUID, Location> stopRegistry = new HashMap<>();

    private Method signalMethod;
    private Object signalTarget;
    private boolean signalResolved;

    public static BetterMobAPI api() {
        return BetterMobAPI.get();
    }

    @Override
    public MobType getType() {
        return MobType.BETTERMOB;
    }

    @Override
    public boolean mobExists(final String mobId) {
        return mobId != null && api().getMob(mobId).isPresent();
    }

    @Override
    public LivingEntity spawn(final String mobId, final Location location) {
        try {
            return api().spawn(mobId, location).orElse(null);
        } catch (final RuntimeException ex) {
            Debugger.send("§cBetterMob mob " + mobId + " could not be spawned: " + ex.getMessage());
            return null;
        }
    }

    @Override
    public boolean manages(final Entity entity) {
        return entity != null && api().isBetterMob(entity);
    }

    @Override
    public boolean skillExists(final String skillId) {
        if (skillId == null) return false;
        return api().getSkillIds().stream().anyMatch(s -> s.equalsIgnoreCase(skillId));
    }

    @Override
    public boolean castSkill(final String skillId, final LivingEntity caster) {
        try {
            return api().runSkill(skillId, caster, caster);
        } catch (final RuntimeException ex) {
            MCPets.getLog().log(Level.WARNING, "BetterMob skill " + skillId + " failed", ex);
            return false;
        }
    }

    /**
     * The public BetterMob API has no signal method yet. This uses BetterMobAPI#signal if a
     * newer BetterMob adds one, and otherwise MobManager#signal behind the API implementation.
     */
    @Override
    public boolean signal(final LivingEntity mob, final String signal, final LivingEntity sender) {
        if (mob == null || signal == null) return false;
        resolveSignal();
        if (signalMethod == null) return false;
        try {
            final Object result = signalMethod.invoke(signalTarget, mob, signal, sender);
            return !(result instanceof Boolean b) || b;
        } catch (final ReflectiveOperationException ex) {
            MCPets.getLog().log(Level.WARNING, "Could not send BetterMob signal " + signal, ex);
            return false;
        }
    }

    private void resolveSignal() {
        if (signalResolved) return;
        signalResolved = true;
        final BetterMobAPI api = api();
        try {
            signalMethod = api.getClass().getMethod("signal", LivingEntity.class, String.class, LivingEntity.class);
            signalTarget = api;
            return;
        } catch (final NoSuchMethodException ignored) {}
        try {
            final Field field = api.getClass().getDeclaredField("manager");
            field.setAccessible(true);
            final Object manager = field.get(api);
            signalMethod = manager.getClass().getMethod("signal", LivingEntity.class, String.class, LivingEntity.class);
            signalTarget = manager;
        } catch (final ReflectiveOperationException | RuntimeException ex) {
            MCPets.getLog().warning("BetterMob signals could not be hooked, the signal stick won't work for BetterMob pets: " + ex);
            signalMethod = null;
            signalTarget = null;
        }
    }

    @Override
    public String displayName(final LivingEntity mob) {
        if (mob == null) return null;
        return api().getMobInfo(mob).map(MobInfo::displayName).orElse(null);
    }

    @Override
    public void setOwner(final LivingEntity mob, final UUID owner) {
        // BetterMob has no owner concept; skills use @PetOwner instead
    }

    @Override
    public void moveTo(final LivingEntity mob, final Location destination) {
        if (mob instanceof Mob pathfinding) {
            pathfinding.getPathfinder().moveTo(destination, 1.0);
        }
    }

    @Override
    public void stop(final LivingEntity mob, final UUID owner) {
        final Location current = mob.getLocation();
        final Location last = stopRegistry.get(owner);
        if (last != null && last.getWorld() == current.getWorld()
                && last.getBlockX() == current.getBlockX()
                && last.getBlockY() == current.getBlockY()
                && last.getBlockZ() == current.getBlockZ()) {
            return;
        }
        if (mob instanceof Mob pathfinding) {
            pathfinding.getPathfinder().stopPathfinding();
        }
        stopRegistry.put(owner, current);
    }

    @Override
    public void remove(final LivingEntity mob) {
        mob.remove();
    }

    @Override
    public void registerComponents(final JavaPlugin plugin) {
        BetterMobComponents.register(plugin);
    }

    @Override
    public void registerListeners(final JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new BetterMobListener(), plugin);
    }

    // ------------------------------------------------------------------ param helpers for skill lines

    /** Parameter lookup with aliases. BetterMob lower-cases all keys. */
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
        return value == null ? def : Boolean.parseBoolean(value.trim());
    }
}
