package com.leitingsd.plugins.nlkwhitelistx.command;

import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import com.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WlAdd implements SimpleCommand {
    private final NLKWhitelistX plugin;
    private final WhitelistManager whitelistManager;

    public WlAdd(NLKWhitelistX plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 3) { // 修改参数检查条件，改为至少3个参数
            source.sendMessage(Component.text(plugin.getMessage("wladd-bad-arguments")));
            return;
        }

        String inputPlayer = args[0];
        String guarantor = args[1];
        String lotnumber = args[2];
        String description = args.length > 3 ? args[3] : "无"; // 如果没有提供备注，则使用 "无"

        String operator;

        if (source instanceof Player) {
            operator = ((Player) source).getUsername();
        } else {
            operator = "Console";
        }

        CompletableFuture<String> uuidFuture = CompletableFuture.supplyAsync(() -> whitelistManager.getUUIDFromAPI(inputPlayer));
        CompletableFuture<String> playerNameFuture = CompletableFuture.supplyAsync(() -> whitelistManager.getPlayerNameFromAPI(inputPlayer));

        try {
            String uuid = uuidFuture.get(5, TimeUnit.SECONDS);
            String playerName = playerNameFuture.get(5, TimeUnit.SECONDS);

            if (uuid == null || playerName == null) {
                source.sendMessage(Component.text(plugin.getMessage("wladd-uuid-not-found", inputPlayer)));
                return;
            }

            whitelistManager.addPlayerToWhitelist(playerName, operator, guarantor, lotnumber, description).thenRun(() ->
                    source.sendMessage(Component.text(plugin.getMessage("wladd-success", playerName, operator)))
            ).exceptionally(ex -> {
                ex.printStackTrace();
                source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
                return null;
            });

        } catch (TimeoutException e) {
            source.sendMessage(Component.text(plugin.getMessage("wladd-uuid-timeout", inputPlayer)));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", e.getMessage())));
        }
    }
}
