# Mega finale (experimental)

> **Beta.** The data is checked, but it has not been played. Whether a trainer Mega Evolves in battle with only the held stone is the game's own rule.

For games with Mega Evolutions (X, Y, Omega Ruby, Alpha Sapphire, Sun, Moon, Ultra Sun, Ultra Moon), the last Pokemon of the trainers you choose holds the mega stone of one of its Mega Evolutions, so the fight ends with a Mega.

## Who gets it

Pick any combination of tiers (they are independent, and can be combined freely):

- **Gym leaders**: true gym leaders (Giovanni's gym fight in FireRed/LeafGreen included).
- **League**: the Elite Four, the Champion, Uber-tier trainers and the Sun/Moon kahunas. In Red/Blue/Yellow, FireRed/LeafGreen and Sun/Moon/Ultra Sun/Ultra Moon the Champion role is played by a tagged rival or Kukui battle, which is recognised too.
- **Evil-team bosses**: Team Rocket/Magma/Aqua/Galactic/Plasma/Flare/Skull+Aether leaders.
- **Other bosses**: rivals and strong team admins (but not the very first rival fight).
- **Everyone**: every trainer in the game.

## From this level onward

"Start from level" (0 to 100, 0 means from the start) skips every trainer whose strongest Pokemon is below that level. It works on the final team, so it follows your level changes. Use it to begin at, say, the third gym: the interface shows the level of each gym leader in the unmodified game to help you pick the number. It also works for evil-team bosses.

## Details

- A trainer that already has a Mega is left alone (a trainer Mega Evolves only one Pokemon per battle).
- If the last Pokemon has no Mega, it is left as it is, or (if you choose) replaced by a Pokemon that has one, keeping its level.
- It runs after the other trainer options, so it sees the final team.
- The log says how many trainers of each tier got a Mega and how many were skipped by the start level.
- Command line: `--mega-finale gym,league,evil,other,everyone` (a comma separated list; the older `none|leaders|boss|all` still work), `--mega-from-level <n>`, `--mega-finale-replace`, and `--mega-finale-evil-bosses` (adds the evil-team tier).
- A settings file saved with the older single-choice format is converted automatically, with a warning.
