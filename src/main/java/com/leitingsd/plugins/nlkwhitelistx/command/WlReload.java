package com.leitingsd.plugins.nlkwhitelistx.command;

import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
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
            plugin.loadConfig();  // 调用主类中的 loadConfig 方法重新加载配置文件
            source.sendMessage(Component.text(plugin.getMessage("wlreload-success")));
        } catch (Exception e) {
            source.sendMessage(Component.text(plugin.getMessage("wlreload-failure")));
            plugin.getLogger().error("Failed to reload config.yml", e);
        }
    }
}
