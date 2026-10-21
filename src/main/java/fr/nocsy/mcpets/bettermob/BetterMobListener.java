package fr.nocsy.mcpets.bettermob;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.listeners.PetDespawnHandler;
import fr.nocsy.mcpets.mobs.MobType;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * BetterMob-only listener. BetterMob has no despawn event, so a BetterMob pet that leaves the
 * world without MCPets removing it (chunk unload, /bettermob killall, another plugin) is detected here.
 * Deaths are handled by PetListener for both mob plugins.
 */
public class BetterMobListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent e) {
        if (!MCPets.getInstance().isEnabled()) return;

        final Pet pet = Pet.getFromEntity(e.getEntity());
        if (pet == null || pet.isRemoved()) return;
        if (pet.getActiveMobType() != MobType.BETTERMOB) return;
        // Only the pet's current entity, not an old one being cleaned up
        if (pet.getActiveMob() == null || !pet.getActiveMob().getUniqueId().equals(e.getEntity().getUniqueId())) return;

        // The world is still removing the entity, respawn on the next tick
        Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> PetDespawnHandler.unexpectedDespawn(pet));
    }
}
