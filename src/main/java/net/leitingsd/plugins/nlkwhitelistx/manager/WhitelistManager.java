package net.leitingsd.plugins.nlkwhitelistx.manager;

import net.leitingsd.plugins.nlkwhitelistx.database.DatabaseManager;
import org.slf4j.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WhitelistManager {
    private final DatabaseManager databaseManager;
    private final boolean useMojangAPI;
    private final String thirdPartyAPI;
    private final Logger logger;

    public WhitelistManager(DatabaseManager databaseManager, boolean useMojangAPI, String thirdPartyAPI, Logger logger) {
        this.databaseManager = databaseManager;
        this.useMojangAPI = useMojangAPI;
        this.thirdPartyAPI = thirdPartyAPI;
        this.logger = logger;
    }

    private static final ExecutorService WHITELIST_EXECUTOR =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String getUUIDFromAPI(String player) {
        String apiUrl;
        if (useMojangAPI) {
            apiUrl = "https://api.mojang.com/users/profiles/minecraft/" + player;
        } else if (thirdPartyAPI != null && !thirdPartyAPI.isEmpty()) {
            apiUrl = thirdPartyAPI.replace("{username}", player);
        } else {
            logger.warn("API URL 为空，无法获取 UUID");
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            // 异步请求
            HttpResponse<String> response = HTTP_CLIENT
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(4, java.util.concurrent.TimeUnit.SECONDS); // 超时4秒

            if (response.statusCode() == 200) {
                return extractUUID(response.body());
            } else {
                logger.warn("API 请求失败: {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("获取UUID时发生错误", e);
            return null;
        }
    }

    public CompletableFuture<WhitelistCheckResult> checkWhitelist(String player) {
        return CompletableFuture.supplyAsync(() -> {
            String uuid = getUUIDFromAPI(player);
            if (uuid == null) {
                // 无法连接API，尝试本地数据库校验
                return checkLocalWhitelist(player);
            }

            try (Connection connection = databaseManager.getConnection()) {
                String sql = "SELECT player FROM whitelist WHERE uuid = ? AND deleteAt = 0";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            String currentID = resultSet.getString("player");
                            if (!currentID.equals(player)) {
                                updatePlayerID(uuid, player, currentID);
                            }
                            return WhitelistCheckResult.ALLOWED;
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }

            return WhitelistCheckResult.NOT_WHITELISTED;
        }, WHITELIST_EXECUTOR);
    }

    // 当API不可用时执行本地数据库校验
    private WhitelistCheckResult checkLocalWhitelist(String player) {
        try (Connection connection = databaseManager.getConnection()) {
            // 尝试通过玩家名直接查询
            String sql = "SELECT uuid, player FROM whitelist WHERE player = ? AND deleteAt = 0";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        // 本地存在该玩家记录且未被删除
                        logger.info("API不可用，但本地数据库验证通过: {}", player);
                        return WhitelistCheckResult.ALLOWED_OFFLINE; // 返回离线验证通过状态
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("本地白名单校验时发生SQL异常", e);
        }
        // 本地无匹配记录或发生错误，返回 API_ERROR 触发原有逻辑
        return WhitelistCheckResult.API_ERROR;
    }

    private String extractUUID(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            return rootNode.get("id").asText();
        } catch (Exception e) {
            logger.error("在提取UUID时发生错误", e);
            return null;
        }
    }

    public String getPlayerNameFromAPI(String player) {
        String apiUrl;
        if (useMojangAPI) {
            apiUrl = "https://api.mojang.com/users/profiles/minecraft/" + player;
        } else if (thirdPartyAPI != null && !thirdPartyAPI.isEmpty()) {
            apiUrl = thirdPartyAPI.replace("{username}", player);
        } else {
            logger.warn("API URL 为空，无法获取玩家名称");
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(4, java.util.concurrent.TimeUnit.SECONDS);

            if (response.statusCode() == 200) {
                return extractPlayerName(response.body());
            } else {
                logger.warn("API 请求失败: {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("获取玩家名称时发生错误", e);
            return null;
        }
    }

    private String extractPlayerName(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            return rootNode.get("name").asText();
        } catch (Exception e) {
            logger.error("在提取玩家名称时发生错误", e);
            return null;
        }
    }

    public CompletableFuture<Boolean> isPlayerWhitelisted(String player) {
        return CompletableFuture.supplyAsync(() -> {
            String uuid = getUUIDFromAPI(player);
            if (uuid == null) {
                return false;
            }
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "SELECT player FROM whitelist WHERE uuid = ? AND deleteAt = 0";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            String currentID = resultSet.getString("player");
                            if (!currentID.equals(player)) {
                                updatePlayerID(uuid, player, currentID);
                            }
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
            return false;
        }, WHITELIST_EXECUTOR);
    }

    private void updatePlayerID(String uuid, String newID, String oldID) {
        try (Connection connection = databaseManager.getConnection()) {
            String sql = "UPDATE whitelist SET player = ?, oldid = ? WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, newID);
                statement.setString(2, oldID);
                statement.setString(3, uuid);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            logger.error("更新玩家ID时发生错误 （事件ID: 13 - 数据库错误）", e);
        }
    }

    public CompletableFuture<Void> addPlayerToWhitelist(String inputPlayerName, String operator, String guarantor, String lotnumber, String description) {
        return CompletableFuture.runAsync(() -> {
            String uuid = getUUIDFromAPI(inputPlayerName);
            String playerName = getPlayerNameFromAPI(inputPlayerName);

            if (uuid == null || playerName == null) {
                logger.warn("无法获取UUID或玩家名称, 无法添加玩家到白名单");
                return;
            }

            try (Connection connection = databaseManager.getConnection()) {
                // 修改查询语句以获取 player 列
                String checkSql = "SELECT player FROM whitelist WHERE uuid = ?";
                try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                    checkStatement.setString(1, uuid);
                    try (ResultSet resultSet = checkStatement.executeQuery()) {
                        if (resultSet.next()) {
                            // 从ResultSet中正确获取 player 列的值
                            String oldPlayerName = resultSet.getString("player");
                            String oldid = oldPlayerName.equals(playerName) ? null : oldPlayerName;

                            // 执行更新操作
                            String updateSql = "UPDATE whitelist SET player = ?, oldid = ?, operator = ?, guarantor = ?, lotnumber = ?, description = ?, deleteAt = 0, deleteReason = NULL, deleteOperator = NULL WHERE uuid = ?";
                            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                                updateStatement.setString(1, playerName);
                                updateStatement.setString(2, oldid);
                                updateStatement.setString(3, operator);
                                updateStatement.setString(4, guarantor);
                                updateStatement.setString(5, lotnumber);
                                updateStatement.setString(6, description);
                                updateStatement.setString(7, uuid);
                                updateStatement.executeUpdate();
                            }
                        } else {
                            // 插入新的记录
                            String insertSql = "INSERT INTO whitelist (uuid, player, oldid, operator, guarantor, lotnumber, description) VALUES (?, ?, NULL, ?, ?, ?, ?)";
                            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                                insertStatement.setString(1, uuid);
                                insertStatement.setString(2, playerName);
                                insertStatement.setString(3, operator);
                                insertStatement.setString(4, guarantor);
                                insertStatement.setString(5, lotnumber);
                                insertStatement.setString(6, description);
                                insertStatement.executeUpdate();
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
        }, WHITELIST_EXECUTOR);
    }

    public CompletableFuture<WhitelistRecord> queryWhitelist(String player) {
        return CompletableFuture.supplyAsync(() -> {
            String uuid = getUUIDFromAPI(player);
            if (uuid == null) {
                return null;
            }
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "SELECT * FROM whitelist WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return new WhitelistRecord(
                                    resultSet.getLong("id"),
                                    resultSet.getString("uuid"),
                                    resultSet.getString("player"),
                                    resultSet.getString("oldid"),
                                    resultSet.getString("operator"),
                                    resultSet.getString("guarantor"),
                                    resultSet.getString("lotnumber"),
                                    resultSet.getString("description"),
                                    resultSet.getTimestamp("time"),
                                    resultSet.getLong("deleteAt"),
                                    resultSet.getString("deleteOperator"),
                                    resultSet.getString("deleteReason")
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
            return null;
        }, WHITELIST_EXECUTOR);
    }

    public CompletableFuture<Void> removePlayerFromWhitelist(String player, String reason, String operator) {
        return CompletableFuture.runAsync(() -> {
            String uuid = getUUIDFromAPI(player);
            if (uuid == null) {
                logger.warn("无法获取UUID, 无法从白名单移除玩家");
                return;
            }
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "UPDATE whitelist SET deleteAt = ?, deleteReason = ?, deleteOperator = ? WHERE uuid = ? AND deleteAt = 0";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setString(2, reason);
                    statement.setString(3, operator);
                    statement.setString(4, uuid);
                    int affectedRows = statement.executeUpdate();
                    if (affectedRows == 0) {
                        logger.warn("移除操作未成功，未找到匹配的UUID记录。");
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
        });
    }

    // 根据批次号查询玩家
    public CompletableFuture<List<WhitelistRecord>> queryByLotnumber(String lotnumber) {
        return CompletableFuture.supplyAsync(() -> {
            List<WhitelistRecord> records = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "SELECT * FROM whitelist WHERE lotnumber = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, lotnumber);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            records.add(new WhitelistRecord(
                                    resultSet.getLong("id"),
                                    resultSet.getString("uuid"),
                                    resultSet.getString("player"),
                                    resultSet.getString("oldid"),
                                    resultSet.getString("operator"),
                                    resultSet.getString("guarantor"),
                                    resultSet.getString("lotnumber"),
                                    resultSet.getString("description"),
                                    resultSet.getTimestamp("time"),
                                    resultSet.getLong("deleteAt"),
                                    resultSet.getString("deleteOperator"),
                                    resultSet.getString("deleteReason")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
            return records;
        }, WHITELIST_EXECUTOR);
    }

    // 查询所有白名单中的玩家
    public CompletableFuture<List<WhitelistRecord>> queryWhitelistedPlayers() {
        return CompletableFuture.supplyAsync(() -> {
            List<WhitelistRecord> records = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "SELECT * FROM whitelist WHERE deleteAt = 0";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            records.add(new WhitelistRecord(
                                    resultSet.getLong("id"),
                                    resultSet.getString("uuid"),
                                    resultSet.getString("player"),
                                    resultSet.getString("oldid"),
                                    resultSet.getString("operator"),
                                    resultSet.getString("guarantor"),
                                    resultSet.getString("lotnumber"),
                                    resultSet.getString("description"),
                                    resultSet.getTimestamp("time"),
                                    resultSet.getLong("deleteAt"),
                                    resultSet.getString("deleteOperator"),
                                    resultSet.getString("deleteReason")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
            return records;
        }, WHITELIST_EXECUTOR);
    }

    // 查询所有记录（包括已移除白名单的玩家）
    public CompletableFuture<List<WhitelistRecord>> queryAllRecords() {
        return CompletableFuture.supplyAsync(() -> {
            List<WhitelistRecord> records = new ArrayList<>();
            try (Connection connection = databaseManager.getConnection()) {
                String sql = "SELECT * FROM whitelist";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            records.add(new WhitelistRecord(
                                    resultSet.getLong("id"),
                                    resultSet.getString("uuid"),
                                    resultSet.getString("player"),
                                    resultSet.getString("oldid"),
                                    resultSet.getString("operator"),
                                    resultSet.getString("guarantor"),
                                    resultSet.getString("lotnumber"),
                                    resultSet.getString("description"),
                                    resultSet.getTimestamp("time"),
                                    resultSet.getLong("deleteAt"),
                                    resultSet.getString("deleteOperator"),
                                    resultSet.getString("deleteReason")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("SQL异常", e);
            }
            return records;
        }, WHITELIST_EXECUTOR);
    }
}
