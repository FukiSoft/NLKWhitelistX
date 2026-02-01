package net.leitingsd.plugins.nlkwhitelistx;

import com.google.inject.Inject;
import net.leitingsd.plugins.nlkwhitelistx.command.WlMainCommand;
import net.leitingsd.plugins.nlkwhitelistx.database.DatabaseManager;
import net.leitingsd.plugins.nlkwhitelistx.listener.FallbackListener;
import net.leitingsd.plugins.nlkwhitelistx.listener.PostLoginListener;
import net.leitingsd.plugins.nlkwhitelistx.listener.WhitelistListener;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistCheckResult;
import net.leitingsd.plugins.nlkwhitelistx.manager.WhitelistManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(
        id = "nlkwhitelistx",
        name = "NLKWhitelistX",
        version = "1.3.0",
        description = "A whitelist plugin for Velocity",
        authors = {"leitingsd"}
)
public class NLKWhitelistX {

    private static final String PLUGIN_VERSION = "1.3.0";
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private DatabaseManager databaseManager;
    private WhitelistManager whitelistManager;
    private Map<String, String> messages = new HashMap<>();
    private boolean useMojangAPI;
    private String thirdPartyAPI;
    
    // 管理员列表
    private List<String> adminPlayers = new ArrayList<>();
    
    // 临时缓存，用于在 LoginEvent 和 PostLoginEvent 之间传递检查结果
    private final Map<UUID, WhitelistCheckResult> loginResultCache = new ConcurrentHashMap<>();

    @Inject
    public NLKWhitelistX(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    private boolean initialized = false;

    public boolean isInitialized() {
        return initialized;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Initialize database manager
        databaseManager = new DatabaseManager(this);

        try {
            loadConfig();
            // loadConfig 现在会抛出异常如果数据库初始化失败，所以这里的 isConnected 检查可能多余，但保留无害
            if (!databaseManager.isConnected()) {
                throw new IllegalStateException("数据库未完成初始化");
            }
            whitelistManager = new WhitelistManager(databaseManager, useMojangAPI, thirdPartyAPI, logger);
            initialized = true;  // 初始化成功
            logger.info("NLKWhitelistX 完成初始化");
        } catch (Exception e) {
            logger.error("插件初始化失败，请检查控制台输出，仅允许部分命令注册",e);
            initialized = false;
        }

        registerCommands();
        registerListeners();
    }

    public void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configPath = dataDirectory.resolve("config.yml");
            
            // 检查并更新配置文件
            checkAndUpdateConfig(configPath);
            
            Yaml yaml = new Yaml();
            try (InputStream input = Files.newInputStream(configPath)) {
                Map<String, Object> config = yaml.load(input);
                messages = (Map<String, String>) config.get("messages");
                Object adminsObj = config.get("admins");
                if (adminsObj instanceof List) {
                    adminPlayers = (List<String>) adminsObj;
                } else {
                    adminPlayers = new ArrayList<>();
                }
                Map<String, Object> apiConfig = (Map<String, Object>) config.get("api");
                if (apiConfig != null) {
                    Object useMojangAPIObj = apiConfig.get("useMojangAPI");
                    if (useMojangAPIObj instanceof Boolean ) {
                        useMojangAPI = (Boolean) useMojangAPIObj;
                    } else if (useMojangAPIObj instanceof String) {
                        useMojangAPI = Boolean.parseBoolean((String) useMojangAPIObj);
                    } else {
                        useMojangAPI = true;  // 默认情况下使用MojangAPI
                    }

                    Object thirdPartyAPIObj = apiConfig.get("thirdPartyAPI");
                    thirdPartyAPI = (thirdPartyAPIObj != null) ? thirdPartyAPIObj.toString().trim() : "";

                    // 当 useMojangAPI == false 时读取第三方 API
                    if (!useMojangAPI) {
                        if (thirdPartyAPI.isEmpty()) {
                            logger.error(getMessage("api-thirdparty-missing"));
                        } else {
                            logger.info(MessageFormat.format(getMessage("api-using-thirdparty"), thirdPartyAPI));
                        }
                    } else {
                        logger.info(getMessage("api-using-mojang"));
                    }
                } else {
                    useMojangAPI = true;
                    thirdPartyAPI = "";
                    logger.warn(getMessage("api-missing"));
                }
                databaseManager.init((Map<String, Object>) config.get("database"));
            }
        } catch (IOException e) {
            logger.error("无法加载配置文件", e);
            throw new RuntimeException("配置文件加载失败", e);
        } catch (Exception e) {
            throw e; 
        }
    }
    
    private void checkAndUpdateConfig(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                Files.copy(in, configPath);
            }
            return;
        }

        // 读取当前配置文件的版本号
        String currentVersion = null;
        try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            String line;
            Pattern versionPattern = Pattern.compile("^#\\s*Version:\\s*([\\d\\.]+)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = versionPattern.matcher(line);
                if (matcher.find()) {
                    currentVersion = matcher.group(1);
                    break;
                }
            }
        }

        // 如果版本不匹配，备份旧配置并生成新配置
        if (currentVersion == null || !currentVersion.equals(PLUGIN_VERSION)) {
            logger.warn("检测到旧版本配置文件 (当前: {}, 最新: {})，正在更新...", currentVersion, PLUGIN_VERSION);
            String backupName = "config_backup_v" + (currentVersion != null ? currentVersion : "unknown") + ".yml";
            Path backupPath = dataDirectory.resolve(backupName);
            if (Files.exists(backupPath)) {
                String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
                backupName = "config_backup_v" + (currentVersion != null ? currentVersion : "unknown") + "_" + timestamp + ".yml";
                backupPath = dataDirectory.resolve(backupName);
            }
            
            Files.move(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("旧配置文件已备份至: {}", backupPath.getFileName());
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                Files.copy(in, configPath);
            }
            logger.info("已生成新的配置文件，请根据需要将旧配置迁移回来。");
        }
    }

    private void registerCommands() {
        server.getCommandManager().register(server.getCommandManager().metaBuilder("wl").build(), new WlMainCommand(this));
    }

    private void registerListeners() {
        EventManager eventManager = server.getEventManager();
        Optional<PluginContainer> container = server.getPluginManager().fromInstance(this);
        container.ifPresent(pluginContainer -> {
            if (initialized) {
                eventManager.register(pluginContainer, new WhitelistListener(this, whitelistManager, logger));
                eventManager.register(pluginContainer, new PostLoginListener(this));
            } else {
                logger.warn("插件未完成初始化，进入降级模式");
                // 注册降级模式监听器，拒绝所有玩家
                eventManager.register(pluginContainer, new FallbackListener(this));
            }
        });
    }
    
    // 重载监听器
    public void reloadListeners() {
        EventManager eventManager = server.getEventManager();
        Optional<PluginContainer> container = server.getPluginManager().fromInstance(this);
        
        container.ifPresent(pluginContainer -> {
            eventManager.unregisterListeners(pluginContainer);
            if (initialized) {
                if (whitelistManager == null) {
                     whitelistManager = new WhitelistManager(databaseManager, useMojangAPI, thirdPartyAPI, logger);
                }
                
                eventManager.register(pluginContainer, new WhitelistListener(this, whitelistManager, logger));
                eventManager.register(pluginContainer, new PostLoginListener(this));
                logger.info("已重新注册正常模式监听器");
            } else {
                eventManager.register(pluginContainer, new FallbackListener(this));
                logger.warn("已重新注册降级模式监听器");
            }
        });
    }
    
    // 允许外部设置 initialized 状态
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
    
    // 允许外部更新 whitelistManager
    public void setWhitelistManager(WhitelistManager whitelistManager) {
        this.whitelistManager = whitelistManager;
    }

    public ProxyServer getServer() { return server; }

    public Logger getLogger() { return logger; }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WhitelistManager getWhitelistManager() {
        return whitelistManager;
    }

    public List<String> getAdminPlayers() {
        return adminPlayers;
    }
    
    public Map<UUID, WhitelistCheckResult> getLoginResultCache() {
        return loginResultCache;
    }

    public String getMessage(String key, Object... args) {
        String message = messages.getOrDefault(key, "未知消息键: " + key);
        message = message.replace("{version}", PLUGIN_VERSION);
        String formattedMessage = MessageFormat.format(message, args);
        if (formattedMessage.contains("&")) {
            formattedMessage = formattedMessage.replace('&', '§');
        }
        return formattedMessage;
    }
    
    // 写入日志文件
    public void logToFile(String message) {
        try {
            Path logFile = dataDirectory.resolve("whitelist_access.log");
            if (!Files.exists(logFile)) {
                Files.createFile(logFile);
            }
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String logEntry = "[" + timestamp + "] " + message + System.lineSeparator();
            
            Files.write(logFile, logEntry.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.error("无法写入 whitelist_access.log", e);
        }
    }
    
    public boolean isUseMojangAPI() {
        return useMojangAPI;
    }

    public String getThirdPartyAPI() {
        return thirdPartyAPI;
    }
}
