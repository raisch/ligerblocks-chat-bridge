# Slack Minecraft Commands

The bridge exposes one native Slack slash command:

```text
/mc <command> [arguments...]
```

Commands are allowlisted in the Minecraft server configuration file:

```text
config/ligerblocks_chat_bridge-slack-commands.json
```

The bridge parses the Slack text without invoking a shell, validates the
arguments, expands them into configured Minecraft command templates, and
executes only the resulting allowlisted commands.

The configuration loader accepts `//` single-line comments and `/* ... */`
multiline comments. Comment markers inside quoted JSON strings are preserved.
For example:

```json
{
  // Administrators may reload this file
  "admins": ["SLACK_USER_ID"],
  "commands": {
    "players": {
      "minecraft": "list",
      "permission": "everyone",
      "description": "Show URLs such as https://example.test/a/*b*/ literally"
    }
  }
}
```

Command results are sent as private ephemeral responses to the Slack user who
invoked `/mc`. Minecraft chat, lifecycle events, and ordinary relay messages
continue to use the configured shared channel.

To intentionally publish a command result to the configured Slack channel, set
the command's `response` property to `channel`:

```json
{
  "announce": {
    "minecraft": "say {message}",
    "permission": "admin",
    "response": "channel",
    "arguments": {
      "message": {
        "type": "rest",
        "required": true
      }
    }
  }
}
```

Invoke it with:

```text
/mc announce Server restart in five minutes
```

The result is posted publicly with the existing `[Slack] <user>` prefix.
Commands without `response` remain private.

## Configuration structure

The root object may contain an administrator list and must contain a `commands`
object:

```json
{
  "admins": ["SLACK_USERID"],
  "commands": {
    "players": {
      "minecraft": "list",
      "permission": "everyone",
      "description": "Show online players"
    }
  }
}
```

### Top-level properties

| Property | Required | Description |
|---|---:|---|
| `admins` | No | Slack user IDs that receive administrator access. |
| `commands` | Yes | Object containing one or more command mappings. |

### Command properties

| Property | Required | Description |
|---|---:|---|
| `minecraft` | Yes | One Minecraft command string or an array of command strings. Do not include `/`. |
| `permission` | Yes | `everyone`, `moderator`, or `admin`. |
| `description` | No | Text shown by `/mc help`. |
| `response` | No | `private` (default) or `channel`. Controls who sees command results. |
| `aliases` | No | Additional command names. |
| `users` | No | Legacy per-command Slack user IDs. |
| `roles` | No | Slack role IDs used by the authorizer. |
| `arguments` | No | Ordered argument definitions. |

Multiple `minecraft` entries form a macro and execute in order:

```json
{
  "minecraft": [
    "weather clear",
    "time set day"
  ],
  "permission": "admin"
}
```

## Command forms

### Command with no arguments

```json
{
  "players": {
    "minecraft": "list",
    "permission": "everyone",
    "description": "Show online players"
  }
}
```

Invocation:

```text
/mc players
```

### Whitelist subcommands

The whitelist mapping accepts `list`, `add`, or `del`, with an optional
Minecraft username:

```json
{
  "whitelist": {
    "minecraft": "whitelist {action} {player}",
    "permission": "admin",
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
```

Invocation:

```text
/mc whitelist list
/mc whitelist add Steve
/mc whitelist del Steve
```

Minecraft usernames must match `[A-Za-z0-9_]{3,16}`.
The `requiredWhen` rule requires `player` for `add` and `del` while rejecting
it for `list`.

### Optional single argument

```json
{
  "greet": {
    "minecraft": "say Hello {name}",
    "permission": "everyone",
    "arguments": {
      "name": {
        "type": "word",
        "required": false
      }
    }
  }
}
```

The argument may be omitted:

```text
/mc greet
/mc greet Alex
```

When an omitted optional argument is referenced in a template, it expands to
an empty string.

### Rest argument

`rest` consumes all remaining tokens as one value. Token spacing is normalized
to single spaces:

```json
{
  "announce": {
    "minecraft": "say {message}",
    "permission": "admin",
    "arguments": {
      "message": {
        "type": "rest",
        "required": true
      }
    }
  }
}
```

Invocation:

```text
/mc announce Server restart in five minutes
```

The resulting Minecraft command is:

```text
say Server restart in five minutes
```

### Required one-or-more arguments

`one_or_more` consumes one or more remaining tokens and joins them into one
value:

```json
{
  "team": {
    "minecraft": "say Team members: {players}",
    "permission": "admin",
    "arguments": {
      "players": {
        "type": "one_or_more",
        "required": true
      }
    }
  }
}
```

Valid:

```text
/mc team Alex
/mc team Alex Steve Jordan
```

Invalid:

```text
/mc team
```

### Optional zero-or-more arguments

`optional_one_or_more` consumes zero or more remaining tokens:

```json
{
  "labels": {
    "minecraft": "say Labels: {labels}",
    "permission": "admin",
    "arguments": {
      "labels": {
        "type": "optional_one_or_more",
        "required": false
      }
    }
  }
}
```

Both forms are valid:

```text
/mc labels
/mc labels urgent staff-only
```

### Constrained option

An `option` argument accepts exactly one value from its `options` list. Matching
is case-insensitive:

```json
{
  "weather": {
    "minecraft": "weather {kind}",
    "permission": "admin",
    "arguments": {
      "kind": {
        "type": "option",
        "required": true,
        "options": ["clear", "rain", "thunder"]
      }
    }
  }
}
```

Valid:

```text
/mc weather clear
/mc weather RAIN
```

Invalid:

```text
/mc weather snow
```

### Required one-or-more followed by an option

The parser supports a required variadic argument followed by one final
`option`. The final token is reserved for the option:

```json
{
  "announce": {
    "minecraft": "say {message} ({style})",
    "permission": "admin",
    "arguments": {
      "message": {
        "type": "one_or_more",
        "required": true
      },
      "style": {
        "type": "option",
        "required": true,
        "options": ["plain", "bold"]
      }
    }
  }
}
```

Invocation:

```text
/mc announce Server restart soon bold
```

Expansion:

```text
say Server restart soon (bold)
```

`rest` and `optional_one_or_more` cannot be followed by another positional
argument. This avoids ambiguous input.

## Supported argument types

| Type | Behavior |
|---|---|
| `word` | One token matching `[A-Za-z0-9_.:-]+`. |
| `minecraft_username` | One Minecraft username matching `[A-Za-z0-9_]{3,16}`. |
| `option` | One value from the configured `options` list. |
| `rest` | All remaining tokens joined into one required or optional value. |
| `one_or_more` | At least one remaining token joined into one value. |
| `optional_one_or_more` | Zero or more remaining tokens joined into one value. |

Arguments are processed in the JSON declaration order. Variadic arguments
should normally be last. The only supported exception is `one_or_more`
followed by one final `option`.

Each command accepts at most 16 Slack argument tokens, and each expanded
argument is limited to 64 characters.

## Quoting and escaping

The parser supports single quotes, double quotes, and backslash escaping:

```text
/mc announce "Server restart soon" bold
/mc announce 'Server restart soon' bold
/mc announce Server\ restart\ soon bold
```

Quotes are removed during tokenization. Shell substitutions, pipes,
redirections, command chaining, and other shell syntax are not evaluated.

## Command names, aliases, and prefixes

Exact command names and aliases take precedence:

```json
{
  "night": {
    "minecraft": "time set night",
    "permission": "admin",
    "aliases": ["sleep"]
  }
}
```

```text
/mc night
/mc sleep
```

If there is exactly one matching command, a unique prefix is accepted:

```text
/mc ni
```

This resolves to `night` when no other command begins with `ni`. Ambiguous
prefixes are rejected:

```text
/mc n
```

If both `night` and `noon` exist, the response identifies the matching
commands. Authorization is checked against the resolved canonical command.

## Built-in commands

### `/mc help`

Lists commands visible to the requesting Slack user. It includes descriptions,
argument syntax, and aliases. The complete visible list, including built-in
commands, is sorted alphabetically. Admin-only commands are hidden from users
who cannot run them. For administrators, admin-only commands are shown in
bold; the first line of the response identifies this formatting.

### `/mc reload`

Reloads the external command JSON configuration:

```text
/mc reload
```

Only a user listed in the top-level `admins` array can reload the file. A
successful reload atomically replaces the active command registry. If the file
is invalid, the previous valid registry remains active.

The common TOML bridge configuration is not reloaded by `/mc reload`.

### `/mc config`

Administrators can request the command configuration privately:

```text
/mc config
```

The response contains the command configuration from
`config/ligerblocks_chat_bridge-slack-commands.json`, including comments if
present, but omits the top-level `admins` list. It is sent as a private
ephemeral response and is not posted to the shared channel.

### `/mc admin add`

Administrators can add a Slack user ID to the top-level `admins` list:

```text
/mc admin add U012345678
```

Optional flags can reload the registry immediately and include the resulting
administrator list:

```text
/mc admin add U012345678 -reload -list
```

The flags may be used independently. The command accepts Slack user IDs in the
`U...` or `W...` format, writes the configuration atomically, and returns a
private response to the invoking administrator.

List administrators:

```text
/mc admin list
```

Remove an administrator:

```text
/mc admin del U012345678
/mc admin del U012345678 -reload -list
```

The final administrator cannot be removed.

### `/mc events`

Administrators can change Minecraft event notifications while the server is
running:

```text
/mc events all on
/mc events all off
/mc events user off
/mc events server on
```

`user` controls player join, leave, death, and advancement notifications.
`server` controls server-start and server-stop notifications. `all` changes
both groups. The setting is runtime-only and does not change the TOML
configuration file; it will return to the configured values after a restart.

### `/mc msgs`

Administrators can change message relay directions while the server is
running:

```text
/mc msgs from on
/mc msgs from off
/mc msgs to on
/mc msgs to off
```

`from` controls Minecraft chat and event messages sent to Slack. `to` controls
ordinary Slack messages sent to Minecraft. The setting is runtime-only and
returns to the configured values after a restart.

## Command response delivery

The slash-command acknowledgement is returned immediately, for example:

```text
Minecraft command accepted.
```

Once the command has run on the Minecraft server thread, the result is posted
to Slack using the slash command's response URL with
`"response_type": "ephemeral"`. Only the invoking user can see that result.
This includes successful execution, invalid arguments discovered during
execution, Minecraft command failures, and reload results.

The response URL is used only for the command result and is not written to
logs. If a response URL is unavailable, the bridge drops the result and logs a
warning rather than posting a private command result to the shared channel.

## Test jig

The development configuration contains administrator-only mappings for manual
validation:

```text
/mc players
/mc test-rest hello from Slack
/mc test-required-many hello brave world
/mc test-optional-many
/mc test-optional-many one two
/mc test-option rain
/mc test-many-option hello brave bold
```

Copy the development configuration to the server and run `/mc reload` before
using these commands. Automated coverage is in
`src/test/java/com/ligerbots/ligerblockschatbridge/commands/CommandPatternJigTest.java`.

## Validation and safety

Invalid input is rejected before Minecraft execution. Typical failures include:

- Missing required arguments.
- Extra arguments.
- Invalid usernames or words.
- Options not present in the configured allowlist.
- Unknown command names.
- Ambiguous command prefixes.
- Unauthorized users or channels.

Minecraft commands are never taken directly from Slack input. They must first
be present as a configured `minecraft` template, and placeholders are expanded
only from validated argument values.
