@echo off
REM Dev launcher: compiles if needed, resolves the dependency classpath for THIS
REM machine, then runs the RetroForge editor from target\classes.
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

set /p CP=<target\cp.txt
java -cp "target\classes;%CP%" io.cannonforge.retroquest.editor.RetroForge %*
endlocal
