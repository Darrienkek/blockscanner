# BlockScanner Installation Guide (Beginner Friendly)

This guide is for people who have never built a Minecraft mod before.

If you follow it step by step, you will:
1. Compile this mod from source.
2. Install it into Minecraft.
3. Open the BlockScanner web UI and confirm it works.

## What You Need

- Minecraft Java Edition
- Fabric Loader for **Minecraft 1.21.11**
- Java **21** (required to build this project)
- Internet connection (for first build + downloading dependencies)

Project versions used by this repo:
- Minecraft: `1.21.11`
- Fabric Loader: `0.18.3+`
- Fabric API: `0.140.0+1.21.11`
- Mod version produced by this repo: `1.1`

## Step 1: Install Java 21

1. Install JDK 21 (not JRE).
2. Open a terminal and run:

```powershell
java -version
```

You should see version `21.x`.

If you see `java is not recognized` or a different major version:
- Install JDK 21.
- Restart your terminal.
- Check `java -version` again.

## Step 2: Build the Mod

Open a terminal in the project folder (`w:\minecraft plugin`) and run:

### Windows

```powershell
.\gradlew.bat build
```

### macOS / Linux

```bash
./gradlew build
```

When successful, you should see `BUILD SUCCESSFUL`.

Output jar files are created in:

- `build/libs/blockscanner-1.1.jar`
- `build/libs/blockscanner-1.1-sources.jar` (not needed to run)

Use `blockscanner-1.1.jar` for Minecraft.

## Step 3: Install Fabric Loader

1. Install Fabric Loader for **Minecraft 1.21.11**.
2. Start Minecraft once using the Fabric profile, then close Minecraft.

This creates/updates the correct `mods` folder.

## Step 4: Install Required Mods

Put these two jars into your Minecraft `mods` folder:

1. `blockscanner-1.1.jar` (from this project’s `build/libs` folder)
2. Fabric API jar for `1.21.11`:
   - `fabric-api-0.140.0+1.21.11.jar`

Common `mods` folder locations:

### Windows
- `%APPDATA%\.minecraft\mods`

### macOS
- `~/Library/Application Support/minecraft/mods`

### Linux
- `~/.minecraft/mods`

## Step 5: Launch and Verify

1. Launch Minecraft with the **Fabric** profile.
2. Join a world (singleplayer or multiplayer).
3. You should see a chat message like:
   - `[Block Scanner] Web server running at http://localhost:8080`
4. Open a browser and go to:
   - `http://localhost:8080`

If the page loads, installation worked.

## Basic First Use

1. In-game, switch to spectator mode (for auto movement scanning).
2. Open `http://localhost:8080`.
3. Click **Start Scanning**.
4. Watch status/chunk data update in the UI.

Optional:
- Enable **Manual scan mode (no auto movement)** in settings to scan around your current chunk without waypoint movement.
- Use the navigation controls to move to chunk or block X/Z targets without starting scan.

## Updating After Code Changes

Each time you change code:

1. Rebuild:

```powershell
.\gradlew.bat build
```

2. Copy the new `build/libs/blockscanner-1.1.jar` into `.minecraft/mods` (replace old file).
3. Restart Minecraft.

## Troubleshooting

### Build fails with Java version errors

Cause: wrong Java version.

Fix:
1. Install JDK 21.
2. Make sure `java -version` reports `21`.
3. Run build again.

### `gradlew` command fails

Cause: terminal not in project directory.

Fix:
1. `cd` into project root (where `gradlew.bat` exists).
2. Run:

```powershell
.\gradlew.bat build
```

### Game crashes on startup / mod not loading

Check:
1. You are launching **Fabric**, not vanilla/Forge/NeoForge.
2. Minecraft version is **1.21.11**.
3. Fabric API jar is installed in `mods`.
4. You copied `blockscanner-1.1.jar` (not sources jar).

### Web UI does not open

Check:
1. In-game message says web server is running.
2. Open exactly: `http://localhost:8080`
3. No other app is already using port `8080`.

### Nothing scans

Check:
1. You clicked **Start Scanning** in UI.
2. For auto traversal mode, player is in spectator mode.
3. For manual mode, **Manual scan mode** is enabled and you are in loaded chunks.

## Quick Command Summary

### Build

```powershell
.\gradlew.bat build
```

### Run tests

```powershell
.\gradlew.bat test
```

### Build + tests

```powershell
.\gradlew.bat build
```

