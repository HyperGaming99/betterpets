package fr.nocsy.mcpets.mobs;

import fr.nocsy.mcpets.MCPets;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Keeps the mob providers that are installed on this server.
 * MythicMobs, BetterMob or both can be present.
 */
public final class MobProviders {

    private static final Map<MobType, MobProvider> PROVIDERS = new EnumMap<>(MobType.class);

    private MobProviders() {}

    /**
     * Detect the installed mob plugins. Called in onLoad.
     * Each provider class is only touched when its plugin's classes exist.
     * @return true if at least one mob plugin was found
     */
    public static boolean detect() {
        PROVIDERS.clear();

        if (classExists("io.lumine.mythic.bukkit.MythicBukkit")) {
            try {
                PROVIDERS.put(MobType.MYTHICMOBS, new fr.nocsy.mcpets.mythicmobs.MythicMobsProvider());
                MCPets.getLog().info("MythicMobs found. MythicMobs pets are available.");
            } catch (final LinkageError | RuntimeException e) {
                MCPets.getLog().warning("MythicMobs was found but could not be hooked: " + e);
            }
        }

        if (classExists("eu.northsoft.bettermob.api.BetterMobAPI")) {
            try {
                PROVIDERS.put(MobType.BETTERMOB, new fr.nocsy.mcpets.bettermob.BetterMobProvider());
                MCPets.getLog().info("BetterMob found. BetterMob pets are available.");
            } catch (final LinkageError | RuntimeException e) {
                MCPets.getLog().warning("BetterMob was found but could not be hooked: " + e);
            }
        }

        return !PROVIDERS.isEmpty();
    }

    private static boolean classExists(final String name) {
        try {
            Class.forName(name);
            return true;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean isAvailable(final MobType type) {
        return PROVIDERS.containsKey(type);
    }

    /** The provider for this type, or null if that plugin is not installed. */
    public static MobProvider get(final MobType type) {
        return type == null ? null : PROVIDERS.get(type);
    }

    public static Collection<MobProvider> all() {
        return Collections.unmodifiableCollection(PROVIDERS.values());
    }

    /**
     * Find the provider that can spawn this mob id.
     * The preferred type wins when it has the mob; otherwise any provider that has it.
     */
    public static MobProvider resolveMob(final String mobId, final MobType preferred) {
        if (mobId == null) return null;
        final MobProvider first = get(preferred);
        if (first != null && first.mobExists(mobId)) return first;
        for (final MobProvider provider : PROVIDERS.values()) {
            if (provider != first && provider.mobExists(mobId)) return provider;
        }
        return null;
    }

    /** The provider that manages this entity, or null. */
    public static MobProvider of(final Entity entity) {
        if (entity == null) return null;
        for (final MobProvider provider : PROVIDERS.values()) {
            if (provider.manages(entity)) return provider;
        }
        return null;
    }

    public static boolean mobExists(final String mobId) {
        return PROVIDERS.values().stream().anyMatch(p -> p.mobExists(mobId));
    }

    public static boolean skillExists(final String skillId) {
        return skillId != null && PROVIDERS.values().stream().anyMatch(p -> p.skillExists(skillId));
    }

    /**
     * Cast a skill on the pet entity. The plugin that manages the entity is asked first,
     * then the other one, so a BetterMob pet can still use a MythicMobs skill and the other way round.
     */
    public static boolean castSkill(final String skillId, final LivingEntity caster) {
        if (skillId == null || skillId.isEmpty() || caster == null) return false;
        final MobProvider owner = of(caster);
        if (owner != null && owner.skillExists(skillId) && owner.castSkill(skillId, caster)) return true;
        for (final MobProvider provider : PROVIDERS.values()) {
            if (provider != owner && provider.skillExists(skillId) && provider.castSkill(skillId, caster)) return true;
        }
        return false;
    }

    public static void registerComponents(final JavaPlugin plugin) {
        for (final MobProvider provider : PROVIDERS.values()) {
            try {
                provider.registerComponents(plugin);
            } catch (final LinkageError | RuntimeException e) {
                MCPets.getLog().warning("Could not register MCPets skills in " + provider.getType() + ": " + e);
            }
        }
    }

    public static void registerListeners(final JavaPlugin plugin) {
        for (final MobProvider provider : PROVIDERS.values()) {
            provider.registerListeners(plugin);
        }
    }
}
