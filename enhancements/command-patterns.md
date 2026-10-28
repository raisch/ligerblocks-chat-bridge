# Command Patterns

/mc cmd                 - run cmd
/mc cmd rest            -     with one arg comprised of everything to the end of the line
/mc cmd arg+            -     with one or more required args
/mc cmd [arg+]          -     with one or more optional args
/mc cmd arg+ [arg+]     -     with one or more required args and one or more optional args
/mc cmd opts            -     with one opt from a group of allowed opts
/mc cmd arg+ opts       -     with one or more required args and one of a group of allowed opts

Argument definitions use the command JSON `arguments` object:

```json
{
  "arguments": {
    "message": { "type": "rest", "required": true },
    "scope": {
      "type": "option",
      "required": true,
      "options": ["all", "user", "server"]
    },
    "players": { "type": "one_or_more", "required": true },
    "tags": { "type": "optional_one_or_more", "required": false }
  }
}
```

`rest` joins all remaining tokens into one value. `one_or_more` requires at
least one remaining token, and `optional_one_or_more` accepts zero or more.
Variadic arguments are normally final because positional parsing cannot
otherwise determine where one variadic value ends and the next begins. The
`one_or_more` form may be followed by one final `option`; the last token is
reserved for that option.

The development configuration includes a test jig for these forms:

```text
/mc players
/mc test-rest hello from Slack
/mc test-required-many hello brave world
/mc test-optional-many
/mc test-optional-many one two
/mc test-option rain
/mc test-many-option hello brave bold
```

All jig commands are administrator-only and execute harmless `say`/`weather`
commands. Copy the updated development configuration to the server and run
`/mc reload` before testing them.


Arguments can be required or optional.
Opts can be multiple choice, like `all|user|server` or `on|off` 

Command names and aliases also support unique prefixes:

/mc ni                   - resolves to /mc night when it is the only match
/mc n                    - rejected when /mc night and /mc noon both match
/mc day                  - exact matches take precedence over /mc daytime

Ambiguous prefixes are rejected with the matching command names. Prefix
resolution happens before authorization, and the resolved canonical command's
permission is always enforced.