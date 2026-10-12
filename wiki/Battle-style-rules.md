# Battle style rules

Give chosen groups of trainers a chance to fight as a **double**, **triple** or **rotation** battle, for example "10% of gym leaders are double battles". They run after the normal "Trainer battle style" option, and trainers the rules do not pick keep whatever that option (or the game) gave them.

## How it works

Each rule has a group, a style and a chance from 1 to 100 percent (up to 8 rules):

- Groups: gym leaders, league (Elite Four, Champion, Uber), evil-team bosses, other bosses (rivals and strong admins), everyone. The groups are the same as the ones of the [Mega finale](Mega-finale).
- For every trainer, the rules are checked in the order listed. The first rule that matches the trainer **and** whose own dice roll succeeds sets the style. The dice are seeded, so the same seed gives the same result.
- A team too small for the style is filled up with copies of its first Pokemon (2 for doubles, 3 for triples and rotation), exactly like the normal battle style option does.
- Trainers that are always part of a multi battle, and the first rival fights, are never touched.

## Games

Gen 3 to 7 can store doubles. Triple and rotation battles exist only in Gen 5 and Gen 6. Gen 1 and 2 have no double battles, so the rules are skipped there. A rule the game cannot use is skipped with a note in the log, never an error.

## Command line

`--battle-style-rule <group>:<style>:<chance>`, repeatable, for example `--battle-style-rule gymLeaders:double:10 --battle-style-rule league:double:25`. Groups: `gymLeaders`, `league`, `evilBosses`, `otherBosses`, `everyone`. Styles: `double`, `triple`, `rotation`.

## Caution

A double battle with a Pokemon team that was balanced for singles can be much harder or easier. Long back-to-back fights are not specially handled yet.
