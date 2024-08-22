package com.leitingsd.plugins.nlkwhitelistx.command;

import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

public class WlCommand implements SimpleCommand {
    private final NLKWhitelistX plugin;

    public WlCommand(NLKWhitelistX plugin) {
        this.plugin = plugin;
    }


    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        // 从 config.yml 中读取描述信息
        String description = plugin.getMessage("wl-command");

        // 发送描述信息
        source.sendMessage(Component.text(description));

    }
}
