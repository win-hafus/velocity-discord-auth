# velocity-discord-auth

A Velocity proxy plugin that gates server access behind Discord account linking via DiscordSRV. Designed for offline-mode (cracked) servers to provide proper account security.

## How It Works

1. On first join, a stable UUID is generated from `nickname + timestamp` and stored in the database.
2. This UUID is injected into the player's game profile via `GameProfileRequestEvent`.
3. On `LoginEvent`, the plugin checks whether the UUID is linked to a Discord account in DiscordSRV's tables.
4. If not linked — the player is kicked with a 4-digit code. They send it to the Discord bot to complete linking.
5. Once linked, the player is allowed through on every subsequent join.

## Requirements

- Java 17+
- Velocity 3.x
- MariaDB (shared with DiscordSRV)
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) installed on the backend server with `Experiment_JdbcTablePrefix` enabled

## Building

```bash
git clone https://git-vertex-homelab.mooo.com/hafus/velocity-discord-auth.git
cd velocity-discord-auth
mvn package
```

The compiled jar will be at `target/velocity-discord-auth-1.0.0.jar`.

## Installation

1. Copy the jar to your Velocity `plugins/` directory.
2. Start the proxy once to generate `plugins/velocity-discord-auth/config.properties`.
3. Edit the config and restart.

## Configuration

`plugins/velocity-discord-auth/config.properties`:

| Key                        | Default      | Description                                                                      |
| -------------------------- | ------------ | -------------------------------------------------------------------------------- |
| `db.host`                  | `127.0.0.1`  | MariaDB host                                                                     |
| `db.port`                  | `3306`       | MariaDB port                                                                     |
| `db.name`                  | `discord`    | Database name (same as DiscordSRV uses)                                          |
| `db.user`                  | `root`       | Database user                                                                    |
| `db.password`              | _(empty)_    | Database password                                                                |
| `discordsrv.table_prefix`  | `discordsrv` | Must match `Experiment_JdbcTablePrefix` in DiscordSRV's `config.yml`             |
| `messages.kick`            | Your account is not linked to Discord. Send the code {code} to the Discord bot to link your account            | Message shown when player is not linked. `{code}` is replaced with the link code |
| `messages.already_pending` | Your account is not linked yet.Your link code is {code}.Send it to the Discord bot            | Message shown when a pending code already exists                                 |

## Database Schema

The plugin creates two tables automatically:

- `vda_players` — maps nickname to stable UUID
- `vda_pending` — stores temporary link codes (expire after 10 minutes)

It also writes into DiscordSRV's `{prefix}_codes` table to integrate with the bot's `/discord link` flow.

## Acknowledgements

- [Velocity](https://velocitypowered.com/) — Modern Minecraft proxy
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) — Discord linking backend
- [HikariCP](https://github.com/brettwooldridge/HikariCP) — JDBC connection pool

## Contact

- Discord: [Vertex System](https://discord.gg/gTuh9z29)
- Email: [konstantin.pirs@gmail.com](mailto:konstantin.pirs@gmail.com)

## Author

- Gitea: [Hafus](https://git-vertex-homelab.mooo.com/Hafus)
- GitHub: [Win-hafus](https://github.com/win-hafus)

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
