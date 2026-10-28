Yes. You can make the mapping between Slack `/mc` subcommands and Minecraft commands entirely configuration-driven, so adding, modifying, or removing mappings requires no recompilation of the NeoForge mod.

In fact, you could accomplish this without modifying the mod for each new command, provided it already supports a generic command-execution request over WebSocket.

## Recommended architecture

Keep the command mappings in a configuration file on the Minecraft server, and implement a generic command dispatcher in the mod.

For example, a `slack-commands.json` file:

```
{
  "commands": {
    "players": {
      "minecraft": "list",
      "permission": "everyone"
    },
    "daytime": {
      "minecraft": "time query daytime",
      "permission": "everyone"
    },
    "whitelist": {
      "minecraft": "whitelist list",
      "permission": "admin"
    },
    "addplayer": {
      "minecraft": "whitelist add {0}",
      "permission": "admin"
    },
    "lag": {
      "minecraft": "spark health",
      "permission": "moderator"
    }
  }
}
```

This would produce the following mappings:

| Slack command         | Executed Minecraft command |
| --------------------- | -------------------------- |
| `/mc players`         | `/list`                    |
| `/mc daytime`         | `/time query daytime`      |
| `/mc whitelist`       | `/whitelist list`          |
| `/mc addplayer Steve` | `/whitelist add Steve`     |
| `/mc lag`             | `/spark health`            |

The mappings could include commands registered by other mods, not just vanilla Minecraft commands.

## Where should the mapping live?

There are two reasonable choices.

Option A: Minecraft mod configuration

The NeoForge mod loads the mappings and resolves them against Minecraft's Brigadier command dispatcher.

This keeps authorization and execution together and avoids giving the Slack bridge unrestricted command-execution authority.

Option B: Slack bridge configuration

The bridge translates `/mc players` into a WebSocket request to execute `list`.

This is convenient if you administer several Minecraft servers or want to change mappings centrally. The mod must still independently authorize each executable command.

I'd choose Option A, with the bridge remaining a generic transport.

## Hot-reloading command definitions

You can go a step further and avoid restarting Minecraft.

The mod could load the configuration at startup and support a reload command:

```
/mc reload
```

Or use a native Minecraft administrative command:

```
/slackbridge reload
```

The implementation would parse the new configuration, validate it, and atomically replace the active command registry. Invalid configurations should leave the previous registry intact.

## Handling arguments safely

I would avoid unrestricted string substitution.

For example, this mapping:

```
{
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
```

Would validate the player name before inserting it into the command.

For more complex commands, you could support typed arguments, defaults, and optional parameters. Brigadier would then validate the final command against Minecraft's registered command tree.

Crucially, the mod should execute only commands authorized by the active configuration, even if the Slack bridge sends an arbitrary command string. It should also execute on the Minecraft server thread and capture the command output for return through your existing messaging path.

## A useful extension: aliases and macros

The same configuration could define commands that execute several Minecraft commands in sequence:

```
{
  "morning": {
    "permission": "moderator",
    "minecraft": [
      "time set day",
      "weather clear",
      "say Good morning from Slack!"
    ]
  }
}
```

This would make `/mc morning` a simple administrative macro.

For security, each expanded command should be checked against an explicit execution policy; authorizing the alias alone should not silently grant arbitrary console access.

My recommendation: Implement a small, configuration-driven command executor in your NeoForge mod, with hot reload, typed arguments, permission levels, and support for capturing Brigadier command output. The Slack bridge only needs to forward requests and publish responses.

This gives you a flexible Slack administration interface without recompiling the mod whenever you add a command.