package com.leitingsd.plugins.nlkwhitelistx.command;

import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import com.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.leitingsd.plugins.nlkwhitelistx.manager.WhitelistRecord;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

public class WlQuery implements SimpleCommand {
    private final NLKWhitelistX plugin;
    private final WhitelistManager whitelistManager;

    public WlQuery(NLKWhitelistX plugin, WhitelistManager whitelistManager) {
        this.plugin = plugin;
        this.whitelistManager = whitelistManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(Component.text(plugin.getMessage("wlquery-bad-arguments")));
            return;
        }

        String queryType = args[0];

        switch (queryType.toLowerCase()) {
            case "lot":
                if (args.length < 2) {
                    source.sendMessage(Component.text(plugin.getMessage("wlquery-bad-arguments")));
                } else {
                    queryByLotnumber(source, args[1]);
                }
                break;

            case "list":
                if (args.length > 1 && args[1].equalsIgnoreCase("-full")) {
                    queryWhitelistedPlayersFull(source);  // 执行详细输出
                } else {
                    queryWhitelistedPlayersSimple(source);  // 执行简洁输出
                }
                break;

            case "all":
                if (args.length > 1 && args[1].equalsIgnoreCase("-full")) {
                    queryAllRecordsFull(source);  // 执行详细输出
                } else {
                    queryAllRecordsSimple(source);  // 执行简洁输出
                }
                break;

            default:
                querySinglePlayer(source, queryType);
                break;
        }
    }

    private void querySinglePlayer(CommandSource source, String player) {
        whitelistManager.queryWhitelist(player).thenAccept(record -> {
            if (record != null) {
                String deleteAtMessage = (record.getDeleteAt() == 0) ? "无" : formatTimestamp(record.getDeleteAt());
                String deleteOperatorMessage = (record.getDeleteOperator() != null) ? record.getDeleteOperator() : "无";
                String deleteReasonMessage = (record.getDeleteReason() != null) ? record.getDeleteReason() : "未被删除白名单";
                String oldIdMessage = (record.getOldid() != null) ? record.getOldid() : "无";
                String guarantorMessage = (record.getGuarantor().equalsIgnoreCase("unknown") || record.getGuarantor().equals("-")) ? "无" : record.getGuarantor();

                source.sendMessage(Component.text(plugin.getMessage("wlquery-entry",
                        record.getId(),
                        record.getUuid(),
                        record.getPlayer(),  // 显示当前玩家ID
                        formatTimestamp(record.getTime().getTime()), // 添加时间
                        guarantorMessage,    // 担保人
                        record.getOperator(), // 操作员
                        record.getLotnumber(), // 审核批次号
                        record.getDescription() != null ? record.getDescription() : "无", // 描述
                        oldIdMessage,         // 曾用ID
                        deleteAtMessage,      // 删除于
                        deleteOperatorMessage, // 删除操作员
                        deleteReasonMessage    // 删除原因
                )));
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-no-data")));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
            return null;
        });
    }

    private void queryByLotnumber(CommandSource source, String lotnumber) {
        whitelistManager.queryByLotnumber(lotnumber).thenAccept(records -> {
            if (records != null && !records.isEmpty()) {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-lot-header", lotnumber)));
                for (WhitelistRecord record : records) {
                    String oldId = (record.getOldid() != null) ? record.getOldid() : "无";
                    String formattedOutput = record.getDeleteAt() == 0
                            ? plugin.getMessage("wlquery-lot", lotnumber, record.getPlayer(), formatTimestamp(record.getTime().getTime()), oldId)
                            : "§o§m" + plugin.getMessage("wlquery-lot", lotnumber, record.getPlayer(), formatTimestamp(record.getTime().getTime()), oldId) + "§r";
                    source.sendMessage(Component.text(formattedOutput));
                }
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-no-data")));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
            return null;
        });
    }

    private void queryWhitelistedPlayersFull(CommandSource source) {
        whitelistManager.queryWhitelistedPlayers().thenAccept(records -> {
            if (records != null && !records.isEmpty()) {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-list-full-header", records.size())));
                for (WhitelistRecord record : records) {
                    String oldId = (record.getOldid() != null) ? record.getOldid() : "无";
                    source.sendMessage(Component.text(plugin.getMessage("wlquery-list-full", records.size(), record.getPlayer(), formatTimestamp(record.getTime().getTime()), oldId)));
                }
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-no-data")));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
            return null;
        });
    }

    private void queryWhitelistedPlayersSimple(CommandSource source) {
        whitelistManager.queryWhitelistedPlayers().thenAccept(records -> {
            if (records != null && !records.isEmpty()) {
                String players = records.stream()
                        .map(WhitelistRecord::getPlayer)
                        .collect(Collectors.joining(", "));
                source.sendMessage(Component.text(plugin.getMessage("wlquery-list", records.size(), players)));
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-no-data")));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
            return null;
        });
    }

    private void queryAllRecordsFull(CommandSource source) {
        whitelistManager.queryAllRecords().thenAccept(records -> {
            if (records != null && !records.isEmpty()) {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-all-full-header", records.size())));
                for (WhitelistRecord record : records) {
                    String oldId = (record.getOldid() != null) ? record.getOldid() : "无";
                    String formattedOutput = record.getDeleteAt() == 0
                            ? plugin.getMessage("wlquery-all-full", record.getPlayer(), formatTimestamp(record.getTime().getTime()), oldId)
                            : "§o§m" + plugin.getMessage("wlquery-all-full", record.getPlayer(), formatTimestamp(record.getTime().getTime()), oldId) + "§r";
                    source.sendMessage(Component.text(formattedOutput));
                }
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-no-data")));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
            return null;
        });
    }

    private void queryAllRecordsSimple(CommandSource source) {
        whitelistManager.queryAllRecords().thenAccept(records -> {
            if (records != null && !records.isEmpty()) {
                String players = records.stream()
                        .map(record -> {
                            if (record.getDeleteAt() != 0) {
                                return "§o§m" + record.getPlayer() + "§r";
                            } else {
                                return record.getPlayer();
                            }
                        })
                        .collect(Collectors.joining(", "));
                source.sendMessage(Component.text(plugin.getMessage("wlquery-all", records.size(), players)));
            } else {
                source.sendMessage(Component.text(plugin.getMessage("wlquery-no-data")));
            }
        }).exceptionally(ex -> {
            ex.printStackTrace();
            source.sendMessage(Component.text(plugin.getMessage("internal-error", ex.getMessage())));
            return null;
        });
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日");
        return sdf.format(new Date(timestamp));
    }
}
