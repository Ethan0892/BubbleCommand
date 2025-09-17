# Installation and Usage Guide

## Installation

1. Download the latest release from the [Releases page](https://github.com/Ethan0892/BubbleCommand/releases)
2. Place the JAR file in your server's `plugins/` folder
3. Start or restart your server
4. The plugin will create a `config.yml` file in `plugins/BubbleCommand/`

## Configuration

Edit `plugins/BubbleCommand/config.yml` to add your custom commands:

```yaml
custom-commands:
  daily:
    aliases: ["dailyreward"]
    action: "panels open dailyreward %player%"
    permission: ""
    description: "Open daily reward panel"
    player-only: true
```

## Adding New Commands (NO REBUILD REQUIRED!)

1. Add the command to `plugins/BubbleCommand/config.yml` under `custom-commands`
2. Use `/bubblecommand reload` to register the new commands
3. That's it! No need to rebuild or restart the server

## Example: Adding a `/shop` command

1. Edit `plugins/BubbleCommand/config.yml` and add:
```yaml
custom-commands:
  shop:
    aliases: ["store", "market"]
    action: "warp shop"
    permission: ""
    description: "Open the shop"
    player-only: true
```

2. Run `/bubblecommand reload` in-game or console
3. The `/shop`, `/store`, and `/market` commands are now available!

## Commands

- `/bubblecommand` or `/bcommand` - Show plugin info
- `/bubblecommand reload` - Reload configuration and register new commands
- All custom commands defined in config.yml (dynamically registered)

## Permissions

- `bubblecommand.admin` - Access to admin commands
- `bubblecommand.use` - Use custom commands (default: true)
- Custom permissions as defined in your config

## Placeholders

- `%player%` - Player's name
- `%uuid%` - Player's UUID
- `%world%` - Player's world
- `%sender%` - Command sender's name
- `%arg0%`, `%arg1%`, etc. - Command arguments
- `%args%` - All arguments joined

## Action Prefixes

- `console:` - Execute as console command
- `player:` - Execute as player command (default)

## Example Actions

- `"spawn"` - Teleport player to spawn
- `"console:give %player% diamond 1"` - Give diamond as console
- `"player:warp mall"` - Make player run /warp mall
- `"panels open shop %player%"` - Open DeluxeMenus panel

## Key Features

✅ **No JAR Rebuilding Required** - Add commands via config only  
✅ **Dynamic Command Registration** - Commands registered at runtime  
✅ **Hot Reload** - Use `/bubblecommand reload` to apply changes  
✅ **Full Alias Support** - Multiple aliases per command  
✅ **Rich Placeholders** - Player data, arguments, etc.  
✅ **Permission System** - Optional permissions for each command  
✅ **Console & Player Actions** - Execute as console or player
