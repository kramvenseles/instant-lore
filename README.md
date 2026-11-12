# Instant Lore

A Fabric mod that turns server-driven content into a runtime: dialogs,
cutscenes, camera effects, custom HUD, ghosts and more are delivered as
*data* from the server, so the client mod almost never needs a manual update.

Built for a production Minecraft server (1.21.11, Java 21) and running in
the wild — the design goal is "ship content without shipping a new client jar."

## What it does
- **Data-driven content runtime** — dialogs, cutscenes, effects, HUD layouts
  and events are described as data and fetched/cached on the client
  (`ClientContentUpdater`, `ContentCache`), decoupling content from code.
- **Self-updating jar** — a pre-launch updater keeps the mod current without
  the player reinstalling anything.
- **Rich client layer** — custom dialog screens, scripted cutscenes, camera
  effects, crosshair/boss-bar/HUD rendering, a lightweight client event bus.
- **Mod compatibility** — integrations and guards for REI, Simple Voice Chat,
  Xaero's Map, JourneyMap, Carpet and more (loaded only when present).

## Tech
Java 21 · Fabric Loader 0.19+ · Fabric API · Mixins · Gradle (Loom)

## Building
`./gradlew build` → `build/libs/instantlore-<version>.jar`

## License
MIT — see [LICENSE](LICENSE).
