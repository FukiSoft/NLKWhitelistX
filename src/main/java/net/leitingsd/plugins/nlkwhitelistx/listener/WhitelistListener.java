package net.leitingsd.plugins.nlkwhitelistx.listener;

import net.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistCheckResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WhitelistListener {

    private final NLKWhitelistX plugin;
    private final WhitelistManager whitelistManager;
    private final Logger logger;
    private final int timeoutSeconds;

    public WhitelistListener(NLKWhitelistX plugin, WhitelistManager whitelistManager, Logger logger) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
        this.logger = logger;
        // 超时判定，默认5秒
        this.timeoutSeconds = 5;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();

        try {
            WhitelistCheckResult result = whitelistManager
                    .checkWhitelist(playerName)
                    .get(timeoutSeconds, TimeUnit.SECONDS);

            switch (result) {
                case ALLOWED -> {
                    logger.info("玩家 {} 通过白名单验证。", playerName);
                    event.setResult(LoginEvent.ComponentResult.allowed());
                }
                case ALLOWED_OFFLINE -> {
                    logger.info("玩家 {} 通过离线白名单验证（API不可用）。", playerName);
                    event.setResult(LoginEvent.ComponentResult.allowed());
                    // 缓存
                    plugin.getLoginResultCache().put(player.getUniqueId(), WhitelistCheckResult.ALLOWED_OFFLINE);
                    // 记录日志
                    plugin.logToFile("ALLOWED_OFFLINE: 玩家 " + playerName + " (" + player.getUniqueId() + ") 通过本地缓存验证进入。");
                }
                case NOT_WHITELISTED -> {
                    logger.info("玩家 {} 不在白名单中，拒绝进入。", playerName);
                    event.setResult(LoginEvent.ComponentResult.denied(
                            Component.text(plugin.getMessage("not_whitelisted"))
                    ));
                    plugin.logToFile("NOT_WHITELISTED: 玩家 " + playerName + " (" + player.getUniqueId() + ") 尝试进入被拒绝。");
                }
                case API_ERROR -> {
                    logger.warn("无法验证玩家 {} 的白名单状态（API错误且本地无记录）。", playerName);
                    event.setResult(LoginEvent.ComponentResult.denied(
                            Component.text(plugin.getMessage("api-error"))
                    ));
                    plugin.logToFile("API_ERROR: 玩家 " + playerName + " (" + player.getUniqueId() + ") 因API校验失败且本地校验失败被拒绝加入。");
                }
            }
        } catch (TimeoutException e) {
            logger.warn("白名单验证超时：{}", playerName);
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.text(plugin.getMessage("check-timeout"))
            ));
            plugin.logToFile("TIMEOUT: 玩家 " + playerName + " 验证超时。");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("检查玩家 {} 白名单时发生错误。", playerName, e);
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.text(plugin.getMessage("internal-error"))
            ));
            plugin.logToFile("ERROR: 玩家 " + playerName + " 验证时发生内部错误: " + e.getMessage());
        }
    }
}
