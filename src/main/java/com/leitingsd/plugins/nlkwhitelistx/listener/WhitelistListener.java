package com.leitingsd.plugins.nlkwhitelistx.listener;

import com.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WhitelistListener {

    private final WhitelistManager whitelistManager;
    private final Logger logger;

    public WhitelistListener(WhitelistManager whitelistManager, Logger logger) {
        this.whitelistManager = whitelistManager;
        this.logger = logger;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        try {
            // 使用玩家的用户名来获取UUID并检查白名单状态
            boolean isWhitelisted = whitelistManager.isPlayerWhitelisted(player.getUsername()).get(5, TimeUnit.SECONDS); // 设置5秒超时

            if (!isWhitelisted) {
                logger.info("Player " + player.getUsername() + " was denied access (not whitelisted).");
                event.setResult(LoginEvent.ComponentResult.denied(Component.text("You are not whitelisted on this server.")));
            } else {
                logger.info("Player " + player.getUsername() + " was allowed access (whitelisted).");
                event.setResult(LoginEvent.ComponentResult.allowed());
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error checking whitelist for player " + player.getUsername(), e);
            player.disconnect(Component.text("发生了一个内部错误。"));
        }
    }
}
