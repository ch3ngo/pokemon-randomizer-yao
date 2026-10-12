#!/bin/bash
# Starts the web interface of Pokemon Randomizer YAO from a source checkout.
# It builds the program if needed, stages a folder (dist/) with the program and its data, and opens the interface in
# your browser. Needs a JDK (Java 21 or newer) on the PATH. Keep this window open while you use the interface.
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. This script is for source checkouts and needs a JDK (Java 21 or newer) on the PATH."
  echo "If you do not want to install Java, use a release archive instead: it bundles its own Java (web-launcher.sh / web-launcher.bat)."
  exit 1
fi
JAR="random/build/libs/PokemonRandomizerYAO.jar"
if [ ! -f "$JAR" ] || [ -n "$(find random/src romio/src utils/src -newer "$JAR" -type f 2>/dev/null | head -1)" ]; then
  echo "Building the program (first run or sources changed). This can take several minutes; please wait..."
  ./gradlew :random:assemble --console=plain -q || { echo "The build failed."; exit 1; }
fi
mkdir -p dist
cp "$JAR" dist/PokemonRandomizerYAO.jar
rm -rf dist/data && cp -r random/src/main/resources/data dist/data
cd dist || exit 1
exec java -Xmx6g -jar PokemonRandomizerYAO.jar web "$@"
