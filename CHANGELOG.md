# Changelog

All notable changes to this project will be documented in this file.

## [2.1.2] - 2026-04-17

### Added
- Sound support for custom commands via `sound:`, `sound-volume:`, and `sound-pitch:` config fields.
- Example commands (`farm`, `heal`, `spawn`) added to the default `config.yml`.

## [2.1.1] - 2026-02-25

### Changed
- Refactored command execution flow into smaller helper methods for maintainability.

### Improved
- Safer startup: plugin now logs a clear error and disables itself if `plugin.yml` command registration is misconfigured.
- Tab completion for `/bubblecommand` now only suggests `reload` to senders who have `bubblecommand.admin`.

## [2.1.0] - 2026-01-07

### Added
- PlaceholderAPI placeholder support in actions/messages (optional).

### Changed
- Improved dynamic command unregister compatibility on newer Paper/Purpur.

## [2.0.0] - 2025-12-16

### Added
- Complete rewrite for better performance.
- Multi-action support.
- Advanced action types: `title`, `actionbar`, `broadcast`, `sound`.
- Comprehensive cooldown system with data persistence and async saves.
- More placeholders.
- Debug mode.

### Improved
- Error handling throughout.
- Production-ready code quality with extensive documentation and examples.

