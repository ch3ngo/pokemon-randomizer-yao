@echo off
REM Expansion not relevant yet, just good practice to have it here
setlocal enabledelayedexpansion

set "TIMESTAMP=%date% %time%"
echo ===== Launching the Universal Pokémon Randomizer FVX at %TIMESTAMP% ===== >> console_output.log
echo ===== Launching the Universal Pokémon Randomizer FVX at %TIMESTAMP% =====

REM Change to the directory of the launcher
cd /d "%~dp0"
if errorlevel 1 (
    echo Error: cannot change the working directory to the launcher folder: %~dp0 >> console_output.log
    echo Error: cannot change the working directory to the launcher folder: %~dp0
    exit /b 1
)

REM Modified (packaging restructure, 11/10/2026): this launcher now lives inside app\ itself,
REM directly alongside the jar, data\ and bundled java\, so no further cd is needed to reach them.
set "JAVA=java\bin\java.exe"

REM Detect embedded Java missing
if not exist "%JAVA%" (
    echo Error: embedded Java runtime not found at: %JAVA% >> console_output.log
    echo Error: embedded Java runtime not found at: %JAVA%
    exit /b 1
)

REM Detect Java not runnable
"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
    echo Error: embedded Java runtime exists but cannot be executed: %JAVA% >> console_output.log
    echo Error: embedded Java runtime exists but cannot be executed: %JAVA%
    exit /b 1
)

REM Launch FVX
"java\bin\java.exe" -Xmx4608M -jar PokemonRandomizerYAO.jar please-use-the-launcher
REM Honestly not sure if all bad wrong things (crashes) give an errorlevel 1
if errorlevel 1 (
    echo Error: something went wrong with the Randomizer / Java. >> console_output.log
    echo Error: something went wrong with the Randomizer / Java.
)
REM Since we can't mirror the Java launch output to console_output.log, put a wait here.
REM Otherwise, users won't see the message(s) for when things go wrong.
REM (For the curious the powershell command Tee-Object could be used, but then we get encoding issues)
echo Press any key to exit...
pause
