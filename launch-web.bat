@echo off
REM Starts the web interface of Pokemon Randomizer YAO from a source checkout.
REM It builds the program if needed, stages a folder (dist) with the program and its data, and opens the interface in
REM your browser. Needs a JDK (Java 21 or newer) on the PATH. Keep this window open while you use the interface.
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
    echo Java was not found. This script is for source checkouts and needs a JDK ^(Java 21 or newer^) on the PATH.
    echo If you do not want to install Java, use a release archive instead: it bundles its own Java ^(web-launcher.bat^).
    pause
    exit /b 1
)
set "JAR=random\build\libs\PokemonRandomizerYAO.jar"
if not exist "%JAR%" (
    echo Building the program ^(first run, this can take several minutes; please wait^)...
    call gradlew.bat :random:assemble --console=plain -q
    if errorlevel 1 (
        echo The build failed.
        pause
        exit /b 1
    )
)
if not exist dist mkdir dist
copy /y "%JAR%" dist\PokemonRandomizerYAO.jar >nul
if exist dist\data rmdir /s /q dist\data
xcopy /e /i /q /y random\src\main\resources\data dist\data >nul
cd dist
java -Xmx6g -jar PokemonRandomizerYAO.jar web %*
echo.
echo The program stopped.
pause
