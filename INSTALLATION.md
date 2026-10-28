# LigerBlocks Chat Bridge Installation

This guide installs the LigerBlocks Chat Bridge on a NeoForge dedicated server
and connects it to Slack. The bridge is a **server-side** mod. Players do not
need to install the bridge mod on their clients.

## Compatibility

The current release targets:

- Minecraft **1.21.1**
- NeoForge **21.1.256** or a compatible later 21.1 release
- Java **21**
- Slack Socket Mode

Do not install the bridge into a Fabric, Forge, or Vanilla server. The
Minecraft server and NeoForge versions must match the versions declared by the
mod.

## Choose a deployment

There are two supported approaches:

- **Existing dedicated server:** install the JAR into the server's `mods`
  directory and provide Slack tokens as environment variables or token files.
- **Packwiz-managed server:** add the mod to the pack's source files and
  update the server through the pack's normal Packwiz installation workflow.
- **Docker:** use the repository's `docker-compose.yml`, which provisions a
  persistent NeoForge server and mounts the built JAR.

The Docker deployment is convenient for a new test server. An existing server
should use the standalone or Packwiz installation steps.

## 1. Prepare Slack

Create or select a Slack app at <https://api.slack.com/apps>.

### Bot token scopes

Under **OAuth & Permissions**, add these bot token scopes:

- `chat:write` — post messages to Slack.
- `chat:write.customize` — allow the configured `slackUsername` and
  `slackIconUrl` values to customize outbound messages.
- `channels:history` — receive messages from public channels.
- `users:read` — resolve Slack user display names.

If the bridge will use a private channel, add the private-channel history scope
required by Slack for that installation, typically `groups:history`.

### Socket Mode

1. Open **Socket Mode** in the Slack app settings.
2. Enable Socket Mode.
3. Create an app-level token with the `connections:write` scope.
4. Save the token beginning with `xapp-`.

Under **Event Subscriptions**:

1. Enable events.
2. Subscribe to `message.channels` for public channels.
3. Subscribe to `message.groups` for private channels.

Under **Features → Slash Commands**:

1. Create a command named `/mc`.
2. Use a description such as `Run an allowlisted Minecraft command`.
3. Do not configure a Request URL; the bridge receives the command over Socket
   Mode.

Install or reinstall the app to the workspace after changing scopes. Copy the
bot token beginning with `xoxb-` and the app token beginning with `xapp-` into
secure storage. Never commit either token to Git.

### Add the bot to the channel

Open the channel that the bridge will use and run:

```text
/invite @YourAppName
```

Copy the channel ID from **Channel details → About → Channel ID**. Use the ID,
not the channel name. The bridge accepts ordinary messages and `/mc` commands
only from this exact channel.

## 2. Build the mod

Install Java 21, then build from the repository root:

```sh
./gradlew clean build
```

The distributable artifact is:

```text
build/libs/ligerblocks_chat_bridge-0.1.0.jar
```

The build also creates a SHA-256 checksum:

```sh
sha256sum -c build/libs/ligerblocks_chat_bridge-0.1.0.jar.sha256
```

On macOS, use `shasum -a 256` if `sha256sum` is unavailable.

## 3. Install the supplied configuration

The repository includes clean-install configuration templates in the top-level
[`config/`](config/) directory:

- [`ligerblocks_chat_bridge-common.toml`](config/ligerblocks_chat_bridge-common.toml)
  enables the relay and contains the channel and outbound-message settings.
- [`ligerblocks_chat_bridge-slack-commands.json`](config/ligerblocks_chat_bridge-slack-commands.json)
  defines the `/mc` commands and the administrator list.

The common TOML file is required for a useful clean installation: NeoForge's
generated default has the relay disabled and no channel configured. Before
copying it, replace these values:

```toml
channelId = "YOUR_SLACK_CHANNEL_ID"
slackUsername = "Minecraft"
slackIconUrl = "https://example.com/minecraft-avatar.png"
```

Replace `SLACK_USERID` in the command JSON with the Slack user ID of each
administrator. The command JSON is optional because the mod has bundled
defaults, but it is recommended for a real installation because it supplies
your administrator list and the editable command registry.

Copy both files into the server's `config/` directory after NeoForge has
created that directory:

```sh
cp config/ligerblocks_chat_bridge-common.toml /path/to/server/config/
cp config/ligerblocks_chat_bridge-slack-commands.json /path/to/server/config/
```

For Docker, use the container paths described in the Docker section below.
Do not copy secrets into either configuration file.

## 4A. Install with Packwiz

If the server is managed by Packwiz, Packwiz is the source of truth for the
server's mod list. Do not copy the bridge JAR directly into the live
`mods/` directory and then run Packwiz, because a later pack update can remove
files that are not recorded in `index.toml`.

Build the mod from the repository root:

```text
./gradlew clean build
```

Packwiz does not automatically add an arbitrary local JAR when it runs
`packwiz refresh`. The `.pw.toml` files in `mods/` are metadata entries, and a
Packwiz-managed entry requires a download source. Therefore, a local JAR
cannot be distributed through the normal Packwiz metadata workflow without
making it available at a URL.

For a fully Packwiz-managed installation, publish the JAR to a private or
public HTTPS artifact location and add it with:

```sh
packwiz url add https://downloads.example.com/ligerblocks_chat_bridge-0.1.0.jar
packwiz refresh
```

The URL does not need to be public, but it must be reachable by the Packwiz
installer running on the server. An internal artifact server or a
pack-hosting service can be used instead of a public download site. Mark the
generated entry as **server-side** because the bridge is not required on
clients. Commit the generated `.pw.toml` file and updated `index.toml`.

If publishing an artifact is not possible, install the bridge outside Packwiz
as a local server-only file after Packwiz finishes installing the rest of the
pack:

```sh
packwiz refresh
cp /path/to/ligerblocks-chat-bridge/build/libs/ligerblocks_chat_bridge-0.1.0.jar /path/to/server/mods/
```

In this arrangement, the bridge JAR is intentionally not shown by
`packwiz list` and is not tracked by Packwiz. Your server update procedure must
copy the JAR again after every build or deployment. Keep the JAR outside the
Packwiz pack repository if the pack's update process replaces the entire
`mods/` directory.

If the bridge is published to Modrinth or CurseForge, prefer the corresponding
Packwiz platform command instead:

```sh
packwiz modrinth add <project-id-or-slug>
# or
packwiz curseforge add <project-id-or-slug>
```

The exact platform command depends on the Packwiz version installed by the
pack. Confirm the generated entry points to the server-compatible NeoForge
build.

Update the server using its normal Packwiz installer command. A common
installer-based setup looks like:

```sh
java -jar packwiz-installer-bootstrap.jar https://example.com/pack/pack.toml
```

Use the pack maintainer's existing URL and installer command if they differ.
Stop the Minecraft server before applying the mod update, then start it with
Java 21 and NeoForge 21.1.256.

After the mod is installed, configure the bridge in the server's
`config/ligerblocks_chat_bridge-common.toml` and provide the Slack tokens as
described in [Slack token configuration](#slack-token-configuration). Packwiz
manages the mod artifact; it does not replace the bridge's runtime TOML or
Slack secret configuration unless the pack explicitly includes those files.
For a clean install, copy the supplied configuration files into the server
configuration directory after the Packwiz update:

```sh
cp config/ligerblocks_chat_bridge-common.toml /path/to/server/config/
cp config/ligerblocks_chat_bridge-slack-commands.json /path/to/server/config/
```

For a custom local build update with a URL-backed Packwiz entry:

1. Run `./gradlew clean build`.
2. Publish the new JAR at the artifact URL.
3. Update the Packwiz entry and run `packwiz refresh`.
4. Commit the `.pw.toml` metadata and `index.toml`.
5. Apply the updated pack to the server.
6. Verify the server log reports `LigerBlocks Chat Bridge loaded`.

For a local side-loaded update, replace the JAR directly in the server's
`mods/` directory after Packwiz finishes:

```sh
cp /path/to/ligerblocks-chat-bridge/build/libs/ligerblocks_chat_bridge-0.1.0.jar /path/to/server/mods/
```

Keep the previous JAR available for rollback. Do not delete the server's
world or configuration while rolling back a mod.

## 4B. Install on an existing server

Stop the server before changing its mods:

```sh
cp build/libs/ligerblocks_chat_bridge-0.1.0.jar /path/to/server/mods/
```

Start the server with Java 21 and NeoForge 21.1.256. On the first startup,
NeoForge creates the common configuration file under:

```text
config/ligerblocks_chat_bridge-common.toml
```

Stop the server after the first startup, replace the generated common
configuration with the supplied template, and edit its Slack-specific values:

```sh
cp config/ligerblocks_chat_bridge-common.toml /path/to/server/config/
cp config/ligerblocks_chat_bridge-slack-commands.json /path/to/server/config/
```

In particular, update the `[relay]` section:

```toml
[relay]
enabled = true
channelId = "C0123456789"
slackIconUrl = "https://example.com/minecraft-avatar.png"
slackUsername = "Minecraft"
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

`slackIconUrl` is optional, but when set it must be a publicly reachable HTTPS
image URL with a valid certificate and an image response. `slackUsername` is
optional; leave it blank to use Slack's default app identity.

Provide the tokens before starting the server. Direct environment variables
take precedence over token-file variables:

```sh
export LIGERBLOCKS_SLACK_BOT_TOKEN='xoxb-...'
export LIGERBLOCKS_SLACK_APP_TOKEN='xapp-...'
java -jar <your-neoforge-server-launcher>.jar nogui
```

For local development, the repository's `.env` file is not read
automatically. Load it into the process environment first:

```sh
set -a
. ./.env
set +a
./gradlew runServer
```

For production, prefer token files with restrictive permissions:

```sh
chmod 600 secrets/slack-bot-token secrets/slack-app-token
export LIGERBLOCKS_SLACK_BOT_TOKEN_FILE=/path/to/secrets/slack-bot-token
export LIGERBLOCKS_SLACK_APP_TOKEN_FILE=/path/to/secrets/slack-app-token
```

## Slack token configuration

The bridge accepts tokens through direct environment variables or token files.
Direct environment variables take precedence:

- `LIGERBLOCKS_SLACK_BOT_TOKEN` or `LIGERBLOCKS_SLACK_BOT_TOKEN_FILE`
- `LIGERBLOCKS_SLACK_APP_TOKEN` or `LIGERBLOCKS_SLACK_APP_TOKEN_FILE`

The bot token begins with `xoxb-`; the app token begins with `xapp-`. Keep
token files outside the Packwiz repository and outside source control.

## 4C. Install with Docker

The repository includes a Docker Compose deployment using
`itzg/minecraft-server:java21`. It persists the server in the named
`minecraft-data` volume and mounts the repository's `mods/` directory into
`/data/mods`.

Create token files outside source control:

```sh
mkdir -p secrets
printf '%s\n' 'xoxb-...' > secrets/slack-bot-token
printf '%s\n' 'xapp-...' > secrets/slack-app-token
chmod 600 secrets/slack-bot-token secrets/slack-app-token
```

Build and start the server:

```sh
./gradlew clean build
mkdir -p mods
cp build/libs/ligerblocks_chat_bridge-0.1.0.jar mods/
docker compose up -d
docker compose logs -f minecraft
```

The Compose file bind-mounts `./mods` to `/data/mods` as read-only. Add every
server mod that should be loaded to the repository's `mods/` directory before
starting or recreating the container. The bridge JAR is therefore loaded from:

```text
./mods/ligerblocks_chat_bridge-0.1.0.jar
→ /data/mods/ligerblocks_chat_bridge-0.1.0.jar
```

When updating the bridge or another server mod:

```sh
./gradlew clean build
cp build/libs/ligerblocks_chat_bridge-0.1.0.jar mods/
docker compose up -d --force-recreate minecraft
```

On first startup, the container creates its configuration under `/data/config`.
The Compose file mounts the repository's `config/` directory at `/config`.
The image init process copies those files into the writable `/data/config`
directory, so NeoForge can create and update files such as `fml.toml`.
The repository's top-level `config/` directory is not mounted into the
container.
After the first startup, copy the configuration files into the persistent
container volume:

```sh
docker cp config/ligerblocks_chat_bridge-common.toml \
  ligerblocks-minecraft:/data/config/ligerblocks_chat_bridge-common.toml

docker cp config/ligerblocks_chat_bridge-slack-commands.json \
  ligerblocks-minecraft:/data/config/ligerblocks_chat_bridge-slack-commands.json

docker compose restart minecraft
```

If you edit either file later, copy it into `/data/config` again. Restart the
server after changing the common TOML configuration. The `/mc reload` command
reloads only the Slack command JSON; it does not reload the common TOML.

The Docker volume is persistent. Recreating the container is safe:

```sh
docker compose up -d --force-recreate minecraft
```

Do **not** use `docker compose down -v` unless you intentionally want to delete
the persistent Minecraft world and configuration.

## 5. Verify startup

A successful startup should include messages similar to:

```text
LigerBlocks Chat Bridge loaded
Slack outbound relay started for configured channel
Slack Socket Mode connected
```

The outbound log should show the configured channel ID:

```text
Queued outbound Slack message to channel C0123456789: [Server] server started
```

If the relay is disabled or configuration validation fails, Minecraft
continues starting but the Slack relay remains disabled. Read the actionable
diagnostic in the server log before testing Slack.

## 6. Test the bridge

### Minecraft to Slack

Send a Minecraft chat message. With the default format, Slack should show:

```text
<Steve> Hello from Minecraft
```

Server and player events use the `[Server]` prefix:

```text
[Server] server started
[Server] Steve joined
```

### Slack to Minecraft

Send an ordinary message in the configured Slack channel:

```text
Hello from Slack
```

It should appear in Minecraft as:

```text
[Slack] <U012345678> Hello from Slack
```

Messages from other channels, bot messages, edited/deleted messages, thread
subtypes, and blank messages are ignored.

### Slack commands

Run:

```text
/mc players
/mc help
/mc whitelist list
```

Command results are private by default. A mapping with `"response": "channel"`
publishes its result in the configured channel.

For the command schema, parser rules, administrator management, runtime relay
controls, and JSON comments, see [COMMANDS.md](COMMANDS.md).

## 7. Updating the mod

Build the new artifact and stop the server before replacing the old JAR:

```sh
./gradlew clean build
```

For a standalone server, replace the JAR in `mods/` and start the server.

For Docker, copy the new JAR into the bind-mounted `mods/` directory, then
recreate the container:

```sh
cp build/libs/ligerblocks_chat_bridge-0.1.0.jar mods/
docker compose up -d --force-recreate minecraft
```

Keep the previous JAR available for rollback. The `minecraft-data` volume
preserves the world, configuration, and logs across container recreation.

## Troubleshooting

### `Slack outbound relay started` is missing

Check that:

- `[relay] enabled = true`.
- `channelId` is a valid Slack channel ID.
- The bot token is present and begins with `xoxb-`.
- The server is using Java 21.

### `chat.postMessage` is rejected

Check that:

- The bot is a member of the configured channel.
- The channel ID is correct.
- The bot token has `chat:write`.
- `chat:write.customize` is installed if using `slackUsername` or
  `slackIconUrl`.
- The icon URL returns `200` with an image content type and has a valid HTTPS
  certificate.

The bridge logs Slack's API error code, such as `missing_scope`,
`channel_not_found`, or `is_archived`.

### Slack messages do not arrive in Minecraft

Check that:

- Socket Mode is enabled.
- The app token begins with `xapp-` and has `connections:write`.
- The bot is a member of the configured channel.
- The relevant message event subscription is enabled.
- `slackToMinecraft = true`.
- The message was sent in the exact configured channel.

### `/mc` is rejected

Check that:

- The `/mc` slash command exists in the Slack app.
- The command is being sent from the configured channel.
- The command JSON is valid.
- The user is in the top-level `admins` list for administrator commands.

After editing the command JSON, run:

```text
/mc reload
```

### The Docker server uses an old configuration

The Docker deployment stores configuration in the persistent `minecraft-data`
volume. Editing the repository's `config/` files alone does not update the
container. Copy the file again:

```sh
docker cp config/ligerblocks_chat_bridge-common.toml \
  ligerblocks-minecraft:/data/config/ligerblocks_chat_bridge-common.toml
docker compose restart minecraft
```

## Security checklist

- Never commit Slack tokens or `.env` files.
- Keep token files readable only by the account running Docker or Minecraft.
- Use HTTPS for `slackIconUrl`.
- Use a dedicated Slack channel for the bridge.
- Keep the Slack app's scopes limited to the features you use.
- Back up the Minecraft world before changing the server or mod version.
