# Real shiny chance

Sets how many Pokemon are shiny, from 0 to 100 percent in steps of 0.1, for wild, static, gift, egg and trainer Pokemon, in the games listed on [Supported games](Supported-games).

## How it works

In every generation the game decides "is this Pokemon shiny?" with one small piece of code. The program changes that code, in the randomized copy only, so that besides the normal rule a share of Pokemon (picked by their own personality number) also counts as shiny. The sprites, the sparkle and the summary screen all follow it.

Two details matter and were learned the hard way:

- The game also asks the same question while it **creates** a Pokemon, in loops such as "roll again until it is not shiny". Those loops keep the original behaviour, otherwise a chance of 100 percent would freeze the game. Only the code that **shows** a Pokemon uses the new rule.
- In Generation 2 there is no personality number. Shininess comes from the four DVs, so the new rule uses a rearrangement of them that keeps every single DV evenly distributed among the shiny Pokemon (no skew toward one gender or weak stats).

## Limits

- It changes the game's code, so you confirm it before every run. Your original file is never touched.
- A Pokemon made shiny this way is shiny **only inside the patched game**. It is not shiny in the data, so it is not shiny if you move it to another game, and a save editor will not show it as shiny.
- Generation 2: trainers use fixed DVs per trainer class, so for them the share of shiny Pokemon is not the chosen percentage (a whole class is shiny or not). Wild, gift and egg Pokemon follow the percentage.
- It was checked by emulating the patched code against the original over many random inputs and by reading the output files, not by playing.
