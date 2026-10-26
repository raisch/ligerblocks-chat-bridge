# LigerBlocks Chat Bridge

A server-side NeoForge mod for bridging Minecraft chat and Slack. This repository
contains the completed Milestone 1 NeoForge module skeleton. Slack integration
and gameplay events will be added in later milestones.

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

## Slack SDK packaging decision

Milestone 1 does not add Slack SDK classes or network behavior. The production
JAR is currently self-contained because the module has no external runtime
dependencies, and its clean dedicated-server smoke test passes.

When Slack dependencies are introduced, the selected strategy is NeoForge
Jar-in-Jar packaging: declare the SDK as a runtime dependency in Gradle, embed
its runtime dependencies in the production mod JAR, and repeat `clean build`,
JAR inspection, and the clean dedicated-server smoke test before starting
Slack feature work. A separate bridge service is not required unless that
packaging test exposes an unresolvable dependency conflict.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.256
- Java 21
