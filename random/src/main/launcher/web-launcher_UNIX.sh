#!/bin/bash

# pipes all logging to also go to console_output.log
exec 1> >(tee -a console_output.log >&1)
exec 2> >(tee -a console_output.log >&2)
printf '%s\n' "===== Launching Pokemon Randomizer YAO (web interface) at $(date) =====" >&1

DIR=$(dirname "$0")
cd "$DIR" || {
  printf '%s\n' "Error: cannot change the working directory to that of the launcher: $DIR" >&2
  exit 1
}

# Modified (packaging restructure, 11/10/2026): program files (jar, data/, bundled java/)
# now live under app/ next to this launcher, instead of in the zip root.
JAVA="./app/java/bin/java"

# Detect embedded Java missing
if [ ! -f "$JAVA" ]; then
  printf '%s\n' "Error: embedded Java runtime not found at: $JAVA" >&2
  exit 1
fi

# Detect embedded Java / system architecture mismatch
ARCH=$(file -b "$JAVA")
if printf '%s' "$ARCH" | grep -q "x86-64"; then
    JAVA_ARCH="x86"
elif printf '%s' "$ARCH" | grep -q "aarch64"; then
    JAVA_ARCH="ARM"
else
    JAVA_ARCH="unknown"
fi

# Detect system architecture
SYS_ARCH=$(uname -m)
case "$SYS_ARCH" in
    x86_64) SYS_ARCH="x86" ;;
    aarch64|arm64) SYS_ARCH="ARM" ;;
esac

if [ "$JAVA_ARCH" != "$SYS_ARCH" ]; then
    printf '%s\n' "Error: embedded Java architecture ($JAVA_ARCH) does not match system architecture ($SYS_ARCH)." >&2
    printf '%s\n' "Hint: there are UPR FVX downloads for both x86 and ARM. Make sure you downloaded the right version." >&2
    exit 1
fi

# Detect noexec mount
FS=$(df -P "$DIR" | awk 'NR==2 {print $6}')

if grep " $FS " /proc/mounts | grep -q noexec; then
    printf '%s\n' "Error: UPR FVX is located on a filesystem mounted with noexec: $FS" >&2
    printf '%s\n' "Hint: Move UPR FVX to a directory that allows execution, perhaps your home folder." >&2
    exit 1
fi

# Set executable flag
chmod +x "$JAVA"

# Detect it still not being executable, for mystery reasons
if [ ! -x "$JAVA" ]; then
  printf '%s\n' "Error: embedded Java runtime is not executable: $JAVA" >&2
  exit 1
fi

printf '%s\n' "The program opens in your web browser. Keep this window open while you use it; close it to stop." >&1

# Switch into app/ so the program's working directory is where its data/ folder lives
# (modified, 11/10/2026, packaging restructure).
cd app || {
  printf '%s\n' "Error: cannot find the app subfolder next to this launcher." >&2
  exit 1
}

exec "./java/bin/java" -Xmx6g -jar PokemonRandomizerYAO.jar web
