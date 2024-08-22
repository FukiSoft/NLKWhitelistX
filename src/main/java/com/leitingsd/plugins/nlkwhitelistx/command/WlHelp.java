package com.leitingsd.plugins.nlkwhitelistx.command;

import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

public class WlHelp implements SimpleCommand {

    private final NLKWhitelistX plugin;

    public WlHelp(NLKWhitelistX plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        source.sendMessage(Component.text(plugin.getMessage("wl-command")));
    }
}
