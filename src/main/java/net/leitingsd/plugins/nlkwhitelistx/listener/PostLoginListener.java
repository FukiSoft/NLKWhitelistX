package net.leitingsd.plugins.nlkwhitelistx.listener;

import net.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistCheckResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.UUID;

public class PostLoginListener {

    private final NLKWhitelistX plugin;

    public PostLoginListener(NLKWhitelistX plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getUsername();

        // 检查缓存中是否有该玩家的登录结果
        WhitelistCheckResult result = plugin.getLoginResultCache().remove(playerUuid);

        if (result == WhitelistCheckResult.ALLOWED_OFFLINE) {
            String warningMsg = plugin.getMessage("admin-api-warning", playerName);
            Component warningComponent = Component.text(warningMsg);
            plugin.getLogger().warn(warningMsg);
            List<String> adminPlayers = plugin.getAdminPlayers();
            if (adminPlayers != null && !adminPlayers.isEmpty()) {
                for (Player onlinePlayer : plugin.getServer().getAllPlayers()) {
                    if (adminPlayers.contains(onlinePlayer.getUsername())) {
                        onlinePlayer.sendMessage(warningComponent);
                    }
                }
            }
        }
    }
}
