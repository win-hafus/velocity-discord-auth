package dev.hafus.vdauth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final Logger logger;
    private final String tablePrefix;

    public DatabaseManager(PluginConfig config, Logger logger) throws SQLException {
        this.logger = logger;
        this.tablePrefix = config.getTablePrefix();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mariadb://" + config.getDbHost() + ":" + config.getDbPort()
                + "/" + config.getDbName() + "?useSSL=false&characterEncoding=UTF-8");
        hikari.setUsername(config.getDbUser());
        hikari.setPassword(config.getDbPassword());
        hikari.setMaximumPoolSize(5);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(5000);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.setPoolName("VelocityDiscordAuth");

        dataSource = new HikariDataSource(hikari);
        initTables();
    }

    private void initTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Table linking nickname -> stable UUID (generated on first join)
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS vda_players (
                    nickname VARCHAR(36) NOT NULL,
                    uuid     CHAR(36)    NOT NULL,
                    created_at BIGINT    NOT NULL,
                    PRIMARY KEY (nickname),
                    UNIQUE KEY uq_uuid (uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """).execute();

            // Pending link codes: nickname -> 4-digit code
            // DiscordSRV reads from discordsrv_codes (uuid -> code),
            // so we also write there. This table is our own bookkeeping.
            conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS vda_pending (
                    nickname   VARCHAR(36) NOT NULL,
                    uuid       CHAR(36)    NOT NULL,
                    code       VARCHAR(10) NOT NULL,
                    expires_at BIGINT      NOT NULL,
                    PRIMARY KEY (nickname)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """).execute();
        }
    }

    /**
     * Returns the stable UUID for a nickname, creating one if first join.
     * UUID is generated from nickname + current timestamp via UUID.nameUUIDFromBytes,
     * which gives a deterministic v3-style UUID unique to this (name, time) pair.
     */
    public UUID getOrCreateUuid(String nickname) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Check if already registered
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid FROM vda_players WHERE nickname = ?")) {
                ps.setString(1, nickname.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
                }
            }

            // First join — generate UUID from nickname + timestamp
            long now = System.currentTimeMillis();
            String seed = nickname.toLowerCase() + ":" + now;
            UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes());

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vda_players (nickname, uuid, created_at) VALUES (?, ?, ?)")) {
                ps.setString(1, nickname.toLowerCase());
                ps.setString(2, uuid.toString());
                ps.setLong(3, now);
                ps.executeUpdate();
            }

            logger.info("Assigned UUID {} to new player '{}'", uuid, nickname);
            return uuid;
        }
    }

    /**
     * Returns true if this UUID already has a linked Discord account in DiscordSRV's table.
     */
    public boolean isLinked(UUID uuid) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM " + tablePrefix + "_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String generateLinkCode(String nickname, UUID uuid) throws SQLException {
        // 4-digits code for DiscordSRV
        String code = String.format("%04d", (int)(Math.random() * 10_000));
        long now = System.currentTimeMillis();
        long expiresAt = now + 10 * 60 * 1000L;

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM vda_pending WHERE nickname = ?")) {
                del.setString(1, nickname.toLowerCase());
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vda_pending (nickname, uuid, code, expires_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, nickname.toLowerCase());
                ps.setString(2, uuid.toString());
                ps.setString(3, code);
                ps.setLong(4, expiresAt);
                ps.executeUpdate();
            }

            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM " + tablePrefix + "_codes WHERE uuid = ?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + tablePrefix + "_codes (code, uuid, expiration) VALUES (?, ?, ?)")) {
                ps.setString(1, code);
                ps.setString(2, uuid.toString());
                ps.setLong(3, expiresAt);
                ps.executeUpdate();
            }
        }

        return code;
    }
    /**
     * Cleans up expired pending entries (called on startup and periodically).
     */
    public void cleanupExpired() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM vda_pending WHERE expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            int deleted = ps.executeUpdate();
            if (deleted > 0) logger.info("Cleaned up {} expired link codes", deleted);
        }
    }

    /**
     * Returns the stored UUID for a player if it exists, empty otherwise.
     */
    public Optional<UUID> findUuid(String nickname) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM vda_players WHERE nickname = ?")) {
            ps.setString(1, nickname.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(UUID.fromString(rs.getString("uuid")));
                return Optional.empty();
            }
        }
    }

    public void close() {
        dataSource.close();
    }
}
