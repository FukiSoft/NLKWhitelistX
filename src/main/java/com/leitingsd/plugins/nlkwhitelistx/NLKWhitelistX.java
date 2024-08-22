package com.leitingsd.plugins.nlkwhitelistx;

import com.google.inject.Inject;
import com.leitingsd.plugins.nlkwhitelistx.command.WlMainCommand;
import com.leitingsd.plugins.nlkwhitelistx.command.WlHelp;
import com.leitingsd.plugins.nlkwhitelistx.command.WlAdd;
import com.leitingsd.plugins.nlkwhitelistx.command.WlQuery;
import com.leitingsd.plugins.nlkwhitelistx.command.WlRemove;
import com.leitingsd.plugins.nlkwhitelistx.database.DatabaseManager;
import com.leitingsd.plugins.nlkwhitelistx.listener.WhitelistListener;
import com.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.leitingsd.plugins.nlkwhitelistx.command.WlCommand;
import com.leitingsd.plugins.nlkwhitelistx.command.WlReload;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;



@Plugin(
        id = "nlkwhitelistx",
        name = "NLKWhitelistX",
        version = "1.2.0",
        description = "A whitelist plugin for Velocity",
        authors = {"leitingsd"}
)
public class NLKWhitelistX {

    private static final String PLUGIN_VERSION = "1.2.0";
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private DatabaseManager databaseManager;
    private WhitelistManager whitelistManager;
    private Map<String, String> messages = new HashMap<>();
    private boolean useMojangAPI;
    private String thirdPartyAPI;

    @Inject
    public NLKWhitelistX(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize database manager
        databaseManager = new DatabaseManager(this);

        // Load configuration
        loadConfig();

        // Initialize whitelist manager
        whitelistManager = new WhitelistManager(databaseManager, useMojangAPI, thirdPartyAPI, logger);

        registerCommands();
        registerListeners();
    }

    public void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    Files.copy(in, configPath);
                }
            }
            Yaml yaml = new Yaml();
            try (InputStream input = Files.newInputStream(configPath)) {
                Map<String, Object> config = yaml.load(input);
                databaseManager.init((Map<String, Object>) config.get("database"));
                messages = (Map<String, String>) config.get("messages");
                useMojangAPI = (boolean) config.getOrDefault("api.useMojangAPI", true);
                thirdPartyAPI = (String) config.getOrDefault("api.thirdPartyAPI", "");

                // 打印调试信息
                logger.info("useMojangAPI: " + useMojangAPI);
                logger.info("thirdPartyAPI: " + thirdPartyAPI);
            }
        } catch (IOException e) {
            logger.error("无法加载配置文件", e);
        }
    }


    private void registerCommands() {
        server.getCommandManager().register(server.getCommandManager().metaBuilder("wl").build(), new WlMainCommand(this));
    }

    private void registerListeners() {
        EventManager eventManager = server.getEventManager();
        Optional<PluginContainer> container = server.getPluginManager().fromInstance(this);
        container.ifPresent(pluginContainer -> {
            eventManager.register(pluginContainer, new WhitelistListener(whitelistManager, logger));
        });
    }


    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }

    public String getMessage(String key, Object... args) {
        String message = messages.getOrDefault(key, "未知消息键: " + key);
        message = message.replace("{version}", PLUGIN_VERSION);
//        message = message.replace('&', '§');
//        return MessageFormat.format(message, args);
        // 判断输出是游戏内还是控制台
        // 使用 '&' 作为颜色代码的标识符，并在输出到玩家时替换为 '§'
        String formattedMessage = MessageFormat.format(message, args);

        // 判断输出目标：如果需要输出到玩家，替换 '&' 为 '§'
        if (formattedMessage.contains("&")) {
            formattedMessage = formattedMessage.replace('&', '§');
        }

        return formattedMessage;

    }



    public boolean isUseMojangAPI() {
        return useMojangAPI;
    }

    public String getThirdPartyAPI() {
        return thirdPartyAPI;
    }
}
