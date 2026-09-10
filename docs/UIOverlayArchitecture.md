# UI & Overlay Architecture — RetroQuest

**Last updated:** September 2026

## 1. Overview

All game rendering flows through a single `GamePanel` (extends `JPanel`). Overlays are drawn on top of the tile map by calling their `paint(Graphics2D, int W, int H)` methods during `paintComponent()`. No JDialogs are used for in-game UI — everything is rendered in-panel.

---

## 2. Rendering Pipeline (`GamePanel.paintComponent`)

The paint order determines Z-ordering (later = on top):

```
1. Black background fill
2. Animation short-circuits — first active one paints and returns, in this order:
   EndgameCinematic, Epilogue, Credits, ShadowDescent, WarMarch, Ascension, WashAshore,
   DungeonEntry, DungeonDescent, DungeonExit, TownEntry, WaterfallCaveEntry,
   TownExit, WaterfallCaveExit
3. VisionSystem.computeBrightness(game) — float[viewH][viewW] light buffer for this frame
4. Map rendering, one of four paths:
   - TexturedDungeonRenderer  (dungeon, mode TEXTURED)
   - WireframeDungeonRenderer (dungeon, mode WIREFRAME)
   - Top-down dungeon: floor + special sprite + N/W walls per cell, S/E only where the
     neighbour is below VISIBILITY_THRESHOLD, then a per-tile black alpha = (1 - brightness)
   - Overworld/Town: TileRegistry lookup → sprite or editor colour fallback, same alpha pass
5. NPCs (overworld/town only; skipped below NPC_VISIBILITY_THRESHOLD, else darkened to match)
6. Player (sprite or "@" fallback) — skipped entirely in first-person dungeon modes
7. PlayerDeathAnimation, then the teleport star tunnel, then the level-up flash
8. Overlays, painted in this order (later = on top):
   Combat → boss phase transition → CradleChoice → Spellbook → Inventory → Shop → Casino →
   ForgeArena → AbyssalDepths → SkyGames → NatureGames → MemoryGames → WarGames →
   SaveLoad → Dialogue → QuestLog → Help → DivineAudience → Notification → Death (topmost)
```

Note the paint order is **not** the input order — the spellbook paints over combat but also
outranks it for input, while the death overlay paints last yet sits below the endgame animations
for input.

---

## 3. Overlay Pattern

Every overlay follows the same contract:

### Required API

| Method | Signature | Purpose |
|--------|-----------|---------|
| `paint` | `paint(Graphics2D g, int W, int H)` | Render overlay on GamePanel |
| `isActive` | `boolean isActive()` | Check if overlay should render/accept input |
| `handleKey` | `handleKey(KeyEvent e)` | Process keyboard input |
| `handleClick` | `handleClick(int mx, int my)` | Process mouse input |
| `open` / `start` | varies | Activate overlay |
| `close` | `close()` | Deactivate overlay |

### Common Implementation Pattern

```java
public class FooOverlay {
    private boolean active;

    public void open() { active = true; entryTime = System.currentTimeMillis(); }
    public void close() { active = false; }
    public boolean isActive() { return active; }

    public void paint(Graphics2D g, int W, int H) {
        // 1. Dim background: black fill with ~180 alpha
        // 2. Calculate card dimensions: min(maxW, W-40) × min(maxH, H-40)
        // 3. Slide-in animation: ease-out cubic from bottom/right
        // 4. Draw card: gradient fill, rounded corners, colored border
        // 5. Layout sections: title bar, body, key bar
        // 6. Paint sections
    }

    public void handleKey(KeyEvent e) {
        // Route by VK_* constants
    }
}
```

### Visual Conventions

All overlays share a CRT-inspired retro terminal theme:

| Element | Convention |
|---------|-----------|
| Background | RGB(6, 8, 14) — near-black navy |
| Panel | RGB(10, 14, 22) — slightly lighter |
| Border | 1.5pt stroke, accent color varies by overlay |
| Fonts | All Monospaced via `Fonts.mono/monoBold/monoItalic(pt)`, which multiply pt by `DisplayScale.SCALE` |
| Geometry | Every pixel dimension goes through `OverlayTheme.scaled(int)` — never a raw literal |
| Accent colors | PHOSPHOR green, AMBER, CYAN, DANGER red |
| Dim text | RGB(80, 105, 135) |
| Bright text | RGB(200, 222, 255) |
| Entry animation | Ease-out cubic slide-in (280–350ms) |
| Dim layer | Black with alpha 180–190 |

**Resolved:** Color and font constants are now centralized in `OverlayTheme.java`. Per-overlay overrides (e.g., CombatOverlay, CasinoOverlay, DeathOverlay) remain local where values differ from the shared palette.

---

## 4. Input Routing

### Keyboard

`Retroquest.handleKey()` walks a hardcoded if-chain and returns at the first match. The real order,
highest priority first:

| # | Check | Behaviour |
|---|---|---|
| 1 | Credits animation | any key skips |
| 2 | Epilogue animation | advances when waiting for a key |
| 3 | Endgame cinematic | advances when waiting for a key |
| 4 | Cradle choice overlay | `handleCradleChoiceKey` |
| 5 | Wash-ashore animation | advances when waiting for a key |
| 6 | Divine audience overlay | `handleDivineAudienceKey` |
| 7 | Dialogue | `handleDialogueKey` |
| 8 | Quest log | `handleQuestLogKey` |
| 9 | Death animation / town entry animation | swallowed |
| 10 | Town exit animation | swallowed |
| 11 | Death overlay | `handleDeathKey` |
| 12 | **MessageLog prompt** | `messageLog.handleKey` (see below) |
| 13 | Spellbook | `handleSpellbookKey` |
| 14 | Combat | `handleCombatKey` |
| 15 | Save/Load | `handleSaveLoadKey` |
| 16 | Inventory | `handleInventoryKey` |
| 17 | Shop | `handleShopKey` |
| 18 | Casino | `handleCasinoKey` |
| 19 | Forge arena | `handleArenaKey` |
| 20 | Abyssal depths | `handleDepthsKey` |
| 21 | Sky games | `handleSkyGamesKey` |
| 22 | Nature games | `handleNatureGamesKey` |
| 23 | Memory games | `handleMemoryGamesKey` |
| 24 | War games | `handleWarGamesKey` |
| 25 | Help | `handleHelpKey` |
| 26 | First-person dungeon (`inDungeon && dungeonViewState.isFirstPerson()`) | `handleWireframeKey` — a separate, smaller key map |
| 27 | Normal game input | movement, `E` examine, `A` attack, `C` spell, `T` talk, `I`, `L`, `S`, `F1`, `Q`, `+`/`-` zoom |

Two things worth noting: the spellbook outranks combat (so it can be opened *during* combat), and
the endgame animations sit above everything including the death overlay.

Only the **first matching branch** receives input — everything below it is blocked.

### `MessageLog` as a prompt consumer

`MessageLog` is not just a scrolling log — it is the game's modal prompt surface, and it sits at
priority 12, above every gameplay overlay:

- `prompt(question, yesAction, noAction)` — Y/N.
- `prompt(question, labels[], actions[])` — up to 4 numbered options; a `null` action is a no-op
  (used for "Cancel").
- `promptInput(question, Consumer<String>)` — free-text entry; the consumer receives the trimmed
  string, or `null` if the player pressed Escape.
- `isPromptActive()` returns `inPrompt || inTextInput`.

While a prompt is active, `messageLog.handleKey(e)` consumes the key and returns before any
overlay or movement handler sees it. Code that fires automatically on a step must therefore guard
on `isPromptActive()` — `DungeonController.handleDungeonSpecial()` and `Retroquest.wireframeMove()`
both do, to avoid stacking a second prompt on top of a live one.

### Mouse

`GamePanel` has its own MouseAdapter if-chain, and it is **not** the keyboard order:

```
DivineAudience → Combat → Dialogue → QuestLog → Death → SaveLoad → Shop → Casino →
ForgeArena → AbyssalDepths → SkyGames → NatureGames → MemoryGames → WarGames →
Inventory → Spellbook
```

Clicks go to the first active overlay's `handleClick(mx, my)`. Two consequences: mouse input does
not respect a live `MessageLog` prompt, and the spellbook is *last* for the mouse while it is
*first* for the keyboard. Keep the two chains in mind when adding an overlay.

### Repaint Timing

- Most overlays rely on `repaint()` after each key/click.
- `CombatOverlay` has a dedicated 16ms repaint timer (60fps) for smooth animations.
- Other overlays with animations (entry slide-in) may use a short timer.

---

## 5. Overlay Inventory

The `overlay` package holds 46 files: **19 `*Overlay` classes**, **23 `*Game` mini-games** (all
extending the shared `MiniGame` base), and 4 support classes (`CombatEngine`, `SpellEffectRenderer`,
`OverlayTheme`, `BossFightCoordinator`).

Card dimensions are no longer fixed pixel values — every overlay sizes itself through
`OverlayTheme.scaled()`, so the numbers change with `DisplayScale.SCALE`.

| Overlay | Purpose |
|---------|---------|
| `CombatOverlay` | Turn-based combat; hosts `SpellEffectRenderer` (18 effect types) and `BossFightCoordinator` phase transitions |
| `SpellbookOverlay` | Spell selection, in combat and on the map |
| `InventoryOverlay` | Item management, equip, use, drop |
| `ShopOverlay` | Buy/sell against per-NPC stock; CHA adjusts both sides |
| `CasinoOverlay` | Casino mini-game hub |
| `ForgeArenaOverlay` | Bellorak mini-game hub |
| `AbyssalDepthsOverlay` | Thalorax mini-game hub |
| `SkyGamesOverlay` | Zephyrion mini-game hub |
| `NatureGamesOverlay` | Sylvandar mini-game hub |
| `MemoryGamesOverlay` | Umbryn mini-game hub |
| `WarGamesOverlay` | War-themed mini-game hub |
| `SaveLoadOverlay` | 8 manual slots plus an autosave slot in load mode |
| `DialogueOverlay` | Branching NPC dialogue with conditions and actions |
| `QuestLogOverlay` | Active and completed quests |
| `RevelationOverlay` | Generic "something speaks to you" card — title, subtitle, accent colour, typewriter body, and a `Footer` that fades in only once the speaker has finished. Sizes itself from the whole message rather than the revealed prefix, so the box does not grow under the reader as it types. Used by the bottom-of-dungeon bosses (see `docs/CombatSystem.md` §14) |
| `DivineAudienceOverlay` | Departing-god farewell, shown once per island on first forward portal use. The card is a `RevelationOverlay`; what remains here is the god-specific part — accent colour, the message chosen from the player's history, and the standing-with-the-seven panel supplied as a `Footer` |
| `CradleChoiceOverlay` | Endgame choice at the Cradle of Shards |
| `HelpOverlay` | F1 key reference |
| `NotificationOverlay` | Transient toasts |
| `DeathOverlay` | Death sequence (painted last, always topmost) |

---

## 6. Animation Classes

Full-screen animations that **replace** the tile map during playback (early return in `paintComponent`):

The `animation` package holds 16 classes — the 15 animations below plus `AnimationGuard`, which keeps at most one cinematic live per completion callback (slots are named after the callback, so a town entry and a waterfall-cave entry can never both be running and fire `finishTownEntry()` twice):

| Animation | Trigger |
|-----------|---------|
| `WashAshoreAnimation` | New game intro (`Retroquest` startup) |
| `TownEntryAnimation` | Entering a town — `GamePanel.startTownEntryAnimation(townName)` |
| `TownExitAnimation` | Leaving a town |
| `WaterfallCaveEntryAnimation` | Entering a town `isCaveTown()` classifies as a cave — substituted for `TownEntryAnimation` |
| `WaterfallCaveExitAnimation` | Leaving such a town |
| `DungeonEntryAnimation` | `enterDungeon()` and `enterAuthoredDungeon()` |
| `DungeonDescentAnimation` | Descending to a deeper dungeon level |
| `DungeonExitAnimation` | Leaving a dungeon |
| `ShadowDescentAnimation` | Portal thalorax → umbryn (replaces the generic star tunnel) |
| `WarMarchAnimation` | Portal umbryn → bellorak |
| `AscensionAnimation` | Portal out of bellorak to anywhere other than umbryn |
| `PlayerDeathAnimation` | Player dies (painted over the map rather than replacing it) |
| `EndgameCinematicAnimation` | Endgame trigger in `DungeonController` |
| `EpilogueAnimation` | After the endgame choice; carries ending type, title and champion god |
| `CreditsAnimation` | Started by `EpilogueAnimation` when it finishes |

All animation classes follow `isActive()` / `paint(Graphics2D, int W, int H)` and are started via a
`GamePanel.start*` method. The generic teleport star tunnel is not an animation class — it is
`GamePanel.drawTeleportAnimation()` on a 4200 ms timer in `NavigationController`.

---

## 7. Supporting UI Components

### `StatsPanel.java`

Side panel (not an overlay) showing player stats, HP/XP bars, food, gold, equipment, and active buffs. Updated via `refresh()` after combat, leveling, equip changes.

### `MessageLog.java`

Bottom message area displaying game messages (combat log, NPC dialogue, system messages). Messages logged via `game.log(text, color)`. Scrollable history.

---

## 8. Adding a New Overlay

To add a new overlay:

1. Create `FooOverlay.java` implementing `paint()`, `isActive()`, `handleKey()`, `handleClick()`, `open()`, `close()`.
2. Add a field in `GamePanel` and instantiate in constructor.
3. Add `paint()` call in `paintComponent()` at the desired Z-order.
4. Add input routing in `GamePanel`'s mouse listener and expose `handleFooKey()`.
5. Add keyboard routing in `Retroquest.handleKey()` at the correct priority.
6. Use shared utilities from `OverlayTheme` (`paintKeyBadges()`, `wordWrap()`, `entryEase()`, `paintScrollIndicators()`) for consistent styling.

---

## 9. Known Gaps
1. **No overlay stacking** — only one overlay can be active at a time (except spellbook during combat). No general overlay stack/manager.
2. **Hardcoded input priority** — overlay priority is determined by if/else ordering in `handleKey()` and `mouseClicked()`. No priority system.
3. **Three orders to keep in sync** — keyboard chain, mouse chain and paint chain are three separate hand-maintained lists in two files, and they currently disagree (see §4). Adding an overlay means editing all three.
4. **Mouse ignores prompts** — the mouse chain has no `messageLog.isPromptActive()` guard, so a click can reach an overlay while a modal prompt is open.
