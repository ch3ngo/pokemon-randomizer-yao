# Supported games

Generation 1 to Generation 7 (including the 3DS games up to Ultra Sun and Ultra Moon). Switch games are not supported. Japanese games are out of scope. The program is developed with English and Spanish games; names of Pokemon, items and moves always come from your game file.

## What each extra needs

The extras that change the game's program code or scripts only work on versions whose code is known, found by exact bytes. A version that does not match is left untouched and the program says so.

| Extra | Verified on |
|---|---|
| [Real shiny chance](Real-shiny-chance) | Crystal (English), Emerald (English), HeartGold and SoulSilver (English and Spanish), Black 2 (English and Spanish), X, Omega Ruby, Sun, Ultra Sun (Europe, English text) |
| [Egg box](Egg-box) | Emerald (English) |
| [Shops](Shops), [Stat booster](Stat-booster), [Softlock check](Softlock-check), [HTML guide](HTML-guide) | every supported game |
| [Custom evolutions](Custom-evolutions) | Gen 1 and 2: only the level of an existing level evolution; Gen 3 to 7: everything |
| [Locke mode](Locke-mode) (experimental) | item part everywhere; Pokemon Center part on HeartGold, SoulSilver, Black 2 and White 2 |

"Verified" means built and checked by reading the output game back and by emulating the patched code. It does not mean played through. If you play one of these and something is wrong, please open an issue.

## 3DS games: how the result is saved

X, Y, Omega Ruby, Alpha Sapphire, Sun, Moon, Ultra Sun and Ultra Moon must be decrypted dumps. The randomized game is saved as one `.cxi` file (plays on its own in an emulator) or as a LayeredFS folder laid out as `luma/titles/<title id>/` (for Luma3DS or an emulator's mods folder, needs the same game version installed). See [The web interface](The-web-interface) for the steps. The title ids come from your game file (for example Omega Ruby is `000400000011C400`, but always use the folder the program made).
