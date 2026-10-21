package fr.nocsy.mcpets.listeners;

import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetDespawnReason;
import fr.nocsy.mcpets.data.config.Language;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared handling when a pet mob disappears without MCPets asking for it,
 * used by the MythicMobs and the BetterMob listeners.
 */
public final class PetDespawnHandler {

    private static final Map<UUID, Integer> repeatRespawn = new HashMap<>();

    private PetDespawnHandler() {}

    /**
     * The mob plugin removed the pet (chunk unload, despawn rules, kill command...).
     * Despawn it properly and spawn it again next to the owner, at most 3 times in a row.
     */
    public static void unexpectedDespawn(final Pet pet) {
        if (pet == null || pet.isRemoved()) return;

        pet.despawn(PetDespawnReason.MYTHICMOBS);

        final UUID ownerUUID = pet.getOwner();
        if (ownerUUID == null) return;

        final Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner == null) return;
        if (repeatRespawn.containsKey(ownerUUID) && repeatRespawn.get(ownerUUID) == 3) {
            Language.REVOKED_UNKNOWN.sendMessage(owner);
            repeatRespawn.remove(ownerUUID);
            return;
        }
        int value = 1;
        if (repeatRespawn.containsKey(ownerUUID)) value = repeatRespawn.get(ownerUUID);
        pet.spawn(owner, owner.getLocation());
        pet.setRecurrent_spawn(false);
        repeatRespawn.put(ownerUUID, value + 1);
        new BukkitRunnable() {
            @Override
            public void run() {
                repeatRespawn.remove(ownerUUID);
            }
        }.runTaskLater(MCPets.getInstance(), 10L);
    }

    /**
     * The pet mob died.
     */
    public static void death(final Pet pet) {
        if (pet == null || pet.isRemoved()) return;

        pet.despawn(PetDespawnReason.DEATH);

        if (pet.getOwner() == null) return;
        final Player owner = Bukkit.getPlayer(pet.getOwner());
        if (owner != null) {
            Language.REVOKED.sendMessage(owner);
        }
    }
}
