# Scavengers

A GUI-first, server-wide scavenger hunt race for Paper 26.2.

## Requirements

- Paper 26.2
- Java 25

## Build

```powershell
.\gradlew.bat build
```

The deployable JAR is written to `build/libs/Scavengers-1.0.0.jar`.

## Use

Run `/scav`, `/scavengers`, or `/scavengerhunt` to open the player hunt GUI. Administrators can open the separate configuration dashboard with `/scavengers settings` (or the equivalent alias). All hunt settings, objectives, prizes, and controls are available in-game.

Permissions:

- `scavengers.use` — use the plugin (default: everyone)
- `scavengers.admin` — configure and control hunts (default: operators)
