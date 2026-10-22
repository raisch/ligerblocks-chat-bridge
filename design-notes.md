I've examined the actual Java source, Gradle build configuration, and Slack integration in [transcental/SlackBridge](https://github.com/transcental/SlackBridge?utm_source=chatgpt.com).

My recommendation is now more definite: create a new NeoForge project, but selectively reuse the SlackBridge implementation. The project is small enough that a traditional port offers little advantage, and its existing code contains several problems worth correcting rather than carrying forward.

## What the repository contains

The implementation is remarkably compact:

| Source file          | Purpose                                       | Recommendation                |
| -------------------- | --------------------------------------------- | ----------------------------- |
| `Slackbridge.java`   | Mod initialization, Minecraft event handling  | Rewrite for NeoForge          |
| `SlackApp.java`      | Slack Socket Mode, messages, command handling | Refactor and reuse concepts   |
| `Config.java`        | Configuration properties                      | Reuse data model with changes |
| `ConfigManager.java` | JSON configuration loading                    | Replace                       |
| `Shrug.java`         | Custom Minecraft `/shrug` command             | Optional; rewrite if wanted   |
| `build.gradle`       | Fabric dependencies and build                 | Replace entirely              |

There are only five Java source files. That matters: this isn't a large application whose complex business logic justifies extensive migration work.

## Problems identified in the source

1\. Minecraft events can block on Slack API calls

The event handlers invoke `chatPostMessage` synchronously. Network delays could therefore block the Minecraft server's event-processing thread and cause lag.

2\. Slack messages are broadcast without server-thread scheduling

`sendMinecraftMessage()` calls the player manager directly from a Slack event callback. Minecraft operations should instead be marshalled onto the server thread using `server.execute(...)`.

3\. Incoming Slack messages aren't filtered by channel

The message handler checks the subtype but never compares the event's channel against `relayChannelId`. Consequently, messages from other channels visible to the bot could be relayed to Minecraft.

4\. Slack initialization and server lifecycle are poorly coordinated

The Slack bot starts in a daemon thread during mod initialization, before the Minecraft server has finished starting. Shutdown doesn't explicitly manage the Slack connection or drain outgoing messages.

5\. The dependency packaging is incomplete

The Gradle build declares several Slack Bolt dependencies using `implementation`, but doesn't configure a reliable mechanism to bundle their runtime dependencies into the production mod JAR.

This matches the author's explicit warning that production deployment doesn't work.

6\. Credentials are stored in plaintext configuration

The bot token and app-level token are persisted in `config/slackbridge.json`. Environment variables or a secret file would be preferable, particularly for your Docker deployment.

There is another subtle problem: `/slackbridge-info` gathers player information and server performance measurements in the Slack callback rather than on the Minecraft thread.

None of these problems is particularly difficult to fix, but they reinforce the case for a new implementation.

## What I'd retain from SlackBridge

The most valuable part is `SlackApp.java`, especially its use of Slack's Java SDK:

```
App app = new App(
    AppConfig.builder()
        .singleTeamBotToken(botToken)
        .build()
);

SocketModeApp socketModeApp =
    new SocketModeApp(
        appToken,
        SocketModeClient.Backend.JavaWebSocket,
        app
    );

socketModeApp.startAsync();
```

This is a useful foundation for the Slack connection. However, I'd separate the Slack implementation from the Minecraft-specific code and avoid copying the old dependency versions unchanged.

## Proposed architecture

Slack Workspace

Channel messages · Slash commands · Bot API

Socket Mode / HTTPS

Slack integration layer

SlackClient

IncomingMessageHandler

OutgoingMessageQueue

MessageFormatter

Nonblocking event queue

NeoForge 21.1.256 mod

Chat event listeners

Server lifecycle

Player events

Server-thread dispatcher

Minecraft 1.21.1 Dedicated Server

No client-side mod required

This allows us to retain Slack Bolt's functionality while isolating networking from the Minecraft event system.

For configuration, I would use NeoForge's standard configuration mechanism for ordinary options and Docker environment variables for Slack credentials.

## Development sequence

1. Milestone 1: Create the NeoForge mod skeleton, compile it, and confirm it loads on your server.
2. Milestone 2: Capture player chat and send messages to Slack asynchronously.
3. Milestone 3: Establish Socket Mode and display Slack messages in Minecraft, with channel filtering and thread-safe dispatch.
4. Milestone 4: Add join, leave, death, advancement, and server lifecycle events.
5. Milestone 5: Add reconnect handling, queues, configuration validation, and logging.
6. Milestone 6: Build a distributable JAR and test it in your actual NeoForge Docker server, including restarting Slack and Minecraft independently.

I would treat the production JAR build as an early acceptance test, not something to leave until the end. That was the original project's primary unresolved problem.

One architectural choice remains: whether to embed the full Slack Java SDK inside the NeoForge mod or use a separate Slack bridge service alongside Minecraft in Docker.

Given that the Minecraft server is already experiencing performance issues, my preference is still a self-contained mod with asynchronous networking and strict queues, provided we can package the SDK cleanly. It avoids introducing another service while keeping all network activity off the server thread.

The repository is licensed under MIT, so selective code reuse is straightforward with appropriate preservation of its copyright and license notices.