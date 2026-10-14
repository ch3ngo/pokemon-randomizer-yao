# HTML guide

An optional page that shows everything that was randomized: evolutions, base stats, types, abilities, moves, TMs, trainers, static encounters, wild encounters, trades, shops and more, plus a "where to find each Pokemon" index and the whole text log.

- One self-contained `.html` file that works offline. It uses the same look as the web interface (red accent, dark and light theme) and falls back to your system fonts when it cannot load the web fonts.
- English and Spanish, switched with the EN / ES buttons without reloading (tables, search and sorting follow the language). The page opens in the language of your game. Every game term is shown in the language you pick, whatever language the ROM is in: Pokemon, moves, abilities, items, evolution methods, types and the names of wild places. A Spanish ROM read in English mode shows English names and an English ROM read in Spanish mode shows Spanish names (see "Names" below). The lists of options, the notes and the log are written by the program in English. Trainer names and classes come from the game and stay in its language.
- Overview with tiles that count what changed in each area (base stats, types, abilities, evolutions, movesets, moves, wild areas, trainers, shops...), the options that differ from the defaults, and every warning.
- Spoiler-safe by default: content is blurred behind a "Spoilers hidden" card until you reveal it. This protects against glancing at the screen, it is not security. Printing reveals everything.
- Search, sortable and filterable tables, a "changed only" filter, changed cells tinted with the original value under them (struck through, also in the tooltip, or shown instead with "Show original values"), colored type pills.
- A "Randomization log" section with the text log in a searchable monospace viewer, a jump-to-section menu and a download button, so one file has everything.
- Works on a phone, can be used with the keyboard, and has a print stylesheet.
- A link like `guide.html#pk,show,es,light` opens a section, with spoilers shown, in Spanish, in the light theme.

It reveals the whole game, so the program asks before writing it. Do not use it if you are competing against someone.

## Names

Pokemon, moves, abilities, items and places have an English and a Spanish name in a table bundled with the program (`guide-names.json`), built from PokeAPI's open CSV data (species, moves and abilities by their national number, items by the game's own item number per generation, places by the program's location tags).

- If the ROM is in the language you are reading, the guide uses the game's own text. That text is what that game really calls the thing, including its abbreviations ("V. Cortante", "Picotazo Ven") and older names (a Spanish Gen 4 game says "Portazo" where today's Spanish name is "Atizar"). Text the game stores in capitals is shown with normal capitalization, and text with unreadable control codes falls back to the table.
- If the ROM is in the other language, the table name is used. So an English game read in Spanish shows today's official Spanish names, not the abbreviated names a Spanish copy of the game would show.
- If the table has no entry for something (a hacked ROM, an id outside Generations 1 to 7), the ROM's own text is used and the miss is counted. It is never blank.
- Generation 3 numbers its abilities differently (it has the unused Cacophony at 76 and Air Lock at 77); the guide accounts for that.
- Alternate formes ("Wormadam-Plant", "Mega Charizard X", "Alolan Raichu") keep the English suffix in English and use the Spanish form text in Spanish.
- Wild places are named by the program's location tag, not by the ROM's map text, so the same place has the same name in both languages. Official Spanish place names come from the Spanish games where they were available when the table was built (Gen 3 FireRed and LeafGreen, Gen 4, Gen 5) and from PokeAPI. Places with no official Spanish name in the table keep their English proper name with the generic words translated ("Vermilion City" becomes "Ciudad Vermilion").

## Wild Pokemon

One entry per place and one row per Pokemon, however many slots, maps, methods or times of day it appears in.

- A place groups every encounter table of that place: the floors of a cave and the maps of a route are merged. The place name and its position come from the program's location tag.
- Each row has the combined level range and a chip for every way of meeting the Pokemon: the method (grass, surf, each rod, rock smash, headbutt, hordes, swarms, Poke Radar, DexNav, rippling water, SOS calls...), the time of day (Gen 2 and HGSS morning, day, night; Gen 7 day and night) or the season (Gen 5 spring to winter), and the chance. Tables that give the same chance in every season or time of day are written once ("all seasons", "any time of day").
- The chance is the sum of the slot chances of the Pokemon in that table, and it is only written where the slot chances are well established: the 12 slot grass table of Gen 3 to 6, the 5 slot surf table of Gen 3 to 6, the Gen 3 rock smash table, the Gen 1 and Gen 2 land and water tables, and the three Gen 3 rods (old 70/30, good 60/20/20, super 40/40/15/4/1). Everywhere else (Gen 4 and 5 rods and special tables, Gen 7, hordes, swarms) the chip says how many of the table's slots the Pokemon fills ("2 of 5 slots"). When a place merges several maps with the same method, the chance is a range over the maps where the Pokemon appears.
- Gen 4 games with time based encounters append four slots to each grass table that replace some slots depending on the time of day. The program does not know which time each belongs to, so they are shown as "time-of-day slots".
- Gen 5 tables are tagged with their season only when time based encounters were on for the run. With them off, all four seasons are the same.
- Before and after: a Pokemon that was not in that place and method originally is marked "New", a changed level range shows the original under it, and the Pokemon that were there originally and are gone are listed under the card ("Originally here"). "Show original values" shows the original place instead.
- The "Where to find" index is built from the same grouped data and lists the places in game order, followed by static or gift Pokemon and trades.
- The log lists wild encounters the same way (one block per place in the same order, one line per Pokemon per method) instead of one block per encounter table.

## Game order

Nothing in a ROM says when the player meets something, so each section estimates it from the best signal that exists. The order number (`#n`) is shown on every card or row, and the default sort is "game order". The estimates are good for walking through a game, not a guarantee.

| Section | Order | Limits |
|---|---|---|
| Wild places | The program's own story order list of locations for that game (`locationTagsTraverseOrder`, hand made walkthrough order for most games and the in-game Pokedex habitat list for Black 2 and White 2). Gen 1 to 7 all have one. | A place the list does not know goes after the known ones in the order the game stores them (the unnamed Platinum maps). Places visited twice in the story (a city before and after a plot event) appear once, at the first position. Post-game places are marked "Post-game" and are in the list where the walkthrough puts them. |
| Trainers | The highest level of the team in the unmodified game, with placeholder trainers (a lone level 1 Pokemon, named "no data" in some games) last. Gym leaders are forced into gym order (never earlier than the previous gym) and the league (Elite Four, kahunas, champion) after the last gym leader, using the same boss tiers the randomizer uses. | A trainer in a dungeon right before a gym can have a higher level than that gym's leader, and optional trainers sit wherever their level puts them. Rivals follow their level, not their tag number. |
| Static and gift Pokemon | The level in the unmodified game, ties in the order the game stores them. | Levels follow the story closely but not exactly, eggs and post-game legendaries fall where their level puts them. |
| Trades | The order the game stores them. | No level or place is known, so this is not story order. |
| Shops | Shops that grow with your badges first (by badge count), then shops named after a town in that town's position of the wild story order list (a name starting with the town, for example "Slateport Vitamins"), then the rest in the order the game stores them. | Shops whose name has no town the list knows (a department store, "Accumula Secondary") go last. Field items are not listed because the game stores no place for them. |

## Gifts, static Pokemon and trades: location

No handler of the supported games exposes where a static Pokemon, a gift or an in-game trade is (the data carries only the species, the level and the egg flag, and for a trade the requested species, the given species, the nickname and the original trainer name). The guide and the text log therefore take the place from a table kept by this program, `locations.txt` next to the guide page, with one section per game family (Crystal, FireRed and LeafGreen, Emerald, Diamond and Pearl, Platinum, HeartGold and SoulSilver, Black and White, Black 2 and White 2, X and Y, Omega Ruby and Alpha Sapphire, Sun and Moon, Ultra Sun and Ultra Moon).

- The table is matched against the ORIGINAL game by species and level, never by position, so the Spanish games (whose lists are shorter or ordered differently) match as well as the English ones. The place always describes the original event, whatever Pokemon is there now.
- Place names come from the guide's own place table (so a place is named the same way here as in the wild tables, in English and Spanish) plus a few extra names kept in the file. Where the Spanish name is not known for sure the English name is kept, rather than inventing one.
- An entry the table does not have shows a grey general description ("A gift or fixed encounter (place not listed)", "An egg given by someone", "An in-game trade") and is written to the data with `known: false`. The data is `loc: {en, es, known}`.
- Sources: Serebii's per game pages and the pret decompilation projects. Games without a section (Red, Blue, Yellow, Gold, Silver, Ruby, Sapphire) have no table yet and show the general description.
- To add or fix a place, edit `locations.txt` (`S` lines for static, gift and egg Pokemon, `T` lines for trades, `P` lines for extra place names). `GuideLocationsTest` checks the file reads cleanly and `GuideLocationsRomRandomizerTest` prints the coverage of every game in `roms/`.

## Layout notes

Spacing was reviewed in a headless browser at desktop width (1400) and phone width (390), dark and light, in both languages. On a phone the wild and trainer tables turn into one block per row so nothing needs a sideways scroll.
