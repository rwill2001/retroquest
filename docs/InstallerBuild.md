# RetroQuest — Windows Installer Build Guide

## Overview

The installer is a single self-contained JAR (`retroquest-installer.jar`) that bundles:

- The game fat JAR (`retroquest-1.0.0-fat.jar`)
- The `data/` directory (JSON registries + map files)
- A minimal bundled JRE produced by `jlink`

Running the installer JAR on Windows extracts all of the above to a user-chosen directory,
writes `retroquest.bat` / `retroforge.bat` launchers, and creates Desktop shortcuts.

---

## Prerequisites

| Tool    | Version       | Notes                                      |
|---------|---------------|--------------------------------------------|
| JDK     | 17+ (Corretto)| Must include `jmods/` — full JDK, not JRE. Built here with Corretto 26 |
| Maven   | 3.9+          | `mvn` on PATH                              |
| OS      | Windows       | `jlink` output and launchers are Windows-only |

In Git Bash, `JAVA_HOME` is not inherited from Windows, so set it before building:

```bash
export JAVA_HOME="C:/Program Files/Amazon Corretto/jdk26.0.0_35"
```

---

## Build Steps

### Single command (recommended)

```bash
export JAVA_HOME="C:/Program Files/Amazon Corretto/jdk26.0.0_35"
mvn -Pdist package
```

The `-Pdist` profile is **required**. jlink needs a full JDK with `jmods/` and Launch4j only
emits a Windows executable, so wiring those into the default lifecycle made a plain `mvn package`
fail on any other machine. Without `-Pdist` the build still succeeds — it just stops after the fat
JAR and silently produces no installer. The output is:

```
target/retroquest-installer.jar
```

---

### What `mvn package` does (stage by stage)

#### Stage 1 — Compile all sources

`maven-compiler-plugin` compiles both source roots:
- `src/main/java/` — game + editor classes
- `src/installer/java/` — `Installer.java` (added via `build-helper-maven-plugin`)

#### Stage 2 — Game fat JAR (`maven-shade-plugin`)

Produces `target/retroquest-1.0.0-fat.jar`.

Contains all game and editor classes plus the Gson dependency, with `Retroquest` as
`Main-Class`. The installer class is explicitly excluded from this JAR.

#### Stage 3 — Bundled JRE (`jlink`)

`maven-antrun-plugin` first deletes any existing `target/jre/` so `jlink` can recreate it
cleanly. Then `exec-maven-plugin` runs:

```
jlink
  --module-path  %JAVA_HOME%/jmods
  --add-modules  java.desktop,java.logging,java.sql,java.prefs
  --no-header-files
  --no-man-pages
  --compress=2
  --output       target/jre
```

This produces a minimal ~50 MB JRE containing only the modules the game needs.
`java.desktop` covers Swing/AWT; `java.logging` covers `java.util.logging`; `java.sql` is pulled
in by Gson's `SqlDateTypeAdapter`; `java.prefs` is used by `SoundManager`. Verify the set with
`jdeps --print-module-deps --ignore-missing-deps target/retroquest-1.0.0-fat.jar`.

> If you add features that require additional JDK modules (e.g. `java.net.http`,
> `java.xml`), add them to the `--add-modules` list in `pom.xml`.

#### Stage 4 — Installer JAR (`maven-assembly-plugin`)

`maven-assembly-plugin` uses `src/main/assembly/installer.xml` to assemble
`target/retroquest-installer.jar` from:

| Source                                  | Destination inside JAR       |
|-----------------------------------------|------------------------------|
| `Installer.class` (compiled)            | `io/cannonforge/installer/` |
| `target/retroquest-1.0.0-fat.jar`| `installer/`                 |
| `data/items.json`, `monsters.json`, etc.| `installer/data/`            |
| `data/overworlds/*.rfmap`               | `installer/data/overworlds/` |
| `data/towns/*.rfmap`                    | `installer/data/towns/`      |
| `target/jre/**`                         | `installer/jre/`             |

`Installer` is set as `Main-Class` in the manifest.

---

## Running the Installer

On any Windows machine (no Java required — the bundled JRE is inside the JAR itself,
extracted during install):

```bat
java -jar retroquest-installer.jar
```

Or double-click `retroquest-installer.jar` if `.jar` files are associated with Java.

The installer GUI will:
1. Ask for an install directory (default: `%LOCALAPPDATA%\RetroQuest`)
2. Extract all files
3. Write `retroquest.bat`, `retroforge.bat`, and `uninstall.bat`
4. Create `RetroQuest.lnk` and `RetroForge.lnk` on the Desktop (via PowerShell)
5. Optionally launch the game immediately

> The installer requires Java to *run* it — but once installed, the game uses the bundled
> JRE and has no external Java dependency.

---

## Output Files

After `mvn package`, the relevant artifacts in `target/` are:

```
target/
  retroquest-1.0.0.jar          # thin JAR (not used for distribution)
  retroquest-1.0.0-fat.jar      # game fat JAR (bundled into installer)
  retroquest-installer.jar             # distributable installer  <-- ship this
  jre/                                 # bundled JRE (bundled into installer)
```

Only `retroquest-installer.jar` needs to be distributed.

---

## Troubleshooting

**`jlink` fails: `Error: Module jmods not found`**
- Your `JAVA_HOME` points to a JRE instead of a full JDK. Make sure it points to the
  Corretto JDK which includes `jmods/`.

**Assembly fails: `retroquest-1.0.0-fat.jar` not found**
- The jlink and assembly stages are in the `dist` profile. Run `mvn -Pdist package` — a plain
  `mvn package` stops after the fat JAR and never builds the installer.

**Shortcuts not created after install**
- The shortcut step uses PowerShell + `WScript.Shell`. If PowerShell execution policy
  blocks it, the installer logs a warning and continues — the game still works, just
  without Desktop shortcuts. Run `retroquest.bat` directly from the install folder.

**`target/jre` already exists error on rebuild**
- `maven-antrun-plugin` deletes `target/jre` at the start of each `package` run.
  If a previous build was interrupted, delete `target/jre` manually and re-run.
