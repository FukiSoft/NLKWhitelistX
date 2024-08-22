package com.leitingsd.plugins.nlkwhitelistx.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.leitingsd.plugins.nlkwhitelistx.NLKWhitelistX;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;  // 添加此行以导入 DriverManager
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class DatabaseManager {

    private final NLKWhitelistX plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(NLKWhitelistX plugin) {
        this.plugin = plugin;
    }

    public void init(Map<String, Object> config) {
        Logger logger = plugin.getLogger();
        HikariConfig hikariConfig = new HikariConfig();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC 驱动程序加载失败", e);
        }

        String host = (String) config.getOrDefault("host", "localhost");
        int port = Integer.parseInt(config.getOrDefault("port", 3306).toString());
        String database = (String) config.getOrDefault("database", "whitelist");
        String user = (String) config.getOrDefault("user", "root");
        String password = (String) config.getOrDefault("password", "password");
        boolean useSSL = Boolean.parseBoolean(config.getOrDefault("usessl", false).toString());

        // 先连接到没有指定数据库的MySQL服务器
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=" + useSSL;
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            try (Statement statement = connection.createStatement()) {
                // 创建数据库（如果不存在）
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS " + database);
                logger.info("数据库 '{}' 已检查/创建", database);
            }
        } catch (SQLException e) {
            logger.error("无法创建数据库 '{}'", database, e);
            return;
        }

        // 设置连接到指定数据库的URL
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // 添加可选的 HikariCP 属性
        if (config.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) config.get("properties");
            properties.forEach((key, value) -> hikariConfig.addDataSourceProperty(key, value.toString()));
        }

        this.dataSource = new HikariDataSource(hikariConfig);
        logger.info("数据库连接已初始化");

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS whitelist (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "uuid VARCHAR(36) NOT NULL, " +   // 新增 uuid 字段
                    "player VARCHAR(255) NOT NULL, " +
                    "oldid VARCHAR(255), " +           // 新增 oldid 字段
                    "operator VARCHAR(255), " +
                    "guarantor VARCHAR(255), " +
                    "lotnumber VARCHAR(255), " +      // 将 train 更改为 lotnumber
                    "description TEXT, " +
                    "time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "deleteAt BIGINT DEFAULT 0, " +
                    "deleteReason TEXT, " +
                    "deleteOperator VARCHAR(255)" +
                    ")";
            statement.execute(sql);
            logger.info("表 'whitelist' 已检查/创建");
        } catch (SQLException e) {
            logger.error("无法创建表 'whitelist'", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
