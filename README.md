# Pokemon Randomizer YAO

> Yet Another One. A personal fork of Universal Pokemon Randomizer FVX, extending shop control, real shiny randomization, in-ROM egg boxes, custom sprites for Gen 4-7, a softlock validator, and packaging for Gen 1-7 (including 3DS, up to Ultra Sun/Ultra Moon).

**Status: idea/prototype.** Nothing listed under Roadmap below is implemented yet. This is a fork at the FVX baseline with project scaffolding (README, branch policy) added on top.

---

## Overview

Universal Pokemon Randomizer FVX already covers Gen 1 to 7 randomization well, but a few things I wanted were either missing, hacky, or out of scope for a two-person open source project: permanent shop items with real price/slot control, actual shiny Pokemon (not just a palette swap or a trainer IV hack), egg boxes placed directly in the ROM without touching the save file, custom player/trainer sprites for the DS and 3DS generations, and a validator that catches known static softlocks before they happen instead of after.

This fork exists to build those features for my own use, on top of FVX's codebase and conventions, without asking the upstream maintainers to take on work that is niche by their own stated standards (see `UPSTREAM_README.md`, "What is a good fit for the randomizer?"). It stays scoped to Gen 1-7 including the 3DS games (Ultra Sun/Ultra Moon is the upper bound); Switch-generation games are out of scope.

The repository keeps full upstream history and an `upstream` remote so changes can be rebased against FVX as it continues to develop.

## Features

Nothing below is implemented. This lists what FVX already provides as a baseline, and what this fork plans to add on top, in planned order.

### Baseline (from FVX, already present)
- Randomization of wild Pokemon, trainers, starters, trades, static/gift encounters, Pokemon palettes, items, and more, across Gen 1-7.
- Custom Player Graphics for Gen 1-3.
- A trainer-only shiny hack (IV bit 30) and a shiny-looking palette option, neither of which is a real shiny flag.
- CLI and Swing GUI.
- jlink runtime bundling for 5 platforms.

### Planned, this fork
- Configurable permanent extra shop items (slot limits, custom prices, per-game) (planned)
- Real shiny chance (0-100%) for wild, static, gift, trainer, and egg Pokemon, via per-generation PID/IV patches rather than palette tricks (planned)
- In-ROM egg boxes with species filters (legendary, non-legendary, N-stage lines), no save editing (planned)
- Custom player/trainer sprites for Gen 4-7 (NDS and 3DS) (planned)
- A rule-based static softlock validator run at randomization time (planned)
- CI-built native installers (msi/exe, dmg, deb/AppImage) via jpackage, and a longer-term headless-core-plus-Tauri-shell GUI (planned)

Detailed per-phase technical notes are kept on the `dev` branch.

## Requirements

- JDK 25 (the upstream project builds and tests against this version)
- Gradle (wrapper included, `./gradlew`)
- Your own legally dumped ROMs for any ROM-dependent build or test step. None are provided or distributed by this project.

## Installation

```bash
git clone https://github.com/ch3ngo/pokemon-randomizer-yao.git
cd pokemon-randomizer-yao
git checkout dev
./gradlew build
```

Place your own ROM files under `roms/` for the `testROMs` Gradle task. That folder is gitignored and nothing inside it is ever committed.

## Usage

Until the planned features land, usage is identical to upstream FVX: run the GUI jar, or use the CLI via `CliRandomizer` (`-i` input ROM, `-o` output ROM, `-s` settings file, and related flags). See `UPSTREAM_README.md` and the FVX wiki linked there for the current feature set and CLI reference.

## Project structure

```
random/      Core randomization logic: Randomizer classes per category, Settings, CLI, GUI
romio/       ROM reading/writing: RomHandler implementations per generation, graphics packs
utils/       Shared utility code
devtools/    Development and maintenance tooling
docs/        Project documentation and wiki source
roms/        Local ROM files for testing (gitignored, user-supplied, never committed)
```

## Branching policy

- `main`: only functional, verified, released versions. Nothing experimental lands here directly.
- `dev`: integration branch. All day-to-day work happens here or on feature branches merged into it.
- `feature/<name>`: one branch per feature, merged into `dev` when working.
- Promotion from `dev` to `main` is done by manually copying or cherry-picking the verified files, never a plain merge, so that internal development notes never reach `main`.

## Credits

This project descends from a chain of forks:

- **[Dabomstew](https://github.com/Dabomstew/universal-pokemon-randomizer)**: original Universal Pokemon Randomizer.
- **Ajarmar** and the **UPR-ZX** team: [Universal Pokemon Randomizer ZX](https://github.com/Ajarmar/universal-pokemon-randomizer-zx), built on Dabomstew's work.
- **FVX** ([foxoftheasterisk](https://github.com/foxoftheasterisk), [voliol](https://github.com/voliol)) and the [upr-fvx](https://github.com/upr-fvx/universal-pokemon-randomizer-fvx) contributors: Universal Pokemon Randomizer FVX, built on UPR-ZX.
- **yao** (this fork, ch3ngo): shop control, real shiny randomization, in-ROM egg boxes, Gen 4-7 custom sprites, softlock validation, and packaging work, built on FVX.

Changes in this fork are modifications of FVX and are marked as such in code and commit history, starting 10/10/2026.

Pokemon names, sprites, and all ROM data belong to Nintendo and Game Freak. This project does not ship any ROMs or copyrighted game assets. You must supply your own legally dumped ROM files to use it.

## License

GPL-3.0-or-later, inherited from upstream. See `LICENSE.txt`. All original source file headers are kept intact.

## Contributing

This is a personal project. Issues and pull requests are welcome, but see the branching policy above: contributions target `dev`, not `main`.
