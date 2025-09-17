# BubbleCommand Plugin

A user-friendly Minecraft plugin for Purpur 1.21.8+ that allows server administrators to create custom commands through configuration files.

## Features

- **Easy Configuration**: Define custom commands in `config.yml` with simple syntax
- **Multiple Aliases**: Support for command aliases
- **Placeholder Support**: Built-in placeholders for player data and arguments
- **Permission Support**: Optional permission requirements for commands
- **Console & Player Commands**: Execute commands as console or player
- **Hot Reload**: Reload configuration without restarting the server
- **User-Friendly**: Comprehensive examples and documentation

## Installation

1. Download the latest release
2. Place `BubbleCommand.jar` in your `plugins/` folder
3. Start/restart your server
4. Edit `plugins/BubbleCommand/config.yml` to add your custom commands
5. Use `/bubblecommand reload` to reload the config

## Configuration

The plugin uses a simple YAML configuration format:

```yaml
custom-commands:
  daily:
    aliases: ["dailyreward"]
    action: "panels open dailyreward %player%"
    permission: ""
    description: "Open daily reward panel"
    player-only: true
```

### Available Placeholders

- `%player%` - Player's name
- `%uuid%` - Player's UUID  
- `%world%` - Player's current world
- `%sender%` - Command sender's name
- `%arg0%`, `%arg1%`, etc. - Command arguments
- `%args%` - All arguments joined

### Action Prefixes

- `console:` - Execute as console command
- `player:` - Execute as player command (default)

## Commands

- `/bubblecommand` - Show plugin info
- `/bubblecommand reload` - Reload configuration

## Permissions

- `bubblecommand.admin` - Access to admin commands (default: op)
- `bubblecommand.use` - Use custom commands (default: true)

## Examples

See the default `config.yml` for comprehensive examples including:
- Daily reward commands
- Heal commands
- Spawn/hub commands
- Kit commands
- Broadcast commands

## Building

This project uses Maven. To build:

```bash
mvn clean package
```

## Compatibility

- Minecraft 1.21.8+
- Purpur (recommended)
- Paper
- Spigot
