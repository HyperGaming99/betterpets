package fr.nocsy.mcpets.mobs;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * A plugin that provides the mobs behind the pets (MythicMobs or BetterMob).
 * Everything MCPets needs from a mob plugin goes through this interface, so the
 * implementation of a missing plugin is never loaded.
 */
public interface MobProvider {

    MobType getType();

    /** Does a mob with this id exist in this plugin? */
    boolean mobExists(String mobId);

    /** Spawn the mob, or return null if it could not be spawned. */
    LivingEntity spawn(String mobId, Location location);

    /** Is this entity a mob of this plugin? */
    boolean manages(Entity entity);

    /** Does a skill with this id exist in this plugin? */
    boolean skillExists(String skillId);

    /**
     * Cast the skill with the entity as caster and as trigger.
     * @return true if the skill was found and started
     */
    boolean castSkill(String skillId, LivingEntity caster);

    /**
     * Send a signal to the mob (~onSignal:NAME).
     * @return true if the signal reached the mob
     */
    boolean signal(LivingEntity mob, String signal, LivingEntity sender);

    /** Display name configured in the mob plugin, or null. */
    String displayName(LivingEntity mob);

    /** Tell the mob plugin who owns this mob (if it has that concept). */
    void setOwner(LivingEntity mob, UUID owner);

    /** Walk to the location. */
    void moveTo(LivingEntity mob, Location destination);

    /** Stop the mob where it is. */
    void stop(LivingEntity mob, UUID owner);

    /** Remove the mob, including the mob plugin's own bookkeeping. */
    void remove(LivingEntity mob);

    /** Register the MCPets mechanics, conditions, targeters and placeholders. Called in onEnable. */
    void registerComponents(JavaPlugin plugin);

    /** Register listeners specific to this plugin. Called in onEnable. */
    void registerListeners(JavaPlugin plugin);
}
