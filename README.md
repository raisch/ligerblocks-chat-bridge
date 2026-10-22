# LigerBlocks Chat Bridge

A server-side NeoForge mod for bridging Minecraft chat and Slack. This repository
starts with the Milestone 1 mod skeleton; Slack integration and gameplay events
will be added in later milestones.

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
./gradlew build
```

The JAR is written to `build/libs/`.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.256
- Java 21
