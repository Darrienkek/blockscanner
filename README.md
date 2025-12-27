# BlockScanner Mod

THIS PROJECT IS VIBE CODED INTO NEXT WEEK.

Client-side Fabric mod for Minecraft 1.21.11. It scans nearby chunks for
barrier and command blocks, then exposes results in a local web UI.

## Requirements

- Minecraft: 1.21.11
- Fabric API: `fabric-api-0.140.0+1.21.11`
- Java: 21

## Build

1. `.\gradlew.bat build`
2. Find the output in `build/libs`.

## Run

1. Install Fabric Loader for 1.21.11.
2. Drop the mod jar and the Fabric API jar into your `mods` folder.
3. Launch Minecraft.
4. Open the web UI at `http://localhost:8080`.

## Notes

- Scanning is player-driven; it only scans around the chunk you stand in.
- The map highlights the next target chunk to move to.
