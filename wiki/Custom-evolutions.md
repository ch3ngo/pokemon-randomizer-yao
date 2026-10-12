# Custom evolutions

- **Change or add an evolution:** choose the Pokemon it evolves from, the one it becomes, and how (level, stone, trade, friendship, a move, time of day and the other methods the game supports), with its detail (level, item, move or species).
- **Remove an evolution.**
- **Fully evolved by level:** every level evolution above a level you choose (15 to 100) is brought down to it, and middle stages to 75% of it. It works on its own, without ticking Make Evolutions Easier, and replaces that option's own level when both are on. Command line: `--fully-evolved-by-level <n>`. In the web interface the old level slider of Make Evolutions Easier is hidden: ticking only that option now changes the other things it does (trade and similar evolutions) and leaves levels alone.
- **Lower evolution levels:** every level evolution at or above a level you choose is lowered by a percentage. The evolutions you set yourself are never lowered.

## Safety checks

The program refuses an edit, and says why in the log, when:

- another evolution of the same Pokemon already uses the same trigger (two stones the same, two level evolutions, and so on; day and night versions, and male and female versions, do not overlap);
- it would create a loop (a Pokemon that evolves back into its own ancestor);
- the Pokemon already evolves from something else (remove that evolution first);
- the game cannot store that many evolutions for one Pokemon (5, 7, 7, 8 and 8 in Generation 3, 4, 5, 6 and 7) or that method;
- the item or move does not exist in the game.

Generation 1 and 2 can only change the level of an existing level evolution.
