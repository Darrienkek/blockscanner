# BlockScanner Mod

THIS PROJECT IS VIBE CODED INTO NEXT WEEK.

Client-side Fabric mod for Minecraft 1.21.11. It scans chunks in an automatic
7x7 window spiral and records configured block hits for a local web UI.

## Requirements

- Minecraft: 1.21.11
- Fabric API: `fabric-api-0.140.0+1.21.11`
- Java: 21

## Build

1. `./gradlew.bat build`
2. Find the output in `build/libs`.

## Run

1. Install Fabric Loader for 1.21.11.
2. Drop the mod jar and the Fabric API jar into your `mods` folder.
3. Launch Minecraft and join singleplayer or multiplayer.
4. Put your player in spectator mode.
5. Open the web UI at `http://localhost:8080`.

## Notes

- Scanning is automatic while enabled: the mod navigates the player through a
  squared spiral that starts at chunk `0,0`.
- Each waypoint scans a centered `7x7` chunk batch before advancing.
- Traversal progress is persisted per server and per dimension, then resumed on
  reconnect.
- If you leave spectator mode, traversal pauses until spectator is restored.