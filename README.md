# Handhold

A Fabric mod for Minecraft 1.21.11 that lets players request to hold hands. Once the receiver accepts, they follow the requesting player until either player releases the hold.

## Commands

- `/handhold request <player>` — send a hand-holding request.
- `/handhold accept <player>` — accept a request from that player.
- `/handhold deny [player]` — decline the current request, optionally only if it came from that player.
- `/handhold release` — release an active handhold. Either player can use this command.

Requests expire after 60 seconds. Both players must be in the same dimension to accept, but there is no distance limit: after accepting, the follower approaches the leader before taking a consistent left or right side, slightly behind. Any player may have one leader and multiple followers, allowing a chain or branching group; requests that would create a cycle are rejected. Follow motion is smoothed and collision-aware, and the follower's position is synchronized to their client. Players must install Handhold on both the server and their Fabric client: while following, local movement keys are suppressed so client prediction cannot fight the server-controlled movement. Holding a movement key away from a nearby leader releases the hold; either player can also use `/handhold release`. Vertical following is limited to shared swimming or airborne movement so a grounded follower keeps walking on the terrain; followers jump when their leader jumps. The follower also smoothly matches the leader's yaw and pitch. The hold ends if either player disconnects or they end up in different dimensions.

## Development

This project uses Fabric Loom and Kotlin. Run `./gradlew build` (or `gradlew.bat build` on Windows) to build the mod.

Build artifacts start at `0.1-alpha`. The version increments to the next alpha version only when files under `src` change; rebuilding unchanged sources keeps the same version. Previous JARs remain in `build/libs`, including when running `gradlew clean`.
