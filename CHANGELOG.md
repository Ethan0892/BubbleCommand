# Changelog

All notable changes to this project will be documented in this file.

## [1.0.1] - 2026-02-25

### Changed
- Refactored command execution flow into smaller helper methods for maintainability.

### Fixed
- Removed a stray merge-conflict marker in `README.md`.

### Improved
- Safer startup: plugin now logs a clear error and disables itself if `plugin.yml` command registration is misconfigured.
- Tab completion for `/bubblecommand` now only suggests `reload` to senders who have `bubblecommand.admin`.
