@echo off
REM Dev launcher: compiles if needed, resolves the dependency classpath for THIS
REM machine, then runs the game from target\classes.
setlocal
cd /d "%~dp0"

REM Fall back to whatever JDK is installed rather than a pinned version.
if not exist "%JAVA_HOME%\bin\javac.exe" (
    for /d %%d in ("C:\Program Files\Amazon Corretto\jdk*") do set "JAVA_HOME=%%~fd"
)

if not exist target\classes (
    echo Compiling...
    call mvn -q compile || exit /b 1
)

REM Generated per machine - never checked in.
if not exist target\cp.txt (
    echo Resolving dependency classpath...
    call mvn -q dependency:build-classpath -Dmdep.outputFile=target\cp.txt || exit /b 1
)

REM UI scale: pass a factor as the first argument, or set RETROQUEST_SCALE.
REM   run-retroquest.bat 1.5     larger window
REM   run-retroquest.bat fit     largest the display can show
REM Omit both and the game picks from the screen resolution.
set "SCALE_OPT="
if not "%RETROQUEST_SCALE%"=="" set "SCALE_OPT=-Dretroquest.uiScale=%RETROQUEST_SCALE%"
if not "%~1"=="" (
    set "SCALE_OPT=-Dretroquest.uiScale=%~1"
    shift
)

set /p CP=<target\cp.txt
java %SCALE_OPT% -cp "target\classes;%CP%" io.cannonforge.retroquest.core.Retroquest %*
endlocal
