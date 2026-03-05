# BlockScanner

This project was very much coded with AI.

There are no compiled jar releases, and there will not be any. If you do not know how to build a Fabric mod from source, you should not use this mod.

`BlockScanner` is a client-side Fabric mod for Minecraft `1.21.11` that scans loaded chunks for configured blocks and selected entity types, stores scan state and block findings locally, and exposes a local web UI at `http://localhost:8080`.

## Requirements

- Minecraft `1.21.11`
- Fabric Loader `0.18.3+`
- Fabric API `0.140.0+1.21.11`
- Java `21`

## What The Mod Does

- Runs entirely on the client.
- Starts a local HTTP server on port `8080`.
- Scans loaded chunks for target blocks.
- Scans loaded chunks for selected entity types.
- Tracks scanned and skipped chunks by dimension.
- Moves the player automatically in spectator mode for hands-off traversal.
- Shows progress, hits, and chunk coverage in a browser UI.
- Saves config and scan data locally per server.

## Current Feature Set

### Scanning

- Automatic spiral traversal across chunk waypoints.
- Default batch radius `3`, which means a `7x7` chunk window (`49` chunks) is scanned at each waypoint.
- Configurable batch radius from `1` to `6` (`3x3` through `13x13` chunk windows).
- Configurable scan throughput from `1` to `16` chunks per tick per enabled scanner.
- Separate toggles for block scanning and entity scanning.
- Manual scan mode that stays centered on your current chunk and does not auto-move.
- Optional rescanning of already scanned chunks.
- Auto-pause when auto movement is active and you are not in spectator mode.
- Per-dimension spiral cursor tracking, so traversal progress resumes correctly.
- Manual traversal start reset from the web UI for the current dimension.

### Default Targets

Default target blocks:

- `minecraft:barrier`
- `minecraft:command_block`
- `minecraft:chain_command_block`
- `minecraft:repeating_command_block`

Default entity targets:

- `minecraft:block_display`
- `minecraft:item_display`
- `minecraft:text_display`
- `minecraft:item_frame`
- `minecraft:glow_item_frame`

### Sign Scanning

- Optional sign scanning toggle in the UI.
- Detects normal signs, wall signs, hanging signs, and wall hanging signs.
- Captures front and back sign text when present.
- Shows signs in a separate panel in the web UI.

### Announcements And Messages

- Local client chat message when a matching block or entity is found.
- Optional server chat announcements for configured IDs.
- Periodic progress message to server chat while auto scan is running.
- Web server status message in chat when you join a world.

### Navigation Tools

- Move to a target chunk without starting a scan.
- Move to a target block without starting a scan.
- Stop manual navigation from the UI.
- Click a result in the UI to copy a `/tp x y z` command to your clipboard.

## Web UI

Open `http://localhost:8080` after joining a world.

The UI currently provides:

- Start/stop scanning.
- Found result list grouped by block or entity ID.
- Optional sign results panel.
- Chunk coverage map with separate tabs for Overworld, Nether, and End.
- Current traversal status:
  - active/idle state
  - current dimension
  - waypoint grid position
  - center chunk
  - spiral direction
  - batch progress
  - completed waypoint count
- Config editor for:
  - target block IDs
  - announcement IDs
  - rescan toggle
  - sign scanning toggle
  - block scanner toggle
  - entity scanner toggle
  - manual scan mode
  - chunks per tick
  - batch radius
- Traversal start controls.
- Manual navigation controls.
- Clear-server-data button.

## Persistence

BlockScanner stores files under your Minecraft config directory in `config/blockscanner/`.

Saved config:

- `scan-config.json`

Saved per-server scan data:

- `<server>.json`
- `<server>.backup.json`

Current persistence behavior:

- Config is saved from the web UI.
- Active scan data is saved roughly every `30` seconds while scanning.
- Rolling backups are refreshed roughly every `10` minutes.
- Saved server data is separated by server address.
- Persisted scan data currently covers saved block findings, scanned chunks, and traversal state.
- Traversal state is stored per dimension.
- The clear button deletes the current server's persisted scan data.

## Build

### Windows

```powershell
.\gradlew.bat build
```

### macOS / Linux

```bash
./gradlew build
```

Primary output jar:

- `build/libs/blockscanner-1.1.jar`

There is a more step-by-step guide in [installation.md](installation.md).

## Install

1. Build the jar yourself.
2. Install Fabric Loader for Minecraft `1.21.11`.
3. Put `blockscanner-1.1.jar` into your `mods` folder.
4. Put the matching Fabric API jar into your `mods` folder.
5. Launch Minecraft with the Fabric profile.
6. Join a world and open `http://localhost:8080`.

## Usage Notes

- Auto traversal expects spectator mode for movement.
- Manual scan mode does not require traversal movement.
- The mod only scans loaded chunks. Unloaded chunks are tracked as skipped.
- Changing settings while scanning is known to be buggy. Stop the scan first if you want predictable behavior.
- This is a local tool with a local web UI, not a polished end-user release.

## Testing

Run tests with:

```powershell
.\gradlew.bat test
```

The repo includes JUnit and jqwik property tests for traversal, data storage, and persistence logic.
