# RetroQuest

An 80s-style tile-based fantasy CRPG for the desktop, written in Java/Swing — and the RetroForge
editor you build it with.

You wash ashore on Lirandel with nothing, and work your way across seven islands
(Lirandel → Pyralis → Zephyrion → Sylvandar → Thalorax → Umbryn → Bellorak), each sealed behind a
key earned on the island before it. Along the way: towns, shops, inns, branching NPC dialogue,
quests, casinos and island mini-games, turn-based combat with a 37-spell book, and dungeons that
change how they look as you go — Lirandel's are a torch-lit overhead grid, Sylvandar's are
texture-mapped first-person, and everything else is vector wireframe.

All artwork is 32×32 sprites and all audio is synthesized at runtime — there are no sound files.

## Running

Requires a JDK on PATH (this checkout is built with Amazon Corretto 26; the pom targets Java 17 bytecode).

```bash
# Windows
run-retroquest.bat        # the game
run-retroforge.bat        # the RetroForge editor
```

Or directly, **from the repository root** (all `data/` and `saves/` paths are resolved relative to
the working directory):

```bash
export JAVA_HOME="C:/Program Files/Amazon Corretto/jdk26.0.0_35"
mvn compile
java -cp "target/classes;$(< cp.txt)" io.cannonforge.retroquest.core.Retroquest
```

Saves live in `saves/`; game content (maps, tiles, items, monsters, quests) lives in `data/` and is
editable in RetroForge.

## Building Installers

### Windows

```bash
export JAVA_HOME="C:/Program Files/Amazon Corretto/jdk26.0.0_35"
mvn clean package verify
```

This produces `target/RetroQuest-0.9.0-windows.zip` containing:
- `RetroQuest-Setup.exe` — native installer (no Java required)
- `jre/` — bundled Java runtime

Distribute the zip. The end user extracts it and runs `RetroQuest-Setup.exe`.

## Documentation

`docs/Architecture.md` is the starting point and links to every subsystem document.

## Known Issues

### Desktop icons missing when OneDrive is enabled (Windows)

If your Desktop folder is managed by OneDrive (via "Known Folder Move" / Desktop backup), shortcut icons and file icons may appear blank or as generic white squares. This is caused by OneDrive's file-on-demand placeholders preventing Windows from reading the icon files.

**Fix:** Open OneDrive Settings → Sync and backup → Manage backup → toggle **Desktop** off. Icons should reappear once files are stored locally again.
