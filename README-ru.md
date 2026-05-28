# velocity-discord-auth

Плагин для прокси Velocity, который блокирует вход на сервер до тех пор, пока игрок не привяжет аккаунт Discord через DiscordSRV. Разработан для пиратских серверов (offline-mode) с целью обеспечить нормальную защиту аккаунтов.

## Как это работает

1. При первом входе генерируется стабильный UUID на основе `nickname + timestamp` и сохраняется в базе данных.
2. Этот UUID подставляется в игровой профиль игрока через `GameProfileRequestEvent`.
3. При `LoginEvent` плагин проверяет, привязан ли UUID к аккаунту Discord в таблицах DiscordSRV.
4. Если не привязан — игрок получает кик с 4-значным кодом. Код нужно отправить Discord-боту для завершения привязки.
5. После привязки игрок беспрепятственно заходит на сервер при каждом следующем входе.

## Требования

- Java 17+
- Velocity 3.x
- MariaDB (общая с DiscordSRV)
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) на бэкенд-сервере с включённым `Experiment_JdbcTablePrefix`

## Сборка

```bash
git clone https://git-vertex-homelab.mooo.com/hafus/velocity-discord-auth.git
cd velocity-discord-auth
mvn package
```

Скомпилированный jar будет находиться по пути `target/velocity-discord-auth-1.0.0.jar`.

## Установка

1. Скопируй jar в папку `plugins/` Velocity.
2. Запусти прокси один раз — будет создан файл `plugins/velocity-discord-auth/config.properties`.
3. Заполни конфиг и перезапусти прокси.

## Конфигурация

`plugins/velocity-discord-auth/config.properties`:

| Ключ                       | По умолчанию | Описание                                                                      |
| -------------------------- | ------------ | ----------------------------------------------------------------------------- |
| `db.host`                  | `127.0.0.1`  | Хост MariaDB                                                                  |
| `db.port`                  | `3306`       | Порт MariaDB                                                                  |
| `db.name`                  | `discord`    | Имя базы данных (та же, что использует DiscordSRV)                            |
| `db.user`                  | `root`       | Пользователь БД                                                               |
| `db.password`              | _(пусто)_    | Пароль БД                                                                     |
| `discordsrv.table_prefix`  | `discordsrv` | Должен совпадать с `Experiment_JdbcTablePrefix` в `config.yml` DiscordSRV     |
| `messages.kick`            | —            | Сообщение при кике непривязанного игрока. `{code}` заменяется на код привязки |
| `messages.already_pending` | —            | Сообщение, если код уже был выдан ранее                                       |

## Схема базы данных

Плагин автоматически создаёт две таблицы:

- `vda_players` — хранит соответствие nickname → стабильный UUID
- `vda_pending` — хранит временные коды привязки (истекают через 10 минут)

Также записывает в таблицу `{prefix}_codes` DiscordSRV для интеграции с командой `/discord link` бота.

## Благодарности

- [Velocity](https://velocitypowered.com/) — современный прокси для Minecraft
- [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV) — бэкенд привязки Discord
- [HikariCP](https://github.com/brettwooldridge/HikariCP) — пул соединений JDBC

## Контакты

- Discord: [Vertex System](https://discord.gg/gTuh9z29)
- Email: [konstantin.pirs@gmail.com](mailto:konstantin.pirs@gmail.com)

## Автор

[Hafus](https://git-vertex-homelab.mooo.com/Hafus)

## Лицензия

Проект распространяется под лицензией [GNU General Public License v3.0](LICENSE).
