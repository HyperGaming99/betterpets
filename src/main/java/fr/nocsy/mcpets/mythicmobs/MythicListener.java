package fr.nocsy.mcpets.mythicmobs;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.listeners.PetDespawnHandler;

import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent;
import io.lumine.mythic.bukkit.events.MythicReloadedEvent;
import io.lumine.mythic.core.skills.CustomComponentRegistry;

/**
 * MythicMobs-only listener. Registered by MythicMobsProvider when MythicMobs is installed.
 */
public class MythicListener implements Listener {

    public static final String PLACEHOLDER_PACKAGE = "fr.nocsy.mcpets.mythicmobs.placeholders";
    public static final String CONDITION_PACKAGE = "fr.nocsy.mcpets.mythicmobs.conditions";
    public static final String TARGETER_PACKAGE = "fr.nocsy.mcpets.mythicmobs.targeters";
    public static final String MECHANIC_PACKAGE = "fr.nocsy.mcpets.mythicmobs.mechanics";

    @EventHandler
    public void onMythicReload(MythicReloadedEvent e) {
        final CustomComponentRegistry registry = MythicMobsProvider.getComponentRegistry();
        if (registry == null) return;
        Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> registry
            .registerCustomComponent(CustomComponentRegistry.MythicComponentType.PLACEHOLDER, PLACEHOLDER_PACKAGE)
            .registerCustomComponent(CustomComponentRegistry.MythicComponentType.CONDITION, CONDITION_PACKAGE)
            .registerCustomComponent(CustomComponentRegistry.MythicComponentType.TARGETER, TARGETER_PACKAGE)
            .registerCustomComponent(CustomComponentRegistry.MythicComponentType.MECHANIC, MECHANIC_PACKAGE)
        );
    }

    /**
     * MythicMobs despawned the pet on its own (despawn rules, chunk unload...)
     */
    @EventHandler
    public void onMythicDespawn(MythicMobDespawnEvent e) {
        if (e.getEntity() == null) return;
        PetDespawnHandler.unexpectedDespawn(Pet.getFromEntity(e.getEntity()));
    }
}
