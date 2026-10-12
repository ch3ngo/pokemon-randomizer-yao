# The web interface

An alternative to the classic window. It is a web page served by the program itself on your own computer.

## How it works

- Start `web-launcher` (release) or `./launch-web.sh` / `launch-web.bat` (source checkout). The program opens your browser at an address like `http://127.0.0.1:8765/?t=...`.
- It listens only on your own machine (the loopback address). Nothing is sent over the internet and no game data is uploaded. A random token in the address, different for every run, is required for every request, so other web pages open in your browser cannot talk to it.
- Close the launcher window to stop it.

## The four steps

1. **Game.** Choose a file with the built-in file browser (a web page cannot read file paths, so the program lists the folders of your computer). The page then shows what the game can and cannot do.
2. **Randomizer.** All the options of the original randomizer, with the original wording and tooltips, grouped by topic. Options that do not apply to your game are hidden, and the options of a topic stay hidden until you turn the topic on. Search finds any option. Presets set many options at once.
3. **Extras.** The features that only this project has.
4. **Run.** Where to save, seed, log and guide options, a summary, and the live output of the run.

## Good to know

- Settings are remembered in your browser between visits (only in your browser, never sent anywhere).
- The mouse effects (a background that bends toward the pointer, a glowing cursor, cards that light up) can be switched off with the pointer button at the top right. They are off automatically if your system asks for reduced motion. There is also a light theme.
- For development, starting the program with `-Dyao.web.dir=<folder>` serves the page files from disk.

## Where the game is saved

You can type anything in the "Save as" box; the program works out the real destination itself and tells you, in the run output and on the result card, if it had to change what you typed.

| Game | What you typed | What is saved |
|---|---|---|
| GB, GBC, GBA, DS | `My game` | `My game.gba` (the extension of your game is added) |
| GB, GBC, GBA, DS | `My game.nds` for a GBA game | `My game.gba` (a wrong game extension is replaced, with a warning) |
| GB, GBC, GBA, DS | an existing folder | `Original name randomized.<ext>` inside it |
| Any | the original game file | `Original name randomized.<ext>` next to it, never the original |
| 3DS as a folder | `My game` or `My game.3ds` | a folder `My game` holding `luma/titles/<title id>/` (code.bin and romfs) |
| 3DS as one file | `My game` | `My game.cxi` |

The original game is never replaced. If the destination already holds a result, you are asked before it is replaced. The folders it needs are created for you.

## Playing a randomized 3DS game

A 3DS game can be saved in two ways, chosen on the Run step:

- **One .cxi file.** Open it in Citra or Azahar (File, Load File). It contains the whole game and runs on its own. Do not have a game update for the same game installed in the emulator. The file is big (1.7 GB to 3.5 GB).
- **A LayeredFS folder.** Small, but it is not a game on its own: it only holds what changed. Copy the `luma` folder to the root of the console's SD card (it ends up as `/luma/titles/<title id>/`), turn on "Enable game patching" in the Luma3DS menu (hold Select while switching the console on) and start the game. In an emulator, open the game's mods location and copy what is inside the `<title id>` folder there. **The same game version must be installed** as the one you randomized (the version that comes with the file, or the one of the update you loaded). A different version makes the game crash on start or ignore the changes, which looks like a game that "cannot be played".
