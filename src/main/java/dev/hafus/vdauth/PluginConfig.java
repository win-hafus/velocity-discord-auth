package dev.hafus.vdauth;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class PluginConfig {

    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final String tablePrefix;
    private final String kickMessage;
    private final String alreadyPendingMessage;

    public PluginConfig(Path dataDirectory) throws IOException {
        Path configFile = dataDirectory.resolve("config.properties");

        if (!Files.exists(configFile)) {
            Files.createDirectories(dataDirectory);
            try (InputStream defaultConfig = getClass().getResourceAsStream("/config.properties")) {
                if (defaultConfig == null) throw new IOException("Default config not found in jar");
                Files.copy(defaultConfig, configFile);
            }
        }

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        dbHost      = props.getProperty("db.host",   "localhost");
        dbPort      = Integer.parseInt(props.getProperty("db.port", "3306"));
        dbName      = props.getProperty("db.name",   "discord");
        dbUser      = props.getProperty("db.user",   "root");
        dbPassword  = props.getProperty("db.password", "");
        tablePrefix = props.getProperty("discordsrv.table_prefix", "discordsrv");
        kickMessage = props.getProperty("messages.kick",
                "§cYour account is not linked to Discord.\n§7Send the code §e{code} §7to the Discord bot to link.");
        alreadyPendingMessage = props.getProperty("messages.already_pending",
                "§cYour account is not linked.\n§7Your link code is §e{code}§7.\n§7Send it to the Discord bot.");
    }

    public String getDbHost()       { return dbHost; }
    public int    getDbPort()       { return dbPort; }
    public String getDbName()       { return dbName; }
    public String getDbUser()       { return dbUser; }
    public String getDbPassword()   { return dbPassword; }
    public String getTablePrefix()  { return tablePrefix; }
    public String getKickMessage()  { return kickMessage; }
    public String getAlreadyPendingMessage() { return alreadyPendingMessage; }
}
