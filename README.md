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

## Installation

1. Run `./gradlew clean build`.
2. Copy `build/libs/ligerblocks_chat_bridge-0.1.0.jar` to the dedicated
   server's `mods/` directory.
3. Start the server with Minecraft 1.21.1, NeoForge 21.1.256, and Java 21.
4. Confirm the startup log reports `LigerBlocks Chat Bridge loaded` and reaches
   the normal ready state.

The metadata rejects incompatible Minecraft and NeoForge versions before the
mod is loaded.

## Outbound Slack relay

The relay is disabled by default. Enable it in
`config/ligerblocks_chat_bridge-common.toml`:

```toml
[relay]
enabled = true
channelId = "C0123456789"
messageFormat = "<{player}> {message}"
queueCapacity = 256
```

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
`connections:write` scope, subscribe to message events, and reinstall the app
after changing scopes. The bot must be a member of the configured channel.

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
Slack HTTP/API failures are logged without message contents or credentials.
HTTP 429 responses honor the `Retry-After` delay, capped at 60 seconds, and
retry the failed delivery once. Server shutdown stops the sender and clears
queued messages.

## Slack SDK packaging decision

Milestone 2 uses Java's standard HTTP client for the outbound Web API call, so
the production JAR still has no external Slack runtime dependency.

The Socket Mode implementation uses NeoForge Jar-in-Jar packaging for the
Slack SDK and Tyrus WebSocket runtime. The development server run also adds
the SDK to its additional runtime classpath. A separate bridge service is not
required.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.256
- Java 21
