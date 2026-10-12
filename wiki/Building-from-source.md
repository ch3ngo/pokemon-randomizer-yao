# Building from source

You need a full JDK 25 (with `javac`) and Git. Gradle is included.

```bash
git clone https://github.com/ch3ngo/pokemon-randomizer-yao.git
cd pokemon-randomizer-yao
./gradlew build
./launch-web.sh        # or launch-web.bat on Windows: builds and opens the web interface
```

| Command | What it does |
|---|---|
| `./gradlew :random:assemble` | builds the jar `random/build/libs/UPR-FVX.jar` |
| `./gradlew test` | runs the tests that do not need game files |
| `./gradlew :random:createReleaseZips` | builds the release archives (needs internet) |
| `./gradlew romio:testROMs random:testROMs` | tests that load real games from `roms/` (ignored by Git) |

The program reads its `data/` folder from the folder it starts in. A bare jar must be started from a folder that has a copy of `random/src/main/resources/data` (`launch-web` does this for you in `dist/`).

## Layout

```
random/   randomizer logic, settings, command line, classic and web interfaces
romio/    reading and writing games, one handler per generation
utils/    shared utilities
docs/     the original project site sources
wiki/     these pages
```
