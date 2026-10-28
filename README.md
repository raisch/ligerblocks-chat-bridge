# LigerBlocks Chat Bridge

A server-side NeoForge mod for bridging Minecraft chat and Slack. This repository
contains the Milestone 2 outbound Minecraft-to-Slack implementation and the
Milestone 3 Slack-to-Minecraft relay.

## Requirements

- Java 21
- No separate Gradle installation is required; use the included wrapper.

## Development

```sh
./gradlew runServer
```

For a local client development run:

```sh
./gradlew runClient
```

Build the distributable mod JAR with:

```sh
./gradlew clean build
```

The JAR is written to `build/libs/`.

The build also runs `verifyModJar`, which checks that the distributable JAR
contains the mod entry point and expanded NeoForge metadata:

```sh
./gradlew verifyModJar
```

## Dedicated-server smoke test

Run the generated dedicated-server configuration locally:

```sh
./gradlew runServer
```

The server should log `LigerBlocks Chat Bridge loaded` and reach the normal
`Done (...)! For help, type "help"` state. The run is server-side only; no
Minecraft client mod is required. The generated `run/` directory is ignored by
Git and can be removed between smoke tests.

The development server forwards terminal input to Minecraft. Type `stop` in
the same terminal after the server reaches the ready state for a graceful
shutdown and server-stop notification test.

## Installation

See [INSTALLATION.md](INSTALLATION.md) for comprehensive installation
instructions, including Slack app setup, standalone server installation,
Docker deployment, configuration, verification, upgrades, and troubleshooting.

The mod targets Minecraft 1.21.1, NeoForge 21.1.256, and Java 21. Its metadata
rejects incompatible Minecraft and NeoForge versions before the mod is loaded.

## Docker deployment

The included [docker-compose.yml](./docker-compose.yml) runs a persistent
NeoForge 1.21.1 server with Java 21 and mounts the built bridge mod read-only.
Build the release artifact first:

```sh
./gradlew clean build
sha256sum -c build/libs/ligerblocks_chat_bridge-0.1.0.jar.sha256
```

Create two local secret files with restrictive permissions. Keep them outside
source control:

```sh
mkdir -p secrets
printf '%s\n' 'xoxb-...' > secrets/slack-bot-token
printf '%s\n' 'xapp-...' > secrets/slack-app-token
chmod 600 secrets/slack-bot-token secrets/slack-app-token
docker compose up -d
docker compose logs -f minecraft
```

Compose mounts these files as Docker secrets. They are not written to the
ordinary Minecraft configuration or included in the mod JAR.

The `minecraft-data` volume preserves the world, NeoForge configuration, and
logs across container recreation. Stop and recreate the container with:

```sh
docker compose down
docker compose up -d
```

The bridge mod is upgraded by replacing the mounted JAR and recreating the
container. Keep a copy of the previous JAR for rollback, then restore it and
recreate the container if the new version is unsuitable. The Minecraft server
must be fully stopped before replacing the JAR.

The container image manages the NeoForge server installation. The bridge
requires the Slack bot to have `channels:history` and `users:read`, the app
token to have `connections:write`, and the bot to be a member of the
configured channel. Socket Mode reconnects automatically after transient
network loss; persistent Slack outages are reported in the server log and
outbound messages remain bounded by `relay.queueCapacity`.

## Outbound Slack relay

The relay is disabled by default. Enable it in
`config/ligerblocks_chat_bridge-common.toml`:

```toml
[relay]
enabled = true
channelId = "C0123456789"
slackIconUrl = "https://ligerblocks.com/minecraft_slack_avatar_96x96.png"
slackUsername = ""
messageFormat = "<{player}> {message}"
queueCapacity = 256
joinNotifications = true
leaveNotifications = true
deathNotifications = true
advancementNotifications = true
serverStartNotifications = true
serverStopNotifications = true
userEvents = true
systemEvents = true
minecraftToSlack = true
slackToMinecraft = true
```

`userEvents` controls player join, leave, death, and advancement notifications.
`systemEvents` controls server-start and server-stop notifications.
`minecraftToSlack` gates all Minecraft chat and event delivery to Slack.
`slackToMinecraft` gates ordinary Slack messages delivered to Minecraft. These
configuration switches are independent of the per-event notification settings
and can be changed in the common config without changing command mappings.
`slackIconUrl` is the HTTPS image URL used for outbound Slack message icons.
`slackUsername` optionally sets the username used for outbound Slack messages;
leave it blank to use Slack's default app identity.

The channel ID is an ordinary configuration value. The Slack bot token must
remain outside the configuration file and is loaded from one of these
environment variables:

- `LIGERBLOCKS_SLACK_BOT_TOKEN` — token value.
- `LIGERBLOCKS_SLACK_BOT_TOKEN_FILE` — path to a mounted file containing the
  token. The file is trimmed and must not be empty.

The direct token environment variable takes precedence. Tokens are never
written to configuration or logs. The relay starts only after the server is
available and the configuration and token pass validation. If validation
fails, the server continues running with the relay disabled and an actionable
diagnostic is logged.

## Inbound Slack relay

The inbound relay uses Slack Socket Mode and requires a separate app-level
token:

- `LIGERBLOCKS_SLACK_APP_TOKEN` — app-level token beginning with `xapp-`.
- `LIGERBLOCKS_SLACK_APP_TOKEN_FILE` — path to a mounted file containing that
  token.

The direct token environment variable takes precedence. In the Slack app
configuration, enable Socket Mode, grant the app-level token the
`connections:write` scope, grant the bot `users:read`, subscribe to message
events, and reinstall the app after changing scopes. The bot must be a member
of the configured channel.

The server log reports Socket Mode connection attempts, confirmed connections
and reconnections, unexpected disconnects, connection errors, and intentional
shutdown. The Slack SDK's automatic reconnect behavior is reported in
disconnect diagnostics.

Only ordinary messages from the exact configured channel are accepted. Bot
messages, edited/deleted messages, thread subtypes, blank messages, and
messages from other channels are ignored. Accepted messages are broadcast as
`[Slack] <user-id> message` after dispatching onto the Minecraft server thread.

To test the inbound path, start the server with both tokens exported, wait for
the normal `Done (...)!` startup message, and send a normal message in the
configured Slack channel. The message should appear to online players. The
app-level token is never written to configuration or logs.

Minecraft chat events are converted to immutable outbound messages and placed
on a bounded queue. The server thread never performs the Slack HTTP request.
When the queue is full, the message is rejected and a warning is emitted.
Inbound Slack messages and successfully queued or delivered outbound Slack
messages are logged with their channel and message text to simplify
troubleshooting. Tokens are never logged. Slack HTTP/API failures are logged
with the failure details. HTTP 429 responses honor the `Retry-After` delay,
capped at 60 seconds. Transient delivery failures use at most three attempts
with bounded exponential backoff. Server shutdown stops the sender and clears
queued messages after the final shutdown notification is attempted.

## Slack `/mc` command executor

The bridge supports a native Slack `/mc` slash command over the existing Socket
Mode connection. No Request URL or second WebSocket is required.

Configure commands in the Minecraft server's
`config/ligerblocks_chat_bridge-slack-commands.json` file. The bundled defaults
provide:

See [COMMANDS.md](COMMANDS.md) for the complete parser reference, JSON schema,
argument forms, validation rules, and examples.

```text
/mc players
/mc daytime
/mc whitelist list
/mc whitelist add Steve
```

The loader accepts `//` single-line comments and `/* ... */` multiline
comments. Comment markers inside quoted strings are preserved.

Admin mappings use the top-level `admins` list. Display names and Minecraft
usernames are not authorization identities:

```json
{
  "admins": ["U0123456789"],
  "commands": {
    "whitelist": {
      "minecraft": "whitelist {action} {player}",
      "permission": "admin",
      "description": "List, add, or remove whitelisted players",
      "arguments": {
        "action": {
          "type": "option",
          "options": ["list", "add", "del"]
        },
        "player": {
          "type": "minecraft_username",
          "required": false,
          "requiredWhen": {
            "argument": "action",
            "values": ["add", "del"]
          }
        }
      }
    }
  }
}
```

To configure the Slack command:

1. Open <https://api.slack.com/apps> and select the bridge app.
2. Open **Features → Slash Commands** and choose **Create New Command**.
3. Set the command to `/mc` and describe it as `Run an allowlisted Minecraft command`.
4. Leave **Request URL** unset because Socket Mode is already enabled.
5. Save the command and reinstall the app if Slack requests it.
6. Confirm the bot is a member of the configured channel.

The command configuration is loaded during server startup. An administrator
listed in the top-level `admins` list can reload an external configuration
without restarting Minecraft:

```text
/mc reload
```

Reload parses and validates the complete replacement before publishing it. A
failed reload leaves the previous valid registry active. The bundled defaults
are used when the external file does not exist; `/mc reload` requires an
external configuration file.

Use the built-in help command to list the mappings available to the requesting
Slack user:

```text
/mc help
```

Help includes each mapping's `description`, argument placeholders, and aliases.
The complete visible command list is sorted alphabetically. Admin-only mappings
and built-in administrator commands are omitted for non-admin users. For
administrators, those commands are shown in bold, and the first line explains
the formatting. The `help` command itself does not execute a Minecraft command.

Built-in commands include:

```text
/mc help
/mc reload
/mc config
/mc admin add SLACK_ID [-reload] [-list]
/mc admin list
/mc admin del SLACK_ID [-reload] [-list]
/mc events [all|user|server] [on|off]
/mc msgs [from|to] [on|off]
```

`/mc config` returns the command JSON privately, excluding the top-level
`admins` list. `/mc admin add`, `/mc admin list`, and `/mc admin del` manage
administrator IDs. The final administrator cannot be removed.

By default, command results are private ephemeral responses visible only to the
invoking user. A command can explicitly publish its result to the configured
channel with:

```json
"response": "channel"
```

The default is:

```json
"response": "private"
```

Command names and aliases support unique prefixes. For example, `/mc ni`
resolves to `night` when it is the only matching command. Ambiguous prefixes
such as `/mc n` when both `night` and `noon` exist are rejected with the
matching command names. Exact command and alias matches always take precedence,
and authorization is checked against the resolved canonical command.

Recommended Slack test sequence:

```text
/mc players
/mc daytime
/mc whitelist list
/mc whitelist add Steve
/mc whitelist del Steve
/mc unknown
/mc whitelist add
/mc whitelist add "not a valid username"
/mc help
/mc reload
/mc config
/mc admin list
/mc events user off
```

Verify that successful commands return bounded Minecraft feedback, invalid or
unknown commands are rejected without execution, unauthorized users cannot run
admin mappings, and reload failures report that the previous configuration was
retained. Test `/mc` from a non-configured channel and confirm it is rejected.
After editing the mapping file, run `/mc reload` and verify a newly added
mapping works without restarting the server.

The `/mc events` command changes user and server event notification switches
at runtime. These changes are not persisted to the TOML configuration and
return to the configured values after a server restart.

The `/mc msgs` command controls message relay directions at runtime:

- `from` enables or disables Minecraft chat and event messages sent to Slack.
- `to` enables or disables ordinary Slack messages sent to Minecraft.

These changes are not persisted to the TOML configuration and return to the
configured values after a server restart.

Server and player lifecycle notifications use the same outbound queue:

- `[Server] server started`
- `[Server] CoachRob joined`
- `[Server] CoachRob left`
- `[Server] CoachRob died`
- `[Server] CoachRob earned advancement: Stone Age`
- `[Server] server stopping`

Each notification type can be disabled independently with the corresponding
configuration value above. The server-stop notification is delivered before
the outbound worker shuts down, subject to normal Slack availability and the
configured shutdown timeout.

## Slack SDK packaging decision

Milestone 2 uses Java's standard HTTP client for the outbound Web API call, so
the production JAR still has no external Slack runtime dependency.

The Socket Mode implementation embeds the Slack SDK and Tyrus WebSocket
runtime classes directly in the mod JAR. This avoids NeoForge classloader
isolation between the Slack SDK modules. A separate bridge service is not
required.

The application reads exported environment variables; it does not parse a
`.env` file automatically. For local testing, load the file into the process
environment before starting the server:

```sh
set -a
. ./.env
set +a
./gradlew runServer
```

Set `relay.channelId` in
`config/ligerblocks_chat_bridge-common.toml`; `LIGERBLOCKS_CHANNEL_ID` is not
read as a replacement for the ordinary NeoForge configuration value.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.256
- Java 21
