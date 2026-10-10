# Pokemon Randomizer YAO

> Yet Another One. A Pokemon ROM randomizer for Gen 1 to Gen 7, built on top of Universal Pokemon Randomizer FVX, with extra tools for shops, shiny Pokemon, eggs, player sprites and safer randomization.

**Status: early development.** The current version behaves like Universal Pokemon Randomizer FVX 1.6.2. The additions listed under "In development" are being built and are not part of a release yet.

---

## What it does

Pokemon Randomizer YAO shuffles and randomizes the data of the mainline Pokemon games: wild Pokemon, starters, trainers, static and gift encounters, in-game trades, moves, movesets, evolutions, abilities, items, shops, TMs and more. It aims to keep every randomization option of FVX and add more control on top.

### Supported games

Generation 1 to Generation 7, including the 3DS games up to Ultra Sun and Ultra Moon. Switch games are not supported. It is developed and tested with English and Spanish ROMs where both exist.

### Included from FVX

- Randomization of wild Pokemon, trainers, starters, trades, static and gift encounters, moves, movesets, evolutions, abilities, base stats, types, TMs, tutors, items and shops.
- Many gameplay improvements, such as making HMs usable by every Pokemon.
- Custom player graphics for Gen 1 to 3.
- A graphical interface and a command line mode.
- Release builds that bundle their own Java runtime, so Java does not need to be installed.

### In development

- Extra items in the shops, with a price for each (for example Rare Candy for 1), configurable per game.
- Real shiny Pokemon with an adjustable chance, from none to all, for wild, static, gift, trainer and egg Pokemon.
- Boxes of eggs placed directly in the game, filtered by type of Pokemon (legendary, non-legendary, three-stage lines and more).
- Player sprite replacement for every generation, with a library of ready-made characters.
- Protected item lists, so random shop items never replace the ones you chose.
- Locke and Soft-locke modes for challenge runs.
- A check that warns about randomizations known to get a game stuck.
- A clear warning when a ROM is not an unmodified retail dump, listing which options may be affected.
- An optional, spoiler-safe HTML guide of what was randomized, in English and Spanish.
- A new, more modern look and native installers for Windows, macOS and Linux.

## Running it

A release archive contains its own Java runtime, so there is nothing to install first. This project has no release yet. When one is published it will be on the Releases page, with the same layout as the FVX release archives:

| Platform | How to start it |
|---|---|
| Windows | Extract the archive and run `launcher.bat` |
| Linux (x86 and ARM) | Extract the archive, then `chmod +x launcher.sh` and run `./launcher.sh` |
| macOS (Intel and Apple Silicon) | Extract the archive and run `launcher.command` (the first time you may need to right click it and choose Open, because the app is not notarized) |

Then open your ROM, choose your options and save the randomized ROM. Always keep a copy of your original ROM.

### Your ROMs

You need your own ROM files. This project does not include or distribute any ROMs, and it does not include game data. The randomizer expects an unmodified retail dump; modified ROMs, ROM hacks and bad dumps may randomize incorrectly.

### 3DS games

3DS games must be **decrypted**. The randomizer reads `.3ds`/`.cci`, `.cia` and `.cxi` files. It can save the result as a `.cxi` file (very large, 1.7 to 3.5 GB) or as a LayeredFS folder, which is much smaller and also works on a 3DS with custom firmware. If you load a game update, the version of that update must match the one installed in your emulator or console. 3DS games need more memory; start with `-Xmx4096M` if you run the command line.

## Credits

This project descends from a chain of forks, and all credit for the randomizer itself belongs to their authors:

- **[Dabomstew](https://github.com/Dabomstew/universal-pokemon-randomizer)**: the original Universal Pokemon Randomizer.
- **Ajarmar** and the **UPR-ZX** team: [Universal Pokemon Randomizer ZX](https://github.com/Ajarmar/universal-pokemon-randomizer-zx), built on Dabomstew's work.
- **FVX** ([foxoftheasterisk](https://github.com/foxoftheasterisk), [voliol](https://github.com/voliol)) and the [upr-fvx](https://github.com/upr-fvx/universal-pokemon-randomizer-fvx) contributors: Universal Pokemon Randomizer FVX, built on UPR-ZX.
- **yao** (ch3ngo): the additions listed above, built on FVX.

Changes in this fork are modifications of FVX and are marked as such in the code and the commit history, starting 10/10/2026. The original FVX readme is kept as `UPSTREAM_README.md`.

Pokemon and all related names, sprites and game data belong to Nintendo, Game Freak and Creatures. This project is not affiliated with or endorsed by them.

## License

GPL-3.0-or-later, inherited from upstream. See `LICENSE.txt`. The source file headers of the original authors are kept intact.

## Feedback

Issues are welcome on the Issues page. This is a personal project, so replies may take a while.
