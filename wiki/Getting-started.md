# Getting started

## What you need

- Your own copy of the game, as a file on your computer. This project does not include or distribute any game. Use an **unmodified retail dump**; ROM hacks and bad dumps may randomize incorrectly (the program tells you when a file is not a clean dump and which options may be affected).
- For 3DS games, a **decrypted** dump (`.3ds`, `.cci` or `.cxi`). 3DS games need about 6 GB of free memory while randomizing.

## Download and start

When a release is published it is on the Releases page. Extract the archive. It contains its own Java, so nothing else has to be installed.

| Platform | Classic interface | Web interface |
|---|---|---|
| Windows | `launcher.bat` | `web-launcher.bat` |
| Linux | `launcher.sh` (run `chmod +x` first) | `web-launcher.sh` |
| macOS | `launcher.command` | `web-launcher.command` |

Keep the window that opens while you use the program. The web launcher opens your browser by itself; if it does not, copy the address it prints (it starts with `http://127.0.0.1`).

## Randomize a game

1. Choose your game.
2. Pick the options you want (or one of the presets: Gentle, Classic random, Total chaos).
3. Pick the extras you want (shiny chance, egg box, shop items and so on).
4. Choose where to save and press **Randomize**.

Your original game file is never changed: a new file is written where you say. For 3DS games the result is a folder for Luma3DS (LayeredFS), ready to copy to the `luma/titles` folder of the console's SD card.

## Seeds

A seed makes a randomization repeatable. Run the same game with the same settings and the same seed and you get the same result. Leave the seed empty for a random one.

## Settings

Settings can be saved and shared as a settings string or a `.rnqs` file. The extras of this project travel inside them. A settings file never carries your acceptance of code patches: you confirm those every run.

## Command line

`java -jar UPR-FVX.jar cli -h` lists every option, including `--shiny-chance`, `--egg-box`, `--shop-item`, `--boost`, `--evolution` and `--html-guide`. The program reads its `data/` folder from the folder it is started in, so start it from the release folder. 3DS games need `-Xmx6g`.
