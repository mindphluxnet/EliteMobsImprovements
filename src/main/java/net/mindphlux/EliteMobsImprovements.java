package net.mindphlux;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
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
        saveDefaultConfig();

        loadConfigOptions();

        // Register the event listener
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("[EliteMobsImprovements] Plugin enabled!");
    }

    private void loadConfigOptions() {
        FileConfiguration config = getConfig();
        saveItems = config.getBoolean("save-items", true);
        saveExperience = config.getBoolean("save-experience", true);
    }

    @Override
    public void onDisable() {
        getLogger().info("[EliteMobsImprovements] Plugin disabled!");
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
}
