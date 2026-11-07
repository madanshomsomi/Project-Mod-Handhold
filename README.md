# Handhold

A Fabric mod for Minecraft 1.21.11 that lets players request to hold hands. Once the receiver accepts, they follow the requesting player until either player releases the hold.

## Commands

- `/handhold request <player>` — send a hand-holding request.
- `/handhold accept <player>` — accept a request from that player.
- `/handhold deny [player]` — decline the current request, optionally only if it came from that player.
- `/handhold release` — release an active handhold. Either player can use this command.

Requests expire after 60 seconds. Both players must be in the same dimension to accept. The following player stays beside the requesting player, on the left or right side; the link ends if either player disconnects or they end up in different dimensions.

## Development

This project uses Fabric Loom and Kotlin. Run `./gradlew build` (or `gradlew.bat build` on Windows) to build the mod.

Build artifacts start at `0.1-alpha`. The version increments to the next alpha version only when files under `src` change; rebuilding unchanged sources keeps the same version. Previous JARs remain in `build/libs`, including when running `gradlew clean`.
