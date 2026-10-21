package fr.nocsy.mcpets.mythicmobs;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.mobs.MobProvider;
import fr.nocsy.mcpets.mobs.MobType;
import fr.nocsy.mcpets.utils.debug.Debugger;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.skills.CustomComponentRegistry;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import io.lumine.mythic.core.skills.SkillTriggers;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static fr.nocsy.mcpets.mythicmobs.MythicListener.CONDITION_PACKAGE;
import static fr.nocsy.mcpets.mythicmobs.MythicListener.MECHANIC_PACKAGE;
import static fr.nocsy.mcpets.mythicmobs.MythicListener.PLACEHOLDER_PACKAGE;
import static fr.nocsy.mcpets.mythicmobs.MythicListener.TARGETER_PACKAGE;

/**
 * MythicMobs implementation. Only loaded when MythicMobs is installed.
 */
public class MythicMobsProvider implements MobProvider {

    private static CustomComponentRegistry componentRegistry;
    private final Map<UUID, AbstractLocation> stopRegistry = new HashMap<>();

    public static MythicBukkit mythic() {
        return MythicBukkit.inst();
    }

    /** The MythicMobs ActiveMob behind this entity, if any. */
    public static Optional<ActiveMob> activeMob(final Entity entity) {
        if (entity == null) return Optional.empty();
        return mythic().getMobManager().getActiveMob(entity.getUniqueId());
    }

    public static CustomComponentRegistry getComponentRegistry() {
        return componentRegistry;
    }

    @Override
    public MobType getType() {
        return MobType.MYTHICMOBS;
    }

    @Override
    public boolean mobExists(final String mobId) {
        return mobId != null && mythic().getMobManager().getMythicMob(mobId).isPresent();
    }

    @Override
    public LivingEntity spawn(final String mobId, final Location location) {
        final Entity ent;
        try {
            ent = mythic().getAPIHelper().spawnMythicMob(mobId, location);
        } catch (final Exception ex) {
            Debugger.send("§cMythicMob " + mobId + " could not be spawned: " + ex.getMessage());
            return null;
        }
        if (ent == null) return null;

        // Sometimes the mob isn't registered, so we try to register it manually
        if (activeMob(ent).isEmpty()) {
            Debugger.send("§6Warn: §7MythicMobs didn't have the mob in the registry, let's try to register it manually.");
            final Optional<MythicMob> type = mythic().getMobManager().getMythicMob(mobId);
            if (type.isPresent())
                mythic().getMobManager().registerActiveMob(BukkitAdapter.adapt(ent), type.get(), 0);
        }

        if (activeMob(ent).isEmpty() || !(ent instanceof LivingEntity living)) {
            Debugger.send("§cMythicMob was spawned but MCPets couldn't link it to an active mob.");
            ent.remove();
            return null;
        }
        return living;
    }

    @Override
    public boolean manages(final Entity entity) {
        return activeMob(entity).isPresent();
    }

    @Override
    public boolean skillExists(final String skillId) {
        return skillId != null && mythic().getSkillManager().getSkill(skillId).isPresent();
    }

    @Override
    public boolean castSkill(final String skillId, final LivingEntity caster) {
        final Optional<Skill> skill = mythic().getSkillManager().getSkill(skillId);
        if (skill.isEmpty()) return false;
        try {
            final Optional<ActiveMob> mob = activeMob(caster);
            if (mob.isPresent()) {
                // Same call as MCPets always made for MythicMobs pets
                skill.get().execute(new SkillMetadataImpl(SkillTriggers.CUSTOM, mob.get(), mob.get().getEntity()));
                return true;
            }
            // The caster is not a MythicMob (e.g. a BetterMob pet using a MythicMobs skill)
            return mythic().getAPIHelper().castSkill(caster, skillId);
        } catch (final Exception ex) {
            return false;
        }
    }

    @Override
    public boolean signal(final LivingEntity mob, final String signal, final LivingEntity sender) {
        final Optional<ActiveMob> active = activeMob(mob);
        if (active.isEmpty()) return false;
        final AbstractEntity from = sender != null ? BukkitAdapter.adapt(sender) : active.get().getEntity();
        active.get().signalMob(from, signal);
        return true;
    }

    @Override
    public String displayName(final LivingEntity mob) {
        return activeMob(mob).map(ActiveMob::getDisplayName).orElse(null);
    }

    @Override
    public void setOwner(final LivingEntity mob, final UUID owner) {
        activeMob(mob).ifPresent(active -> active.setOwnerUUID(owner));
    }

    @Override
    public void moveTo(final LivingEntity mob, final Location destination) {
        final AbstractLocation target = new AbstractLocation(BukkitAdapter.adapt(destination.getWorld()),
                destination.getX(), destination.getY(), destination.getZ());
        mythic().getVolatileCodeHandler().getAIHandler().navigateToLocation(BukkitAdapter.adapt(mob), target, 1);
    }

    @Override
    public void stop(final LivingEntity mob, final UUID owner) {
        final AbstractEntity entity = BukkitAdapter.adapt(mob);
        final AbstractLocation loc = stopRegistry.get(owner);
        if (loc != null
                && loc.getBlockX() == entity.getLocation().getBlockX()
                && loc.getBlockY() == entity.getLocation().getBlockY()
                && loc.getBlockZ() == entity.getLocation().getBlockZ()) {
            return;
        }
        mythic().getVolatileCodeHandler().getAIHandler().navigateToLocation(entity, entity.getLocation(), 1);
        stopRegistry.put(owner, entity.getLocation());
    }

    @Override
    public void remove(final LivingEntity mob) {
        activeMob(mob).ifPresent(active -> {
            active.despawn();
            active.remove();
        });
        mob.remove();
    }

    @Override
    public void registerComponents(final JavaPlugin plugin) {
        componentRegistry = new CustomComponentRegistry(plugin, new ArrayList<>());
        componentRegistry.registerCustomComponent(CustomComponentRegistry.MythicComponentType.PLACEHOLDER, PLACEHOLDER_PACKAGE)
                .registerCustomComponent(CustomComponentRegistry.MythicComponentType.CONDITION, CONDITION_PACKAGE)
                .registerCustomComponent(CustomComponentRegistry.MythicComponentType.TARGETER, TARGETER_PACKAGE)
                .registerCustomComponent(CustomComponentRegistry.MythicComponentType.MECHANIC, MECHANIC_PACKAGE);
    }

    @Override
    public void registerListeners(final JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new MythicListener(), plugin);
    }
}
