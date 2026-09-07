#!/usr/bin/env bash
# Dev launcher for the RetroForge editor: compiles if needed, resolves the
# dependency classpath for THIS machine, then runs from target/classes.
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

java -cp "target/classes${SEP}$(cat "$CP_FILE")" io.cannonforge.retroquest.editor.RetroForge "$@"
