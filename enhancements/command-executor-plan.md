# Implementation Plan: Slack `/mc` Minecraft Command Executor

**Date**: 2026-10-27  
**Spec**: [command-executor.md](./command-executor.md)  
**Mapping storage**: Option A — mappings stored and authorized by the Minecraft
mod configuration.

## Summary

Add a configuration-driven `/mc` Slack slash command handled through the
existing Slack Socket Mode connection. The Slack side remains a generic
transport: it validates the request envelope and forwards the subcommand to
the Minecraft mod. The mod loads an allowlisted command registry from the
server configuration, validates typed arguments, authorizes the Slack user,
executes the resulting Brigadier command on the Minecraft server thread, and
returns captured command output through the existing outbound Slack queue.

Adding or changing mappings, aliases, permissions, and argument definitions
will not require recompiling the mod. A reload operation will atomically
replace the active registry only after the new configuration validates.

## Requirements

- **REQ-001**: Receive `/mc <subcommand> [arguments]` through the existing Slack
  Socket Mode connection; do not add a second WebSocket or HTTP bridge.
- **REQ-002**: Store command mappings in Minecraft-side configuration using
  Option A, including the Minecraft command template and authorization policy.
- **REQ-003**: Support permission policies for everyone and configured Slack
  users/roles, with default-deny behavior for unmapped or unauthorized
  commands.
- **REQ-004**: Validate arguments before template expansion, including required
  typed arguments such as Minecraft usernames; reject unsafe or malformed
  values without executing a command.
- **REQ-005**: Resolve and execute commands through Minecraft's Brigadier
  dispatcher on the Minecraft server thread.
- **REQ-006**: Capture command success, failure, and feedback output and return
  a bounded, readable response to the Slack requester.
- **REQ-007**: Support atomic configuration reload without losing the previous
  valid registry when the new configuration is invalid.
- **REQ-008**: Support aliases and optional multi-command macros, with every
  expanded command independently checked against the execution policy.
- **REQ-009**: Document Slack app setup and repeatable Slack-side testing using
  the existing Socket Mode connection.
- **REQ-010**: Add unit, threading, integration, and security regression tests
  without logging tokens or allowing arbitrary console execution.

## Technical Context

**Language/Version**: Java 21  
**Platform**: NeoForge 21.1.256, Minecraft 1.21.1  
**Slack transport**: Existing Bolt `SocketModeApp` and Socket Mode client  
**Minecraft command execution**: Brigadier command dispatcher on the server
thread  
**Mapping storage**: NeoForge common configuration, with a dedicated
`slack-commands.json` file if the nested command structure is too restrictive
for `ModConfigSpec`  
**Response transport**: Existing `OutboundMessageQueue` and Slack
`chat.postMessage` client  
**Persistence**: Server-side configuration volume in Docker; no tokens in the
mapping file  
**Constraints**:

- Never execute an arbitrary Slack-provided command string.
- Never execute Slack commands with implicit operator or console privileges.
- Use immutable Slack user IDs for authorization, not display names.
- Read Minecraft state and execute Brigadier commands only on the server thread.
- Keep command output and expanded macro output bounded.
- Preserve the current Socket Mode connection and channel filtering behavior.

## Constitution Check

- **Server-thread ownership**: Pass. Slack callbacks schedule all Minecraft
  work through `MinecraftServer.execute`.
- **Least privilege**: Pass. Configuration allowlists command roots and Slack
  identities; authorization defaults to deny.
- **Bounded resources**: Pass. Argument count, expanded command count, output
  length, response size, and execution time will have explicit limits.
- **Credential safety**: Pass. Tokens remain environment/Docker-secret values
  and never enter command mappings, responses, or logs.
- **Observable failures**: Pass. Unknown commands, denied users, validation
  failures, command failures, and reload failures produce actionable logs and
  user-safe Slack responses.
- **Backward compatibility**: Pass. Existing chat, lifecycle events, and
  Socket Mode message filtering remain unchanged.

## Applied Guidelines

- Reuse the existing Socket Mode lifecycle and reconnect handling in
  `SlackSocketModeClient`.
- Reuse server-thread dispatch semantics from `SlackInboundDispatcher`.
- Reuse `OutboundMessageQueue` for asynchronous Slack responses.
- Use explicit typed validation before string substitution; do not introduce
  shell execution or unrestricted scripting.
- Keep configuration reload atomic: parse and validate a complete replacement
  registry before publishing it.

## Implementation Steps

### Phase 1 — Foundational command model and configuration ✅ Complete

#### Plan 1.1: Define command registry and mapping schema

**Requirements**: REQ-002, REQ-003, REQ-004, REQ-008

Define a schema for command mappings:

```json
{
  "commands": {
    "players": {
      "minecraft": "list",
      "permission": "everyone"
    },
    "addplayer": {
      "minecraft": "whitelist add {player}",
      "permission": "admin",
      "arguments": {
        "player": {
          "type": "minecraft_username",
          "required": true
        }
      }
    }
  }
}
```

The schema must support a single command or a command list for macros,
aliases, permission policy, typed arguments, and bounded execution settings.
Use immutable records for parsed mappings and an immutable registry snapshot.

**Tasks**

- [x] T001 [Plan:1.1] Define immutable command mapping, argument definition,
  permission policy, alias, and registry types under
  `src/main/java/com/ligerbots/ligerblockschatbridge/commands/`.
- [x] T002 [P] [Plan:1.1] Define the Option A configuration schema and default
  example in `config/ligerblocks_chat_bridge-slack-commands.json` or the
  selected NeoForge config representation.
- [x] T003 [P] [Plan:1.1] Add parser and validator tests for valid mappings,
  unknown permission values, missing templates, duplicate names, invalid
  placeholders, and excessive macro/output limits in
  `src/test/java/com/ligerbots/ligerblockschatbridge/commands/`.

#### Plan 1.2: Add atomic registry lifecycle

**Requirements**: REQ-002, REQ-007, REQ-010

Load the command file during server startup, retain the last valid registry,
and publish replacements through an atomic reference. Invalid reloads must be
rejected without changing active behavior.

**Tasks**

- [x] T004 [Plan:1.2] Implement command registry loading, validation, and
  atomic replacement in `CommandRegistryManager.java`.
- [x] T005 [P] [Plan:1.2] Add a reload service and actionable reload failure
  logging without including credentials or sensitive command arguments.
- [x] T006 [P] [Plan:1.2] Test startup load, valid replacement, invalid
  replacement retention, duplicate detection, and repeated reloads.

### Phase 2 — Slack Socket Mode command intake ✅ Complete

#### Plan 2.1: Register `/mc` on the existing Socket Mode app

**Requirements**: REQ-001, REQ-003, REQ-009

Register a Bolt `/mc` command handler on the existing `App` in
`SlackSocketModeClient`. Do not create a second Slack client or HTTP listener.
Acknowledge the command immediately, validate the configured channel, preserve
the Slack user ID, and pass a command request to the server-thread dispatcher.

**Tasks**

- [x] T007 [Plan:2.1] Register the `/mc` Bolt command handler in
  `src/main/java/com/ligerbots/ligerblockschatbridge/SlackSocketModeClient.java`
  using the existing `SocketModeApp`.
- [x] T008 [P] [Plan:2.1] Add a command request model containing team, channel,
  Slack user ID, subcommand text, response URL/context, and request timestamp.
- [x] T009 [P] [Plan:2.1] Add tests for Socket Mode command parsing, empty
  commands, wrong channels, unknown subcommands, and immediate acknowledgement.

#### Plan 2.2: Resolve authorization

**Requirements**: REQ-003, REQ-010

Authorize by immutable Slack user ID and configured role/permission policy.
`everyone` means every accepted user in the configured channel; configured
user lists and roles must be explicit. Do not use display names for access
control.

**Tasks**

- [x] T010 [Plan:2.2] Implement command authorization against the active
  registry and Slack user ID.
- [x] T011 [P] [Plan:2.2] Add authorization tests covering everyone, explicit
  users, role membership, default deny, and channel rejection.

### Phase 3 — Minecraft execution and response ✅ Complete

#### Plan 3.1: Parse, validate, and expand arguments

**Requirements**: REQ-004, REQ-008, REQ-010

Parse the `/mc` subcommand and arguments without shell semantics. Validate
typed arguments before expansion, including Minecraft username syntax, bounded
argument length, and required/optional arguments. Expand only declared
placeholders.

**Tasks**

- [x] T012 [Plan:3.1] Implement command tokenization, typed argument
  validation, placeholder expansion, aliases, and macro expansion.
- [x] T013 [P] [Plan:3.1] Add tests for valid arguments, missing arguments,
  extra arguments, invalid usernames, quoting behavior, aliases, and macro
  limits.

#### Plan 3.2: Execute through Brigadier on the server thread

**Requirements**: REQ-005, REQ-006, REQ-008

Schedule execution with `MinecraftServer.execute`. Resolve the command against
the registered Brigadier dispatcher, use an explicitly configured permission
source, capture feedback/result codes, and execute each macro command under
the same policy.

**Tasks**

- [x] T014 [Plan:3.2] Implement server-thread command execution in
  `MinecraftCommandExecutor.java` using a controlled `CommandSourceStack`.
- [x] T015 [P] [Plan:3.2] Implement bounded command feedback capture and
  response formatting for success, failure, denied, timeout, and unavailable
  server states.
- [x] T016 [P] [Plan:3.2] Add threading and executor tests proving Slack
  callbacks never read or mutate Minecraft state directly.
- [x] T017 [Plan:3.2] Add tests for Brigadier unknown commands, insufficient
  permission, command failure, macro partial failure, and output truncation.

#### Plan 3.3: Return responses through the outbound relay

**Requirements**: REQ-006, REQ-010

Send command results through `OutboundMessageQueue`, preserving channel
configuration and bounded retry behavior. Prefer a private Slack response
when the command payload supports it; otherwise use the existing configured
channel with a clear requester prefix.

**Tasks**

- [x] T018 [Plan:3.3] Integrate command response delivery with
  `OutboundMessageQueue.java` and preserve bounded queue/retry behavior.
- [x] T019 [P] [Plan:3.3] Add response-size, queue-overflow, and delivery-failure
  tests without logging tokens.

### Phase 4 — Reload, operational controls, and documentation ✅ Complete

#### Plan 4.1: Add controlled reload behavior

**Requirements**: REQ-007, REQ-010

Support `/mc reload` or a native `/slackbridge reload` command restricted to
administrators. Parse and validate the new file off the request callback, then
atomically publish it on success. Never replace a valid registry with an
invalid or partial one.

**Tasks**

- [x] T020 [Plan:4.1] Implement the administrator-only reload command and
  server-thread-safe registry replacement.
- [x] T021 [P] [Plan:4.1] Add reload success/failure logging and tests for
  concurrent reads during replacement.

#### Plan 4.2: Document Slack app setup and end-to-end testing

**Requirements**: REQ-001, REQ-009, REQ-010

Update the operator documentation with exact Slack-side setup and test
instructions:

1. Open `https://api.slack.com/apps` and select the existing bridge app.
2. Under **Features → Slash Commands**, choose **Create New Command**.
3. Set the command to `/mc`.
4. Add a description such as `Run an allowlisted Minecraft command`.
5. Because the app already uses Socket Mode, do not add a Request URL. The
   command payload is delivered through the existing Socket Mode connection.
6. Save the command and reinstall/re-authorize the app if Slack requests it.
7. Ensure the bot is a member of the configured channel.
8. Start the Docker server and confirm:

   ```text
   Slack Socket Mode connected
   ```

9. Test configured mappings from the configured Slack channel:

   ```text
   /mc players
   /mc daytime
   /mc addplayer Steve
   ```

10. Confirm unknown, unauthorized, malformed, and wrong-channel requests are
    rejected without Minecraft command execution.
11. Edit the mapping file, run the chosen reload command, and verify a valid
    mapping takes effect without restarting Minecraft.
12. Introduce an invalid mapping, reload, and confirm the previous valid
    registry remains active.

**Tasks**

- [x] T022 [Plan:4.2] Document `/mc` Slack app creation, Socket Mode delivery,
  required permissions, mapping examples, reload procedure, and negative tests
  in `README.md`.
- [x] T023 [P] [Plan:4.2] Add a Docker end-to-end test checklist covering
  `/mc players`, `/mc daytime`, typed arguments, denied users, reload, server
  restart, Slack disconnect/reconnect, and token safety.

### Phase 5 — Release and acceptance

#### Plan 5.1: Complete release validation

**Requirements**: REQ-001 through REQ-010

Run unit tests, clean production build, Docker smoke tests, Slack-side
acceptance, and independent restart/recovery checks. Record known limitations,
especially Minecraft command permission behavior and Slack response timing.

**Tasks**

- [ ] T024 [Plan:5.1] Run `./gradlew clean build`, inspect the production JAR,
  and verify no secrets or duplicate host-provided packages are embedded.
- [ ] T025 [P] [Plan:5.1] Run the Docker deployment with secret files and
  verify persistent configuration and rollback behavior.
- [ ] T026 [Plan:5.1] Execute the Slack-side `/mc` test matrix and record
  request, execution, response, denial, reload, and recovery evidence.
- [ ] T027 [Plan:5.1] Update `development-plan.md` with the implementation,
  test evidence, known limitations, and acceptance decision.

## Project Structure

```text
enhancements/
  command-executor.md
  command-executor-plan.md
src/main/java/com/ligerbots/ligerblockschatbridge/
  SlackSocketModeClient.java
  SlackInboundDispatcher.java
  OutboundMessageQueue.java
  commands/
    CommandMapping.java
    CommandRegistry.java
    CommandRegistryManager.java
    CommandAuthorization.java
    MinecraftCommandExecutor.java
src/test/java/com/ligerbots/ligerblockschatbridge/commands/
  CommandMappingTest.java
  CommandRegistryManagerTest.java
  MinecraftCommandExecutorTest.java
```

## Testing Strategy

- **Application type**: Minecraft dedicated server with Slack Socket Mode
  messaging integration.
- **Unit tier**: JUnit tests for schema validation, authorization, argument
  expansion, reload atomicity, response bounds, and retry behavior.
- **Threading tier**: Executor-backed tests proving all Minecraft access occurs
  on the server executor.
- **Docker tier**: `docker compose` with persistent `minecraft-data` volume and
  Docker secret files.
- **Slack tier**: Existing Slack app, `/mc` configured as a native slash
  command, delivered over the existing Socket Mode connection.
- **Critical journeys**:
  1. Authorized `/mc players` returns Minecraft output.
  2. Typed `/mc addplayer Steve` validates and executes only when authorized.
  3. Unauthorized and malformed commands are rejected.
  4. Valid mapping reload takes effect; invalid reload preserves old behavior.
  5. Slack disconnect/reconnect does not crash Minecraft or lose command
     registry state.
- **Acceptance**: No arbitrary command execution, no server-thread violations,
  no token exposure, bounded responses, and successful Docker/Slack recovery.

## Requirement Mapping

| REQ ID | Description | Plan Items | Implementation Evidence |
|---|---|---|---|
| REQ-001 | Receive `/mc` through existing Socket Mode | 2.1, 4.2 | `SlackSocketModeClient.java`, README setup/test instructions |
| REQ-002 | Option A Minecraft-side mappings | 1.1, 1.2 | Command registry/config files |
| REQ-003 | Everyone/user/role authorization | 1.1, 2.2 | `CommandAuthorization.java` and authorization tests |
| REQ-004 | Typed safe argument validation | 1.1, 3.1 | Argument definitions and expansion tests |
| REQ-005 | Server-thread Brigadier execution | 3.2 | `MinecraftCommandExecutor.java` and threading tests |
| REQ-006 | Bounded output and Slack responses | 3.2, 3.3 | Response formatter and outbound queue tests |
| REQ-007 | Atomic hot reload | 1.2, 4.1 | `CommandRegistryManager.java` and reload tests |
| REQ-008 | Aliases and safe macros | 1.1, 3.1, 3.2 | Mapping schema, expansion, and macro tests |
| REQ-009 | Slack setup and test instructions | 2.1, 4.2 | README and Docker test checklist |
| REQ-010 | Security, threading, and regression tests | All phases | JUnit, Docker, and Slack acceptance evidence |
