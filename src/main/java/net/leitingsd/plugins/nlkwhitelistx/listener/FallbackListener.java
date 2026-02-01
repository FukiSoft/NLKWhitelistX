package net.leitingsd.plugins.nlkwhitelistx.listener;

import net.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import net.kyori.adventure.text.Component;

public class FallbackListener {

    private final NLKWhitelistX plugin;

    public FallbackListener(NLKWhitelistX plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPlayerLogin(LoginEvent event) {
        String playerName = event.getPlayer().getUsername();
        plugin.getLogger().warn("降级模式拦截: 阻止玩家 {} 加入", playerName);
        
        // 降级模式：拒绝所有玩家加入
        String message = plugin.getMessage("fallback-kick-message");
        if (message.startsWith("未知消息键")) {
            message = "插件处于降级模式，暂时无法进入服务器。";
        }
        
        event.setResult(LoginEvent.ComponentResult.denied(Component.text(message)));
    }
}
