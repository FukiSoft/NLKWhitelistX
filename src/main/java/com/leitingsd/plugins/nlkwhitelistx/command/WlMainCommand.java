package com.leitingsd.plugins.nlkwhitelistx.command;

import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;

public class WlMainCommand implements SimpleCommand {

    private final NLKWhitelistX plugin;
    private final Map<String, SimpleCommand> subCommands = new HashMap<>();

    public WlMainCommand(NLKWhitelistX plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        subCommands.put("add", new WlAdd(plugin, plugin.getWhitelistManager()));
        subCommands.put("query", new WlQuery(plugin, plugin.getWhitelistManager()));
        subCommands.put("reload", new WlReload(plugin));
        subCommands.put("remove", new WlRemove(plugin, plugin.getWhitelistManager()));
        subCommands.put("help", new WlHelp(plugin));
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(Component.text(plugin.getMessage("wl-command"))); // 输出默认的/wl信息
            return;
        }

        SimpleCommand command = subCommands.get(args[0].toLowerCase());
        if (command != null) {
            command.execute(new Invocation() {
                @Override
                public CommandSource source() {
                    return source;
                }

                @Override
                public String alias() {
                    return invocation.alias();
                }

                @Override
                public String[] arguments() {
                    return getSubCommandArguments(args);
                }
            });
        } else {
            source.sendMessage(Component.text(plugin.getMessage("unknown-command", args[0])));
        }
    }

    private String[] getSubCommandArguments(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return subArgs;
    }
}
