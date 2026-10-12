# Locke mode (experimental)

> **Beta.** This feature may not fully work and has not been played through. It will be finished in a later release.

- **Soft-locke:** Pokemon Centers and whiting out no longer revive fainted Pokemon.
- **Locke:** the same, and Revive, Max Revive, Revival Herb and Sacred Ash are removed from shops, field items and pickup.

The Pokemon Center part is a code patch (one byte) available for HeartGold, SoulSilver, Black 2 and White 2. The item part works in every game.

## Limits

- Items handed over by story scripts cannot be changed.
- A fainted Pokemon left in a PC box is restored when it is taken out, so a dead Pokemon must stay boxed. The game cannot enforce that.
- Encounter rules (first encounter per route, duplicates clause) are not enforced by a game file; they belong to a tracker.
