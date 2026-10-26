# LigerBlocks Chat Bridge — Development Plan

## 1. Purpose

Deliver a server-side NeoForge mod that relays Minecraft server chat to a
configured Slack channel and relays messages from that channel back to online
players. The implementation will keep network work away from Minecraft's
server thread, respect Minecraft thread ownership, manage both systems'
lifecycles, and produce a distributable JAR that can be installed in the
existing NeoForge Docker server.

This plan follows the six-milestone sequence in [design-notes.md](./design-notes.md).
Milestone 1's basic module scaffold is already present; its acceptance criteria
remain a release gate and are included below for completeness.

## 2. Scope and outcomes

### In scope

- Minecraft 1.21.1 and NeoForge 21.1.256, built with Java 21.
- Dedicated-server operation; Minecraft clients do not need this mod.
- Minecraft chat to Slack and Slack channel messages to Minecraft.
- Slack Socket Mode, using the Slack Java SDK if the packaging spike passes.
- Server lifecycle, player join/leave, death, and advancement notifications.
- Safe handoff between Slack callbacks, background workers, and the Minecraft
  server thread.
- Validated configuration, secret handling suitable for Docker, logging,
  reconnect behavior, and bounded asynchronous message handling.
- A production JAR and an installation/restart test in the target Docker
  environment.

### Out of scope unless separately approved

- A separate Slack bridge service.
- Client-side mod features or client-only UI.
- New Minecraft gameplay features or custom commands such as `/shrug`.
- Support for Minecraft/NeoForge versions other than the versions listed above.
- Broad migration of legacy code. Reuse is selective and conditional on
  reviewing the source and license of any code copied.

## 3. Architecture and implementation constraints

1. **Minecraft integration:** A NeoForge entry point, event subscribers, and
   server lifecycle handlers live in the mod. Only the server-side runtime is
   needed.
2. **Slack integration:** A separately encapsulated Slack client owns the
   Socket Mode connection and Slack API calls. Minecraft event handlers must
   not make blocking network calls.
3. **Inbound dispatch:** Slack event callbacks validate message type and
   configured channel before enqueueing an event for dispatch. All interaction
   with Minecraft server/player state is scheduled onto the server thread via
   the server's task dispatcher.
4. **Outbound dispatch:** Minecraft events create immutable message data and
   submit it to a background sender/queue. The queue has a finite capacity and
   an explicit, logged overload policy; Slack rate-limit retry information is
   respected.
5. **Lifecycle ownership:** Slack connection, workers, queues, and callbacks
   are started only when a server is available and are stopped as part of
   server shutdown. Restarting the server must not leave duplicate clients or
   workers.
6. **Configuration:** NeoForge configuration holds non-secret options such as
   channel ID and feature toggles. Slack bot and app-level tokens are supplied
   through Docker environment variables or a mounted secret file. Tokens are
   never written to normal config, logs, crash reports, or test fixtures.
7. **Packaging:** Slack SDK runtime dependencies must be present and loadable
   in the production mod JAR without conflicting with NeoForge or other mods.
   A deployable JAR build is an early gate, not a final-only activity.
8. **Source and licensing:** Inspect the original SlackBridge source before
   reusing any implementation. Preserve required license/copyright notices and
   verify the mod's declared license is consistent with included code and
   dependencies. The design notes report MIT licensing; verify the upstream
   license file before copying code.

## 4. Milestones

### Milestone 1 — NeoForge module and early packaging gate (**COMPLETE**)

**Status:** Complete for the repository's local NeoForge dedicated-server
environment. The acceptance checks below pass with Java 21, and the mod reaches
the normal dedicated-server ready state.

**Work**

- Keep the Java 21 / Minecraft 1.21.1 / NeoForge 21.1.256 project scaffold,
  metadata, wrapper, and server run configuration.
- Confirm the mod is discovered by NeoForge on a dedicated server.
- Record a reproducible build and installation process.
- Before adding Slack libraries, prototype their inclusion in a production JAR
  and test dependency loading in a clean server instance.

**Acceptance criteria**

- `./gradlew clean build` succeeds with the repository's Gradle wrapper and
  Java 21.
- The built JAR contains the mod entry point and expanded `META-INF/neoforge.mods.toml`.
- A clean NeoForge 21.1.256 dedicated server starts with the mod installed,
  reports it as loaded, and reaches the normal ready state without a client mod.
- The test confirms the expected Minecraft version and mod ID; an incompatible
  Minecraft/NeoForge version is rejected by metadata.
- The packaging spike produces a documented decision: embed/shade or otherwise
  package the Slack SDK, or stop and request approval for a separate service.

**Exit gate:** No Slack feature work starts until production packaging feasibility
is known and the basic server load is verified.

**Milestone 1 verification record (2026-10-26):**

- `./gradlew clean build` passes with the included Gradle wrapper and Java 21.
- `build/libs/ligerblocks_chat_bridge-0.1.0.jar` contains
  `LigerBlocksChatBridge.class` and expanded `META-INF/neoforge.mods.toml`.
- `./gradlew runServer` loads `ligerblocks_chat_bridge` and reaches the normal
  `Done (...)! For help, type "help"` state without a client mod.
- The production packaging decision is NeoForge Jar-in-Jar packaging for the
  future Slack SDK. No Slack SDK is included in Milestone 1, so the current
  production JAR has no external runtime dependency to resolve. When the SDK is
  added, its embedded dependency graph must pass the same clean-server smoke
  test before Slack feature work proceeds.

### Milestone 2 — Minecraft chat to Slack (**COMPLETE**)

**Status:** Complete for the outbound relay path. The implementation uses a
bounded asynchronous sender and Java's standard HTTP client, with secrets
loaded only from environment variables or a mounted secret file.

**Work**

- Add ordinary (non-secret) NeoForge config for relay channel, enablement, and
  formatting options.
- Read Slack credentials from the agreed environment variables or mounted
  secret path, with actionable startup errors when missing or invalid.
- Capture player chat on the Minecraft event path, convert it to immutable
  relay data, and enqueue it without blocking the server thread.
- Implement the asynchronous Slack sender and format messages with player
  identity and message text.
- Decide and implement handling for queue saturation and Slack API failures;
  never silently report failed delivery as success.

**Acceptance criteria**

- A player chat message in an enabled test world appears in the configured Slack
  channel with the expected name and text.
- A deliberately slow/unavailable Slack endpoint does not block or stall the
  Minecraft server thread.
- Slack API errors and rate limits are visible in logs; rate limits honor the
  supplied retry delay.
- A full outbound queue follows its documented finite-capacity policy and emits
  an observable diagnostic.
- Tokens are absent from generated config, application logs, and test output.
- Disabled relay configuration produces no Slack connection or outbound
  messages.

**Milestone 2 implementation record (2026-10-26):**

- `BridgeConfig` registers ordinary NeoForge common configuration for relay
  enablement, channel ID, message format, and bounded queue capacity.
- `SecretProvider` reads `LIGERBLOCKS_SLACK_BOT_TOKEN` or
  `LIGERBLOCKS_SLACK_BOT_TOKEN_FILE`; token values are never logged or written
  to config.
- `ServerChatEvent` creates immutable `OutboundMessage` data and enqueues it;
  Slack HTTP work runs on the `ligerblocks-slack-sender` worker.
- Queue saturation rejects messages with an observable warning. Delivery
  failures are logged without message contents, and HTTP 429 `Retry-After`
  delays are honored for one retry, capped at 60 seconds.
- `ServerStoppingEvent` stops the worker and clears pending messages.
- Focused tests cover formatting, token-shape validation, asynchronous
  delivery, stopped queues, queue capacity rejection, and retry-delay data.
- `./gradlew --no-daemon test` passes with 6 tests.

### Milestone 3 — Slack to Minecraft, channel filtering, and thread safety

**Status:** Implementation and focused tests are complete. Final acceptance is
pending a successful live Socket Mode message test against a packaged server;
the ModDev development run currently reports a missing transitive Slack SDK
class after startup, so this milestone is not yet marked complete.

**Work**

- Establish the Socket Mode connection only after a running server is available.
- Register Slack message handling and filter by exact configured channel ID;
  ignore bot messages, edits/subtypes, and unsupported event forms according to
  documented rules.
- Format accepted Slack messages for Minecraft, including a clear Slack
  identity prefix.
- Dispatch accepted messages onto the Minecraft server thread before accessing
  player manager or broadcasting.
- Add tests for event filtering, formatter behavior, and dispatch decisions.

**Implementation record (2026-10-26):**

- `SlackSocketModeClient` owns the Bolt Socket Mode lifecycle and registers
  message events.
- `SlackMessageFilter` accepts only ordinary messages from the exact configured
  channel and rejects blank, bot-originated, and subtype messages.
- `SlackInboundDispatcher` formats accepted messages and schedules broadcasting
  through the Minecraft server executor.
- Focused tests cover channel filtering and server-thread dispatch.
- The production build and test suite pass. A live development-server run
  reaches the normal `Done (...)!` state, but the SDK classloader still reports
  `com/slack/api/util/json/JavaTimeInstantFactory` when constructing the Socket
  Mode client. The server-side startup path now reports that failure without
  crashing Minecraft.

**Acceptance criteria**

- A normal message in the configured Slack channel is broadcast to all online
  Minecraft players in the expected format.
- A message from any other channel is never broadcast, even when visible to the
  Slack bot.
- Bot-originated messages and unsupported message subtypes do not create
  relay loops or unexpected broadcasts.
- Minecraft server/player state is only read or changed on the server thread;
  tests or instrumentation verify dispatch from a non-server Slack callback.
- Slack connection failures are reported explicitly; no false "connected"
  status is logged for an unsuccessful connection.

### Milestone 4 — Player and server events

**Work**

- Add join, leave, death, advancement, server-start, and server-stop
  notifications, controlled by config where practical.
- Route notifications through the same outbound queue and formatter path as
  player chat.
- If implementing an information/status Slack command from legacy behavior,
  collect Minecraft state on the server thread and return a snapshot to the
  Slack callback rather than accessing server state there.

**Acceptance criteria**

- Each enabled event emits one correctly formatted Slack message for the
  corresponding Minecraft event; disabled event types emit none.
- No event causes duplicate notifications during one server lifecycle.
- Any command/status response that uses online player or performance data gets
  its snapshot from the server thread.
- Server startup/shutdown and player events remain functional when Slack is
  temporarily unavailable; failures are surfaced without crashing Minecraft.

### Milestone 5 — Reliability, configuration, and operational visibility

**Work**

- Implement bounded queue behavior, connection recovery, retry/backoff, and
  shutdown draining/cancellation with clear ownership of worker resources.
- Validate channel IDs, required credentials, formatting/config values, and
  incompatible or incomplete configuration at startup.
- Log connection state transitions, retries, rejected messages, queue
  saturation, unexpected status codes, and relevant SDK diagnostics without
  exposing secrets or message contents unnecessarily.
- Test independent Slack and Minecraft outages/restarts, including repeated
  start/stop cycles.

**Acceptance criteria**

- Slack disconnection does not stop or crash the Minecraft server; the mod
  retries according to a bounded backoff policy and logs state transitions.
- A Slack restart restores relay without requiring a Minecraft restart, where
  the SDK supports recovery; otherwise the limitation and operator recovery
  step are documented.
- Minecraft shutdown stops the Slack connection and all owned workers within a
  defined timeout, and no worker or duplicate connection remains after restart.
- Queue size remains bounded during prolonged downstream failure, and the
  configured overload behavior is observable.
- Invalid configuration prevents misleading "ready" state and provides an
  actionable diagnostic. Secrets do not appear in diagnostics.
- SDK diagnostic details are captured for unexpected statuses or slow calls
  without logging credentials.

### Milestone 6 — Release build and Docker acceptance

**Work**

- Build and inspect the production JAR in a clean environment.
- Install it in the target NeoForge Docker server with secrets injected using
  the agreed environment/secret mechanism.
- Exercise both relay directions, filtering, events, failure recovery, and
  independent restarts.
- Document installation, configuration, permissions/scopes, troubleshooting,
  and upgrade/rollback steps.

**Acceptance criteria**

- `./gradlew clean build` succeeds from a clean checkout on Java 21.
- The JAR loads in the actual target NeoForge Docker server without missing
  Slack SDK classes or dependency conflicts.
- All in-scope relay and event scenarios pass end-to-end with no client mod.
- Slack and Minecraft can be restarted independently; the remaining service
  continues running and relay recovers according to the documented policy.
- Configuration and credentials survive container recreation as intended, with
  tokens provided through secrets and not persisted in the ordinary config
  file.
- The release artifact, checksums/version, tested environment, known
  limitations, and operator instructions are recorded.

## 5. Cross-cutting verification strategy

- **Build gate:** Run `./gradlew clean build` at each milestone that changes
  Java, metadata, dependencies, or packaging.
- **Unit tests:** Cover configuration validation, message formatting, inbound
  channel/subtype filtering, retry/backoff decisions, and queue overflow
  behavior.
- **Integration tests:** Exercise Slack client behavior behind a controllable
  test seam or local test endpoint where feasible; do not require production
  tokens in automated tests.
- **Threading tests:** Demonstrate that callbacks from Slack do not access
  Minecraft server state except through the server-thread dispatcher.
- **Dedicated-server smoke test:** Start a clean NeoForge server with the built
  JAR; verify load, ready state, and orderly shutdown.
- **End-to-end Docker test:** Perform the Milestone 6 scenarios in the actual
  deployment topology, including independent service restarts.
- **Regression rule:** Every defect discovered in a milestone gets a focused
  regression test where practical. No test or diagnostic output may reveal
  tokens.

## 6. Dependencies and sequencing

1. Milestone 1 is prerequisite to all integration work.
2. Milestone 2 and Milestone 3 both depend on the packaging decision and
   configuration/credential approach from Milestone 1. They may be developed
   in parallel only after shared interfaces, formatting ownership, and lifecycle
   ownership are agreed.
3. Milestone 4 depends on the outbound queue and server-thread dispatch
   patterns from Milestones 2 and 3.
4. Milestone 5 hardens the integrated behavior from Milestones 2–4.
5. Milestone 6 is the final release gate and depends on all prior milestones.

## 7. Decisions to record before implementation

- **SDK packaging strategy:** Prefer the self-contained mod described in the
  design notes, conditional on a successful production-JAR and clean-server
  test. If the Slack SDK introduces unresolvable class conflicts or packaging
  instability, pause and request a decision on a separate bridge service.
- **Queue capacity and overload policy:** Select capacity based on expected
  server traffic and Slack limits. Document whether overload drops, coalesces,
  or rejects messages; whichever policy is chosen must be bounded and logged.
- **Retry limits and shutdown drain timeout:** Set explicit finite values and
  test behavior under repeated outage and shutdown.
- **Slack event and command scope:** Confirm which legacy slash commands, if
  any, are required. Preserve an information/status command only if it is part
  of the desired product behavior; custom Minecraft commands remain optional.
- **License and source reuse:** Verify the upstream license and attribution
  obligations before porting code, and align the new mod's license metadata
  with the included implementation and dependencies.

## 8. Overall definition of done

The project is ready for release when the Milestone 6 acceptance criteria pass,
the production JAR runs on the target dedicated server, both relay directions
work with correct filtering and thread ownership, lifecycle/recovery behavior is
documented and verified, credentials remain outside ordinary config/logs, and
the artifact can be installed and operated using the documented Docker
procedure.
