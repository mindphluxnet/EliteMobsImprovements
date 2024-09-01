package net.mindphlux;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatFormatter implements Listener {

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rank = EliteMobsImprovements.getPlayerRank(player);
        int level = player.getLevel();

        String formattedMessage = String.format("[%s] [Tag] (%d) %s: %s",
                rank, level, player.getDisplayName(), event.getMessage());

        event.setFormat(formattedMessage);
    }


}
