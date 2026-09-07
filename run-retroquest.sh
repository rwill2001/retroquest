#!/usr/bin/env bash
# Dev launcher: compiles if needed, resolves the dependency classpath for THIS
# machine, then runs the game from target/classes.
set -e
cd "$(dirname "$0")"

# Maven needs JAVA_HOME, and Git Bash does not inherit the Windows one. Pick the
# newest JDK we can find rather than pinning a version that goes stale.
if [ ! -x "$JAVA_HOME/bin/javac" ] && [ ! -x "$JAVA_HOME/bin/javac.exe" ]; then
    for d in "/c/Program Files/Amazon Corretto"/jdk* \
             "/c/Program Files/Eclipse Adoptium"/jdk* \
             "/c/Program Files/Java"/jdk* \
             /usr/lib/jvm/*; do
        if [ -x "$d/bin/javac" ] || [ -x "$d/bin/javac.exe" ]; then JAVA_HOME="$d"; fi
    done
    if [ -n "$JAVA_HOME" ]; then export JAVA_HOME; fi
fi

if [ ! -d target/classes ]; then
    echo "Compiling..."
    mvn -q compile
fi

# Generated per machine — never checked in. Refreshed whenever pom.xml is newer.
CP_FILE=target/cp.txt
if [ ! -s "$CP_FILE" ] || [ pom.xml -nt "$CP_FILE" ]; then
    echo "Resolving dependency classpath..."
    mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
fi

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
    *)                    SEP=':' ;;
esac

# UI scale: pass a factor as the first argument, or set RETROQUEST_SCALE.
#   ./run-retroquest.sh 1.5     larger window
#   ./run-retroquest.sh fit     largest the display can show
# Omit both to let the game pick from the screen resolution.
SCALE_OPT=()
if [ -n "$RETROQUEST_SCALE" ]; then SCALE_OPT=(-Dretroquest.uiScale="$RETROQUEST_SCALE"); fi
if [ -n "$1" ]; then SCALE_OPT=(-Dretroquest.uiScale="$1"); shift; fi

java "${SCALE_OPT[@]}" -cp "target/classes${SEP}$(cat "$CP_FILE")" \
     io.cannonforge.retroquest.core.Retroquest "$@"
