# Randomization log

The text file saved next to the randomized game (`<game>.log`) and shown in the guide's "Randomization log" section. It is plain text, written in English, and always has the same shape:

1. **Header.** Program and version, game, language of the game, the random seed, the date (DD/MM/YYYY) and the settings string. With the same game file, settings string and seed you get exactly the same game again.
2. **Table of contents.** Every section has a four letter code in curly braces. Search (Ctrl+F) for the code, for example `{PKST}`, to jump to it.
3. **Settings summary `{SETT}`.** Only the options that differ from the defaults, in plain words, including the extras of this program (shiny chance, Locke rules, egg box, shop items, stat floor and edits, custom evolutions, fully evolved by level, evolution level scale, mega finale, battle style rules).
4. **Warnings and notes `{WARN}`.** Everything the program repaired, skipped or wants you to know, collected in one place. The same notes also appear inline in their own section.
5. **Overview `{OVRD}`**, then one section per thing that was randomized (base stats, types and abilities, evolutions, movesets, trainers, wild Pokemon, TMs, tutors, shops...), then statistics and diagnostics.

The Pokemon table (base stats, types and abilities) is written from the same randomized game as the HTML guide, and a test checks that both agree for every Pokemon.
