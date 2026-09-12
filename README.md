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

Run `/scav`, `/scavengers`, or `/scavengerhunt` to open the player hunt GUI. Players can inspect the active hunt's prizes with `/scav rewards` and confirm a required-item submission with the clickable chat prompt or `/scav submit`.

Administrators can open the configuration dashboard with `/scav settings`. The dashboard configures the unique item pool, the number of randomly selected stages, prizes, milestone announcements, optional confirmed item consumption, and hunt controls. `/scav stop --force` immediately stops an active hunt for an administrator or the server console while preserving progress and earned reward claims.

Permissions:

- `scavengers.use` — use the plugin (default: everyone)
- `scavengers.admin` — configure and control hunts (default: operators)
