package net.mindphlux;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EliteMobsImprovements extends JavaPlugin implements Listener {

    // Store the player's equipped items upon death
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, ItemStack> savedMainHandItem = new HashMap<>();
    // Store the player's death location
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    // Store the player's experience level and points
    private final Map<UUID, Integer> savedExpLevels = new HashMap<>();
    private final Map<UUID, Float> savedExpPoints = new HashMap<>();

    private boolean saveItems;
    private boolean saveExperience;

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        saveDefaultConfig();

        loadConfigOptions();

        // Register the event listener
        pluginManager.registerEvents(this, this);
        pluginManager.registerEvents(new ChatFormatter(), this);

        logConfigOptions();
    }

    private void logConfigOptions() {
        logOption("Items", saveItems, "won't drop items on death.", "will drop items on death.");
        logOption("Experience", saveExperience, "won't lose experience on death.", "will lose experience on death.");
    }

    private void logOption(String optionName, boolean isEnabled, String enabledMessage, String disabledMessage) {
        String statusMessage = isEnabled ? enabledMessage : disabledMessage;
        getLogger().info(String.format("%s: Players %s", optionName, statusMessage));
    }

    private void loadConfigOptions() {
        FileConfiguration config = getConfig();
        saveItems = config.getBoolean("save-items", true);
        saveExperience = config.getBoolean("save-experience", true);
    }

    // Event listener for player death
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        if(saveItems) {
            // Save the player's equipped items
            savedArmor.put(playerId, player.getInventory().getArmorContents());
            savedMainHandItem.put(playerId, player.getInventory().getItemInMainHand());
        }

        if(saveExperience) {
            // Save the player's experience level and points
            savedExpLevels.put(playerId, player.getLevel());
            savedExpPoints.put(playerId, player.getExp());
        }

        // Store the player's death location
        deathLocations.put(playerId, player.getLocation());

        // Remove these items from the drops
        event.getDrops().removeIf(item ->
                item != null && (isArmor(item, player) || item.equals(player.getInventory().getItemInMainHand())));
    }

    // Event listener for player respawn
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        World world = player.getWorld();

        if(saveItems) {
            // Re-equip the saved armor and main hand item
            if (savedArmor.containsKey(playerId)) {
                player.getInventory().setArmorContents(savedArmor.get(playerId));
                savedArmor.remove(playerId);
            }
            if (savedMainHandItem.containsKey(playerId)) {
                player.getInventory().setItemInMainHand(savedMainHandItem.get(playerId));
                savedMainHandItem.remove(playerId);
            }
        }

        if(saveExperience) {
            // Restore the player's experience
            if (savedExpLevels.containsKey(playerId) && savedExpPoints.containsKey(playerId)) {
                player.setLevel(savedExpLevels.get(playerId));
                player.setExp(savedExpPoints.get(playerId));
                savedExpLevels.remove(playerId);
                savedExpPoints.remove(playerId);
            }
        }

        // Set the respawn location based on the closest configured location to the death point
        Location deathLocation = deathLocations.get(playerId);
        Location respawnLocation = getClosestRespawnLocation(world, deathLocation);
        // Fallback to the world's default spawn location if no specific config is found
        event.setRespawnLocation(Objects.requireNonNullElseGet(respawnLocation, world::getSpawnLocation));

        // Remove the death location from the map
        deathLocations.remove(playerId);

    }

    // Helper method to get the closest respawn location for a world based on the death location
    private Location getClosestRespawnLocation(World world, Location deathLocation) {
        FileConfiguration config = getConfig();
        String worldName = world.getName().toLowerCase();
        String path = "graveyard-locations." + worldName;

        if (config.contains(path)) {
            List<Map<?, ?>> spawnPoints = config.getMapList(path);
            Location closestLocation = null;
            double closestDistance = Double.MAX_VALUE;

            for (Map<?, ?> point : spawnPoints) {
                double x = ((Number) point.get("x")).doubleValue();
                double y = ((Number) point.get("y")).doubleValue();
                double z = ((Number) point.get("z")).doubleValue();
                Location spawnLocation = new Location(world, x, y, z);

                double distance = spawnLocation.distance(deathLocation);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestLocation = spawnLocation;
                }
            }

            return closestLocation;
        }

        return null;
    }

    // Helper method to check if an item is part of the player's armor
    private boolean isArmor(ItemStack item, Player player) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (item.equals(armor)) {
                return true;
            }
        }
        return false;
    }

    public static String getPlayerRank(Player player) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if(user == null) return "Default";

        String groupName = user.getPrimaryGroup();
        Group group = luckPerms.getGroupManager().getGroup(groupName);

        if(group != null) {
            return group.getDisplayName();
        }
        else {
            return "Default";
        }
    }
}
