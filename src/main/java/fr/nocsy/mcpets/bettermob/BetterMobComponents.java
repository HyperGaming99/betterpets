package fr.nocsy.mcpets.bettermob;

import eu.northsoft.bettermob.api.BetterMobAPI;
import eu.northsoft.bettermob.api.ConditionContext;
import eu.northsoft.bettermob.api.MechanicContext;
import fr.nocsy.mcpets.MCPets;
import fr.nocsy.mcpets.data.Pet;
import fr.nocsy.mcpets.data.PetDespawnReason;
import fr.nocsy.mcpets.data.config.ItemsListConfig;
import fr.nocsy.mcpets.data.inventories.PetInventory;
import fr.nocsy.mcpets.data.inventories.PetInventoryHolder;
import fr.nocsy.mcpets.data.livingpets.PetFoodBuff;
import fr.nocsy.mcpets.data.livingpets.PetFoodType;
import fr.nocsy.mcpets.data.livingpets.PetStats;
import fr.nocsy.mcpets.events.PetDamageEvent;
import fr.nocsy.mcpets.utils.PetMath;
import fr.nocsy.mcpets.utils.Utils;
import fr.nocsy.mcpets.utils.debug.Debugger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import static fr.nocsy.mcpets.bettermob.BetterMobProvider.param;
import static fr.nocsy.mcpets.bettermob.BetterMobProvider.paramBoolean;
import static fr.nocsy.mcpets.bettermob.BetterMobProvider.paramDouble;

/**
 * Registers the MCPets mechanics, conditions, targeters and placeholders in BetterMob.
 * Same names and parameters as the old MythicMobs components, so skill lines keep working:
 *
 * Mechanics:    givePet, setPet, setLivingPet, evolvePet, petBuff, petDamage, petDespawn,
 *               petExperience, petFollow, petName, dropPetItem, dropPetInventory
 * Conditions:   petTaming, petExperience
 * Targeters:    @PetOwner, @PetFromOwner
 * Placeholders: <pet.id> <pet.hp> <pet.max.hp> <pet.level.index> <pet.level.name>
 *               <pet.owner.name> <pet.owner.uuid> <pet.power> <pet.experience>
 *               <pet.damagemodifier> <pet.resistancemodifier> <pet.tamingprogress>
 */
public final class BetterMobComponents {

    private BetterMobComponents() {}

    public static void register(final Plugin plugin) {
        final BetterMobAPI api = BetterMobProvider.api();
        registerMechanics(api, plugin);
        registerConditions(api, plugin);
        registerTargeters(api, plugin);
        registerPlaceholders(api, plugin);
    }

    // ------------------------------------------------------------------ mechanics

    private static void registerMechanics(final BetterMobAPI api, final Plugin plugin) {

        mechanic(api, plugin, "givePet", ctx -> {
            if (!(ctx.target() instanceof Player player)) return;
            final Pet pet = Pet.getFromId(param(ctx.params(), "", "id"));
            if (pet == null) return;
            Utils.givePermission(player.getUniqueId(), pet.getPermission());
        });

        mechanic(api, plugin, "setPet", ctx -> {
            if (!(ctx.target() instanceof Player player)) return;
            final Pet pet = Pet.getFromId(param(ctx.params(), "", "id"));
            if (pet == null) return;
            final boolean followOwner = paramBoolean(ctx.params(), true, "followOwner", "fo");
            final boolean mustHavePermission = paramBoolean(ctx.params(), false, "mustHavePermission", "permCheck");
            if (mustHavePermission && !player.hasPermission(pet.getPermission())) return;
            final LivingEntity caster = ctx.caster();
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () ->
                    pet.changeActiveMobTo(caster, player.getUniqueId(), followOwner, PetDespawnReason.SETPET_REPLACED));
        });

        mechanic(api, plugin, "setLivingPet", ctx -> {
            final LivingEntity caster = ctx.caster();
            if (Pet.getFromEntity(caster) != null) return;
            final Pet pet = Pet.getFromId(param(ctx.params(), "", "id"));
            if (pet == null) return;
            final boolean followOnTame = paramBoolean(ctx.params(), true, "followOnTame", "foT");
            final double tamingProgress = paramDouble(ctx.params(), 0.0D, "tamingProgress", "tP");
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> {
                // MCPets may have spawned and linked this entity while this task was queued.
                // Do not replace its fully tamed instance with the wild pet template.
                if (!caster.isValid() || Pet.getFromEntity(caster) != null) return;
                pet.setActiveMob(caster);
                pet.setDefaultTamingValue(tamingProgress);
                pet.setFollowOwner(followOnTame);
            });
        });

        mechanic(api, plugin, "evolvePet", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.caster());
            final String evolutionId = param(ctx.params(), "", "evolutionId");
            final boolean force = paramBoolean(ctx.params(), false, "force");
            final Pet evolution = Pet.getFromId(evolutionId);
            Debugger.send("§6[Evolution Mechanic]:");
            if (pet != null)
                Debugger.send("§7- pet: §a" + pet.getId() + "§7 has pet stats ? §a" + (pet.getPetStats() != null));
            Debugger.send("§7- evolution: §a" + evolutionId + " exists ? §a" + (evolution != null));
            if (evolution == null || pet == null || pet.getPetStats() == null) return;
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () ->
                    pet.getPetStats().getCurrentLevel().evolveTo(pet.getOwner(), force, evolution));
        });

        mechanic(api, plugin, "petBuff", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.target());
            if (pet == null || pet.getPetStats() == null) return;
            final long duration = (long) paramDouble(ctx.params(), 0, "duration");
            final float power = (float) paramDouble(ctx.params(), 0, "power");
            final PetFoodType type = PetFoodType.get(param(ctx.params(), "BUFF_DAMAGE", "type"));
            final PetMath operator = PetMath.get(param(ctx.params(), "ADD", "operator"));
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () ->
                    new PetFoodBuff(pet, type, power, operator, duration).apply());
        });

        mechanic(api, plugin, "petDamage", ctx -> {
            final LivingEntity caster = ctx.caster();
            final Pet pet = Pet.getFromEntity(caster);
            if (pet == null || !(ctx.target() instanceof Damageable damageable)) return;
            double damage = paramDouble(ctx.params(), 0, "damage");
            if (paramBoolean(ctx.params(), true, "applyStats") && pet.getPetStats() != null)
                damage = pet.getPetStats().getModifiedAttackDamages(damage);
            final PetDamageEvent event = new PetDamageEvent(pet, damage);
            Utils.callEvent(event);
            if (event.isCancelled()) return;
            damageable.damage(event.getDamageAmount(), caster);
        });

        mechanic(api, plugin, "petDespawn", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.caster());
            if (pet != null) pet.despawn(PetDespawnReason.PETDESPAWN_SKILL);
        });

        mechanic(api, plugin, "petExperience", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.target());
            if (pet == null || pet.getPetStats() == null) return;
            final double exp = paramDouble(ctx.params(), 0.0D, "exp", "experience");
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> pet.getPetStats().addExperience(exp));
        });

        mechanic(api, plugin, "petFollow", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.target());
            if (pet != null) pet.setFollowOwner(paramBoolean(ctx.params(), true, "follow"));
        });

        mechanic(api, plugin, "petName", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.target());
            if (pet == null) return;
            pet.setDisplayName(param(ctx.params(), "No name", "name"), paramBoolean(ctx.params(), false, "save"));
        });

        mechanic(api, plugin, "dropPetItem", ctx -> {
            final Entity target = ctx.target();
            if (target == null) return;
            final ItemStack item = ItemsListConfig.getInstance().getItemStack(param(ctx.params(), "", "petItem"));
            final double chance = Math.clamp(paramDouble(ctx.params(), 1.0D, "chance"), 0.0D, 1.0D);
            if (item == null || ThreadLocalRandom.current().nextDouble() > chance) return;
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () ->
                    target.getWorld().dropItemNaturally(target.getLocation(), item));
        });

        mechanic(api, plugin, "dropPetInventory", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.caster());
            if (pet == null) return;
            final PetInventory petInventory = PetInventory.get(pet);
            if (petInventory == null) return;
            final Inventory inv = petInventory.getInventory();
            final Location loc = pet.getActiveMob().getLocation();
            Bukkit.getScheduler().runTask(MCPets.getInstance(), () -> {
                for (final ItemStack item : inv.getContents()) {
                    if (item == null) continue;
                    loc.getWorld().dropItemNaturally(loc, item);
                }
            });
            petInventory.setInventory(new PetInventoryHolder(inv.getSize(), PetInventoryHolder.Type.PET_INVENTORY_MENU).getInventory());
        });
    }

    // ------------------------------------------------------------------ conditions

    private static void registerConditions(final BetterMobAPI api, final Plugin plugin) {
        condition(api, plugin, "petTaming", ctx -> {
            final Pet pet = Pet.getFromEntity(subject(ctx));
            return pet != null
                    && pet.getPetStats() != null
                    && Range.parse(param(ctx.params(), null, "taming", "t", "value")).test(pet.getTamingProgress());
        });

        condition(api, plugin, "petExperience", ctx -> {
            final Pet pet = Pet.getFromEntity(subject(ctx));
            return pet != null
                    && pet.getPetStats() != null
                    && Range.parse(param(ctx.params(), null, "experience", "exp", "value")).test(pet.getPetStats().getExperience());
        });
    }

    /**
     * The entity the condition is about: the target when tested against one, otherwise the caster.
     */
    private static Entity subject(final ConditionContext ctx) {
        return ctx.target() != null ? ctx.target() : ctx.caster();
    }

    // ------------------------------------------------------------------ targeters

    private static void registerTargeters(final BetterMobAPI api, final Plugin plugin) {
        targeter(api, plugin, "petOwner", ctx -> {
            final Pet pet = Pet.getFromEntity(ctx.caster());
            if (pet == null || pet.getOwner() == null) return List.of();
            final Player owner = Bukkit.getPlayer(pet.getOwner());
            return owner == null ? List.of() : List.of(owner);
        });

        targeter(api, plugin, "petFromOwner", ctx -> {
            if (!(ctx.caster() instanceof Player player)) {
                Debugger.send("Targeter @PetFromOwner called with a non-player caster: " + ctx.caster().getType());
                return List.of();
            }
            final List<Entity> entities = new ArrayList<>();
            for (final Pet pet : Pet.getActivePetsForOwner(player.getUniqueId())) {
                if (pet.getActiveMob() != null) entities.add(pet.getActiveMob());
            }
            return entities;
        });
    }

    // ------------------------------------------------------------------ placeholders

    private static void registerPlaceholders(final BetterMobAPI api, final Plugin plugin) {
        final boolean ok = api.registerPlaceholder(plugin, "pet", (key, ctx) -> {
            final LivingEntity caster = ctx.caster();
            if (caster == null) return null;
            final Pet pet = Pet.getFromEntity(caster);
            final PetStats stats = pet == null ? null : pet.getPetStats();
            return switch (key.toLowerCase(Locale.ROOT)) {
                case "id" -> pet == null ? "null" : pet.getId();
                case "hp" -> String.valueOf(stats != null ? (int) stats.getCurrentHealth() : (int) caster.getHealth());
                case "max.hp" -> String.valueOf(stats != null ? (int) stats.getCurrentLevel().getMaxHealth() : (int) caster.getMaxHealth());
                case "level.index" -> String.valueOf(stats == null ? 0 : stats.getCurrentLevelIndex());
                case "level.name" -> stats == null ? "null" : stats.getCurrentLevel().getLevelName();
                case "owner.name" -> pet == null || pet.getOwner() == null ? "null" : String.valueOf(Bukkit.getOfflinePlayer(pet.getOwner()).getName());
                case "owner.uuid" -> pet == null || pet.getOwner() == null ? "null" : pet.getOwner().toString();
                case "power" -> String.valueOf(stats == null ? 1D : stats.getPower());
                case "experience" -> String.valueOf(stats == null ? 1D : stats.getExperience());
                case "damagemodifier" -> String.valueOf(stats == null ? 1D : stats.getDamageModifier());
                case "resistancemodifier" -> String.valueOf(stats == null ? 1D : stats.getResistanceModifier());
                case "tamingprogress" -> String.valueOf(pet == null ? 1D : pet.getTamingProgress());
                default -> null;
            };
        });
        if (!ok) MCPets.getLog().warning("BetterMob placeholder namespace 'pet' is already taken, <pet.*> placeholders are not available.");
    }

    // ------------------------------------------------------------------ helpers

    private static void mechanic(final BetterMobAPI api, final Plugin plugin, final String name,
                                 final java.util.function.Consumer<MechanicContext> body) {
        final boolean ok = api.registerMechanic(plugin, name, ctx -> {
            try {
                body.accept(ctx);
            } catch (final RuntimeException ex) {
                MCPets.getLog().log(Level.SEVERE, "MCPets mechanic " + name + " failed", ex);
            }
        });
        if (!ok) MCPets.getLog().warning("BetterMob mechanic '" + name + "' could not be registered (name taken).");
    }

    private static void condition(final BetterMobAPI api, final Plugin plugin, final String name,
                                  final eu.northsoft.bettermob.api.CustomCondition condition) {
        if (!api.registerCondition(plugin, name, condition))
            MCPets.getLog().warning("BetterMob condition '" + name + "' could not be registered (name taken).");
    }

    private static void targeter(final BetterMobAPI api, final Plugin plugin, final String name,
                                 final eu.northsoft.bettermob.api.CustomTargeter targeter) {
        if (!api.registerTargeter(plugin, name, targeter))
            MCPets.getLog().warning("BetterMob targeter '" + name + "' could not be registered (name taken).");
    }

    /**
     * Number range in the MythicMobs style: "5", "&gt;5", "&gt;=5", "&lt;5", "&lt;=5", "2to8", "2-8".
     */
    record Range(double min, boolean minInclusive, double max, boolean maxInclusive) {

        static Range parse(final String raw) {
            if (raw == null || raw.isBlank()) return new Range(Double.NEGATIVE_INFINITY, true, Double.POSITIVE_INFINITY, true);
            final String s = raw.trim().replace(" ", "").toLowerCase(Locale.ROOT);
            try {
                if (s.startsWith(">=")) return new Range(Double.parseDouble(s.substring(2)), true, Double.POSITIVE_INFINITY, true);
                if (s.startsWith("<=")) return new Range(Double.NEGATIVE_INFINITY, true, Double.parseDouble(s.substring(2)), true);
                if (s.startsWith(">")) return new Range(Double.parseDouble(s.substring(1)), false, Double.POSITIVE_INFINITY, true);
                if (s.startsWith("<")) return new Range(Double.NEGATIVE_INFINITY, true, Double.parseDouble(s.substring(1)), false);
                final int to = s.indexOf("to");
                if (to > 0) return new Range(Double.parseDouble(s.substring(0, to)), true, Double.parseDouble(s.substring(to + 2)), true);
                final int dash = s.indexOf('-', 1);
                if (dash > 0) return new Range(Double.parseDouble(s.substring(0, dash)), true, Double.parseDouble(s.substring(dash + 1)), true);
                final double exact = Double.parseDouble(s);
                return new Range(exact, true, exact, true);
            } catch (final NumberFormatException e) {
                MCPets.getLog().warning("Invalid range in pet condition: " + raw);
                return new Range(1, true, 0, true); // never matches
            }
        }

        boolean test(final double value) {
            final boolean aboveMin = minInclusive ? value >= min : value > min;
            final boolean belowMax = maxInclusive ? value <= max : value < max;
            return aboveMin && belowMax;
        }
    }
}
