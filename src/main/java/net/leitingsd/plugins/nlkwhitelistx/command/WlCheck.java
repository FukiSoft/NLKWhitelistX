package net.leitingsd.plugins.nlkwhitelistx.command;

import net.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import java.util.concurrent.CompletableFuture;

public class WlCheck implements SimpleCommand {

    private final NLKWhitelistX plugin;

    public WlCheck(NLKWhitelistX plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        // 动态获取 WhitelistManager
        WhitelistManager whitelistManager = plugin.getWhitelistManager();
        if (whitelistManager == null) {
            source.sendMessage(Component.text("插件未完全初始化，无法执行此命令。"));
            return;
        }

        if (args.length < 1) {
            source.sendMessage(Component.text(plugin.getMessage("wlcheck-usage")));
            return;
        }

        String playerName = args[0];

        CompletableFuture.runAsync(() -> {
            String uuid = whitelistManager.getUUIDFromAPI(playerName);
            String correctName = whitelistManager.getPlayerNameFromAPI(playerName);

            if (uuid != null && correctName != null) {
                source.sendMessage(Component.text(plugin.getMessage("wlcheck-success", correctName, uuid)));
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlcheck-error")));
            }
        });
    }
}
