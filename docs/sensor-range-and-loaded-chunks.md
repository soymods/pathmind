# Sensor range and loaded chunks

Pathmind sensors only inspect world data already available to the Minecraft client. They do not load or generate chunks. A block, item entity, mob, or player outside the client's loaded/tracked area cannot be detected until Minecraft receives it.

## Range rules

| Sensor or target | Default range | Notes |
| --- | ---: | --- |
| Block, item entity, entity, and player target searches | 64 blocks | A supplied `Range` value overrides the default. Block searches skip unloaded chunks. Entity searches only see client-tracked entities. |
| Targets used by `Distance Between` | 256 blocks | Still limited to loaded chunks and client-tracked entities. |
| `Closest Open Block` | 5 blocks | Configurable by its `Range` field and capped at 32 blocks. Only loaded chunks are inspected. |
| `Rendered` and `Visible` | Current client view | Bounded by the client's render distance and the data currently rendered/tracked. `Visible` also requires an unobstructed view. |
| `Touching Block` and `Touching Entity` | Contact only | Tests the player's collision box; it does not perform a nearby search. |
| `At Coordinates` | Exact occupied block | Compares the player's current block position, including negative-coordinate floor behavior. |

## Practical behavior

- Increasing a range does not make the server send distant chunks or entities.
- Multiplayer server view-distance and entity-tracking settings can impose a smaller effective range.
- A negative result means “not found in currently loaded client data,” not necessarily “does not exist in the world.”
- Moving close enough for a chunk or entity to load lets the next sensor evaluation detect it; no graph restart is required.
