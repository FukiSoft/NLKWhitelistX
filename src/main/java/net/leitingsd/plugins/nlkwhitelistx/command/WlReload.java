package net.leitingsd.plugins.nlkwhitelistx.command;

import net.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

public class WlReload implements SimpleCommand {
    private final NLKWhitelistX plugin;

    public WlReload(NLKWhitelistX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        try {
            plugin.loadConfig();
            plugin.setInitialized(true);
            
            // 重新实例化 WhitelistManager
            WhitelistManager newWhitelistManager = new WhitelistManager(
                plugin.getDatabaseManager(), 
                plugin.isUseMojangAPI(), 
                plugin.getThirdPartyAPI(), 
                plugin.getLogger()
            );
            plugin.setWhitelistManager(newWhitelistManager);
            plugin.reloadListeners();
            
            source.sendMessage(Component.text(plugin.getMessage("wlreload-success")));
            
        } catch (Exception e) {
            plugin.setInitialized(false);
            // 加载失败时返回降级模式监听器
            plugin.reloadListeners();
            source.sendMessage(Component.text(plugin.getMessage("wlreload-failure")));
            plugin.getLogger().error("Failed to reload config.yml", e);
        }
    }
}
