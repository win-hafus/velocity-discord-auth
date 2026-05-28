package dev.hafus.vdauth;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "velocity-discord-auth",
    name = "VelocityDiscordAuth",
    version = "1.0.0",
    authors = {"hfv5"}
)
public class VelocityDiscordAuth {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private PluginConfig config;
    private DatabaseManager db;

    @Inject
    public VelocityDiscordAuth(ProxyServer server, Logger logger,
        @DataDirectory Path dataDirectory) {
            this.server = server;
            this.logger = logger;
            this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            config = new PluginConfig(dataDirectory);
            db = new DatabaseManager(config, logger);
            logger.info("VelocityDiscordAuth started. Connected to database '{}'.", config.getDbName());
        } catch (Exception e) {
            logger.error("Failed to initialize VelocityDiscordAuth", e);
            return;
        }

        // Cleanup expired codes every 5 minutes
        server.getScheduler()
            .buildTask(this, () -> {
                try { db.cleanupExpired(); }
                catch (SQLException e) { logger.warn("Cleanup error", e); }
            })
            .repeat(5, TimeUnit.MINUTES)
            .schedule();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (db != null) db.close();
    }

    /**
     * GameProfileRequestEvent fires during login, before the player fully connects.
     * This is where we inject the stable UUID into the player's game profile.
     *
     * Velocity calls this event after authentication; the profile contains the
     * username. We replace the UUID with our stored one (or create it on first join).
     */
    @Subscribe
    public void onGameProfileRequest(GameProfileRequestEvent event) {
        if (db == null) return;

        String username = event.getUsername();
        try {
            UUID stableUuid = db.getOrCreateUuid(username);

            // Replace UUID in the game profile Velocity will use for this session
            GameProfile original = event.getGameProfile();
            GameProfile patched = new GameProfile(stableUuid, original.getName(), original.getProperties());
            event.setGameProfile(patched);

            logger.debug("Set UUID {} for player '{}'", stableUuid, username);
        } catch (SQLException e) {
            logger.error("DB error during GameProfileRequest for '{}'", username, e);
        }
    }

    /**
     * LoginEvent fires just before the player is allowed into the proxy.
     * At this point the UUID in the profile is already our stable one.
     * We check if they're linked; if not, kick them with the link code.
     */
    @Subscribe
    public void onLogin(LoginEvent event) {
        if (db == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        try {
            if (db.isLinked(uuid)) {
                // All good — let them through
                logger.info("Player '{}' ({}) is linked, allowing join.", username, uuid);
                return;
            }

            // Not linked — generate a code and kick
            String code = db.generateLinkCode(username, uuid);

            String rawMessage = config.getKickMessage().replace("{code}", code);
            Component kickComponent = LegacyComponentSerializer.legacySection().deserialize(rawMessage);

            event.setResult(LoginEvent.ComponentResult.denied(kickComponent));
            logger.info("Player '{}' ({}) is not linked. Kicked with code {}.", username, uuid, code);

        } catch (SQLException e) {
            logger.error("DB error during login check for '{}'", username, e);
            event.setResult(LoginEvent.ComponentResult.denied(
                Component.text("§cInternal error. Please try again.")));
        }
    }
}
