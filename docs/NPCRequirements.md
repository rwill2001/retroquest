# RetroQuest NPC System - Requirements Specification & Gap Analysis

**Document Version:** 3.0
**Date:** March 09, 2026
**Scope:** Complete analysis of the NPC system covering all source classes:
`NPC.java`, `NpcController.java`, `NPCEditorDialog.java`, `DialogueTreeEditor.java`, `ShopOverlay.java`, `CasinoOverlay.java`, `DialogueOverlay.java`, `DialogueTree.java`, `DialogueNode.java`, `DialogueChoice.java`, `DialogueAction.java`, `DialogueCondition.java`, `Town.java`, `OverworldManager.java`, `Player.java`, `Quest.java`, `QuestRegistry.java`.

---

## 1. Overview

The NPC system manages all non-player characters in towns and the overworld. It is the primary interface for:

- Branching dialogue trees with conditional choices and side-effects
- Quest giving and turn-in (DELIVER, COLLECT, KILL, TALK, EXPLORE)
- Shopping (per-NPC inventories with global fallback)
- Resting at inns
- Casino mini-games (Game of Chance, Blackjack, Wizard's Dice)
- TALK quest progression

NPCs are placed via the RetroForge map editor and persisted in `.rfmap` JSON files. Runtime conversation flow is centralized in `NpcController.talk()` (triggered by **T** key). The dialogue tree system (when present on an NPC) takes full priority over the legacy single-string dialog path.

**Core Philosophy:** Every NPC is either a service provider (shop/inn/casino) or a quest hub. Conversation is the central gameplay loop.

---

## 2. Functional Requirements

### 2.1 Data Model (`NPC.java`)

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Auto-generated via timestamp if null |
| `name` | String | Display name |
| `spriteName` | String | Sprite path (e.g. `"npcs/shopkeeper"`); defaults to `"townsman.png"` |
| `type` | NPC.Type enum | Determines service behavior (see below) |
| `defaultDialog` | String | Legacy single-string dialog (used when no dialogue tree) |
| `questCompleteDialog` | String | Shown after linked quest is completed |
| `questId` | String | Links to a quest template in `QuestRegistry` |
| `shopItemIds` | List\<String\> | Item IDs stocked by this shopkeeper |
| `dialogueTree` | DialogueTree | Branching conversation graph (null = legacy mode) |
| `x`, `y` | int | Map position |

**NPC.Type enum:**
| Type | Runtime Behavior |
|------|-----------------|
| `SHOPKEEPER` | Opens `ShopOverlay` with NPC's `shopItemIds` |
| `CASINO` | Opens `CasinoOverlay` |
| `INNKEEPER` | Prompts for 15g rest (full heal + food) |
| `QUESTGIVER` | No special action (dialog + quest flow only) |
| `TOWNSFOLK` | No special action (dialog only) |
| `GUARD` | No special action (unused — see Gaps) |
| `TRAINER` | No special action (unused — see Gaps) |
| `BLACKSMITH` | No special action (unused — see Gaps) |
| `HEALER` | No special action (unused — see Gaps) |

**Constructors:**
- No-arg (Gson deserialization)
- Full 7-param: `(id, name, spriteName, type, defaultDialog, x, y)` — used by RetroForge
- Backward-compat 5-param: `(name, type, x, y, dialog)` — used by `Town.java` legacy defaults

**Notes:**
- `spriteName` format is inconsistent across `.rfmap` files — some use `"npcs/shopkeeper"`, others `"orc.png"`. `GamePanel` normalizes at render time. Minor tech debt.
- `getDialog()` is `@Deprecated`; all call sites replaced with `getDefaultDialog()`. Definition remains as cleanup candidate.

### 2.2 Runtime Controller (`NpcController.java`)

**Entry Point:** `talk()` — finds nearest NPC within 1 tile via `findNearbyNPC()` (checks town NPCs first, then overworld).

**Conversation Flow (strict priority order):**

1. **Dialogue tree check (highest priority):** If the NPC has a non-null `dialogueTree` with a valid start node, opens `DialogueOverlay` and **returns immediately** — all legacy behavior below is skipped. Service actions (shop, casino, inn, heal) are triggered via `DialogueAction` nodes within the tree instead.

2. **Legacy path (no dialogue tree):**
   1. If linked quest is completed and `questCompleteDialog` is non-empty → show it and exit.
   2. Log `defaultDialog` to message log.
   3. Type-specific dispatch:

      | Type | Action |
      |------|--------|
      | `SHOPKEEPER` | `game.gamePanel.openShop(shopItemIds)` + repaint timer |
      | `CASINO` | `game.gamePanel.openCasino()` + repaint timer |
      | `INNKEEPER` | `visitInn()` — 15g prompt, full heal + spell refresh + 300 food |
      | All others | No special action |

   4. Turn-in chain: `checkForDeliverTurnIn()` → `checkForCollectTurnIn()` → `checkForKillQuestTurnIn()`. Short-circuits on first success. Each handles at most one quest per conversation. `checkForCollectTurnIn()` iterates backward through inventory.
   5. `giveQuests()` — offers linked quest if eligible (`Quest.createInstance()`). **Skipped** if a turn-in occurred in step 4. Checks `template.isRepeatable()` and `template.getPrereqQuestId()`.
   6. Always: `player.progressQuest(Quest.Type.TALK, npc.getName(), 1)`

**Other Methods:**
- `findNearbyNPC()` — O(n) search across town + overworld NPC lists (1-tile Manhattan distance)
- `castMapSpell(spell)` — handles non-combat spells: Cure/Heal, Teleport (to `lastSafeTownName` or Stonehaven), Detect Magic, Wish, Resurrection
- `teleportToTown()` — looks up entrance position from `overworldManager.getTownEntrances()`; rejects if already in town

### 2.3 Dialogue Tree System

The dialogue tree system provides branching, condition-gated conversations with side-effects. It fully replaces the legacy single-string dialog for any NPC that has a `dialogueTree` set.

#### 2.3.1 Data Model

**DialogueTree** — top-level container:
- `startNodeId`: String — entry point node ID
- `nodes`: Map\<String, DialogueNode\> — O(1) lookup by ID

**DialogueNode** — a single conversation screen:
- `id`: String — unique key (e.g. `"greeting"`, `"open_shop"`)
- `text`: String — NPC speech (supports `\n` line breaks)
- `choices`: List\<DialogueChoice\> — player response options
- `actions`: List\<DialogueAction\> — side-effects executed on node entry

**DialogueChoice** — a player response option:
- `label`: String — display text
- `nextNodeId`: String — target node (null = end conversation)
- `conditions`: List\<DialogueCondition\> — ALL must pass; choice is hidden if any fail

**DialogueAction** — a side-effect triggered when entering a node:
- `type`: Type enum (see table below)
- `target`: String — context-dependent (quest ID, item ID, flag key, etc.)
- `value`: String — context-dependent (flag value, log type name)
- `amount`: int — context-dependent (gold, quantity, HP)

**DialogueCondition** — a gate on choice visibility:
- `type`: Type enum (see table below)
- `target`: String — what to check
- `value`: String — expected value (for FLAG_EQUALS)
- `amount`: int — threshold
- `negate`: boolean — inverts the result

#### 2.3.2 Action Types

| Action Type | Effect | Parameters |
|-------------|--------|------------|
| `GIVE_QUEST` | Adds quest to player | target = quest ID |
| `GIVE_ITEM` | Adds item to inventory | target = item ID, amount (default 1) |
| `GIVE_GOLD` | Adds gold (negative = take) | amount |
| `TAKE_ITEM` | Removes item from inventory | target = item ID, amount |
| `SET_FLAG` | Sets persistent player flag | target = key, value (default `"true"`) |
| `CLEAR_FLAG` | Removes player flag | target = key |
| `OPEN_SHOP` | Closes dialogue, opens shop | Uses NPC's `shopItemIds` |
| `OPEN_CASINO` | Closes dialogue, opens casino | No parameters |
| `HEAL_PLAYER` | Heals HP (+ spell refresh if full) | amount (0 = full heal) |
| `TEACH_SPELL` | Teaches named spell | target = spell name |
| `LOG_MESSAGE` | Writes to message log | target = text, value = MessageLog.Type name |
| `CLOSE_DIALOGUE` | Ends conversation immediately | No parameters |

#### 2.3.3 Condition Types

| Condition Type | Checks | Parameters |
|----------------|--------|------------|
| `QUEST_COMPLETE` | Quest in completed list | target = quest ID |
| `QUEST_ACTIVE` | Quest in active list | target = quest ID |
| `HAS_ITEM` | Item count in inventory | target = item ID, amount = min count |
| `HAS_GOLD` | Player gold | amount = minimum |
| `PLAYER_LEVEL` | Player level | amount = minimum |
| `PLAYER_STAT` | Named stat value | target = `"str"`/`"dex"`/`"con"`/`"int"`/`"wis"`/`"cha"`, amount = min |
| `FLAG_EQUALS` | Flag has specific value | target = key, value = expected |
| `FLAG_SET` | Flag exists (non-null) | target = key |
| `FLAG_NOT_SET` | Flag absent (null) | target = key |

All conditions support `negate: true` to invert the check. Choice visibility requires ALL conditions to pass.

#### 2.3.4 Overlay UI (`DialogueOverlay.java`)

- Full-screen card overlay with slide-in animation (280ms ease-out)
- NPC portrait (64x64) rendered from sprite
- Typewriter text effect (40 chars/sec, blinking cursor)
- Any key skips to full text reveal
- Node actions execute once on entry (guarded by `actionsExecuted` flag)
- If all choices are filtered out by conditions, a default "Farewell." option is added

**Input:**
| Key | Action |
|-----|--------|
| Up / K | Navigate choices up |
| Down / J | Navigate choices down |
| Enter | Select highlighted choice |
| 1-9 | Quick-select by number |
| Escape | Close dialogue |
| Mouse click | Skip text or click choice |

#### 2.3.5 Player Flags (`Player.java`)

Persistent key-value store used by dialogue conditions and actions:
- `flags: Map<String, String>` — persisted in save files, lazy-initialized for old save compatibility
- `getFlag(key)`, `setFlag(key, value)`, `clearFlag(key)`, `hasFlag(key)`

Flags enable cross-conversation state: tracking whether a player has heard a story, made a choice, or unlocked a secret dialogue branch.

#### 2.3.6 Serialization

Dialogue trees are stored inline in NPC JSON within `.rfmap` files (Gson auto-serializes). Example:

```json
{
  "id": "npc_1772259759494",
  "name": "Crusty the shopkeep",
  "spriteName": "npcs/shopkeeper",
  "type": "SHOPKEEPER",
  "dialogueTree": {
    "startNodeId": "greeting",
    "nodes": {
      "greeting": {
        "id": "greeting",
        "text": "Eh? Another customer...",
        "choices": [
          { "label": "Show me what you've got.", "nextNodeId": "open_shop", "conditions": [] },
          { "label": "Farewell.", "nextNodeId": null, "conditions": [] }
        ],
        "actions": []
      },
      "open_shop": {
        "id": "open_shop",
        "text": "Feast your eyes on my wares...",
        "choices": [],
        "actions": [
          { "type": "OPEN_SHOP", "target": "", "value": "", "amount": 0 }
        ]
      }
    }
  }
}
```

### 2.4 Shop System (`ShopOverlay.java`)

- Per-NPC inventory: `open(shopItemIds)` filters stock to the NPC's item list. Falls back to all shop items from `ItemRegistry.getAllShopItems()` if the list is empty.
- **BUY tab:** Items with prices from `ItemRegistry`. Checks gold and inventory space.
- **SELL tab:** Player inventory at 50% price. Uses actual inventory slot indices (not visual row indices).
- **Input:** Tab (switch mode), Up/Down or K/J (navigate), Enter (buy/sell), Escape (close). Mouse supported.
- Feedback via `MessageLog`; stats panel refreshed on transaction.

### 2.5 Casino System (`CasinoOverlay.java`)

Opened via `CASINO` NPC type or `OPEN_CASINO` dialogue action. Three mini-games:

| Game | Description |
|------|-------------|
| **Game of Chance** | 20-slot roulette wheel (red/blue/gold), 6 bet types, 5-second animated spin |
| **Blackjack** | Standard 52-card deck, hit/stand/doubledown, dealer AI, card pixel art |
| **Wizard's Dice** | Craps-style two-die game, come-out + point phases, bouncing dice animation |

All graphics rendered via `Graphics2D` — no external images.

### 2.6 NPC Editor (`NPCEditorDialog.java`)

Full CRT-styled editor invoked from RetroForge:

- **Left panel:** Scrollable NPC list with New/Delete buttons
- **Right panel:** Name, sprite dropdown (live 160x160 preview), type combo, quest dropdown (from `QuestRegistry`), options checkbox
- **Dialog areas:** Two side-by-side text areas for normal and post-quest dialog
- **Dialogue tree button:** "Edit Dialogue Tree..." opens `DialogueTreeEditor` for the selected NPC
- **Shop items editor:** Modal with available/selected item lists and arrow buttons
- **Sprite editor:** Embedded button opens `ImageEditor`
- **Save:** Ctrl+S or button; pushes undo state to canvas
- Supports both town and overworld maps

### 2.6.1 Dialogue Tree Editor (`DialogueTreeEditor.java`)

Visual editor for creating and editing NPC dialogue trees. Accessible from:
- The "Edit Dialogue Tree..." button in `NPCEditorDialog`
- The EDITORS menu in `RetroForge` (prompts to select an NPC from the current map)

**Data Flow:**
1. **Open:** Deep-copies the NPC's `DialogueTree` via Gson round-trip. If null, creates a template with one "greeting" node and a "Farewell" choice.
2. **Edit:** All mutations happen on the copy.
3. **OK - Apply:** Replaces `npc.dialogueTree` with the edited copy.
4. **Cancel:** Discards the copy; NPC unchanged.

**Layout:**
- **Left panel — Tree Navigator:** JTree showing the Tree > Node > Choices/Actions hierarchy. Start node marked with `[*]`. Nodes shown in green, choice/action folders in cyan, choices in white, actions in amber. Buttons: +Node, Delete, Set Start.
- **Left panel — Flow Preview:** Custom-painted BFS graph showing node connections with boxes and arrows. Start node highlighted in green.
- **Right panel — CardLayout** with 5 cards:

| Card | Fields | Description |
|------|--------|-------------|
| **Tree Properties** | Start node dropdown, Validate Tree button, validation results area | Overview and validation |
| **Node Editor** | ID field, multiline text area, choices list, actions list | Edit a single dialogue node. Choices/actions have +/Edit/Delete buttons. Choices support reorder (up/down). |
| **Choice Editor** | Label field, next node dropdown (includes "(End Conversation)"), conditions list | Edit a single choice. Back-to-node button. |
| **Action Editor** | Type combo, target/value/amount fields (visibility changes per type) | Edit a single action. Dynamic fields per action type. |
| **Condition Editor** | Type combo, target/value/amount fields (visibility changes per type), negate checkbox | Edit a single condition. Dynamic fields per condition type. |

**Action type field mapping:**

| Action Type | Visible Fields |
|-------------|---------------|
| `GIVE_QUEST` | target (quest ID) |
| `GIVE_ITEM` / `TAKE_ITEM` | target (item ID) + amount |
| `GIVE_GOLD` | amount |
| `SET_FLAG` | target (key) + value |
| `CLEAR_FLAG` | target (key) |
| `HEAL_PLAYER` | amount (0 = full) |
| `TEACH_SPELL` | target (spell name) |
| `LOG_MESSAGE` | target (message text) + value (log type) |
| `OPEN_SHOP` / `OPEN_CASINO` / `CLOSE_DIALOGUE` | (none) |

**Condition type field mapping:**

| Condition Type | Visible Fields |
|----------------|---------------|
| `QUEST_COMPLETE` / `QUEST_ACTIVE` | target (quest ID) |
| `HAS_ITEM` | target (item ID) + amount |
| `HAS_GOLD` / `PLAYER_LEVEL` | amount |
| `PLAYER_STAT` | target (stat name) + amount |
| `FLAG_EQUALS` | target (key) + value |
| `FLAG_SET` / `FLAG_NOT_SET` | target (key) |

All condition types also show the negate checkbox.

**Node rename propagation:** Changing a node's ID automatically updates all `nextNodeId` references across all choices, and updates `startNodeId` if it pointed to the old ID.

**Validation checks (triggered by Validate Tree button):**
1. Start node ID is set and exists in the nodes map
2. All `nextNodeId` references point to valid nodes (or null for end)
3. No orphaned nodes (unreachable from start via BFS)
4. No empty node text or empty choice labels
5. Results shown in Tree Properties card with color-coded output (green = OK, amber = warnings, red = errors)

### 2.7 Map Loading (`Town.java` + `OverworldManager.java`)

- Towns: `data/towns/<name>.rfmap` → `MapData` (tiles + npcs list)
- Overworld: `data/overworlds/<name>.rfmap` → same `MapData`
- New towns/overworlds auto-generate defaults (including basic NPCs)
- NPCs stored in `MapData.npcs` and loaded into `Town.npcs` or `OverworldManager.getNpcs()`

### 2.8 Quest Integration

- **QuestRegistry** loads from `data/quests.json`; creates 2 defaults if missing (`wolf_menace`, `potion_delivery`)
- **Quest types:** KILL, COLLECT, TALK, DELIVER, EXPLORE
- **Quest statuses:** AVAILABLE, IN_PROGRESS, COMPLETED
- **Repeatable quests** supported via `isRepeatable()` flag
- **Prerequisite chains** via `prereqQuestId` — checked before offering
- **Randomized amounts** via `minAmount`/`maxAmount` — resolved in `createInstance()`
- **Rewards:** gold, XP, item (`itemRewardId`), spell (`spellRewardId`)
- Quests can be given via legacy `questId` on NPC or via `GIVE_QUEST` dialogue action

### 2.9 Integration Points

- **Input:** `Retroquest.handleKey()` → `NpcController.talk()` on T key
- **Player state:** `progressQuest(TALK, ...)` on every conversation; flags persist across saves
- **Overlays:** Shop, Casino, and Dialogue overlays all follow `paint(Graphics2D g, int W, int H)` pattern
- **Repaint timer:** Service overlays (`SHOPKEEPER`, `CASINO`) start `overlayRepaintTimer` after opening

---

## 3. Non-Functional Requirements

- **Performance:** O(n) nearest-NPC search (NPC lists are small per-map)
- **Persistence:** Gson serialization in `.rfmap` files; player flags and quests in save files
- **Retro Aesthetic:** Monospaced CRT UI in editor, overlays, and dialogs
- **Extensibility:** Editor-driven — new NPCs, quests, sprites, and dialogue trees require zero code changes. New dialogue action/condition types require only enum + handler additions.
- **Save Compatibility:** Player flags map is lazy-initialized (null-safe for old saves without flags)

---

## 4. Gaps & Remaining Work

### 4.1 Strong Areas (Fully Working)

- Dialogue tree system with conditions, actions, branching, and typewriter UI
- **Dialogue tree editor** in RetroForge — visual tree navigator, form-based node/choice/action/condition editing, validation, flow preview
- Per-NPC shop inventories via `shopItemIds` (with global fallback)
- Quest integration (offering, DELIVER/COLLECT/KILL turn-in chain, repeatable, prerequisites)
- Player flags for cross-conversation state
- Service NPC actions via dialogue trees (OPEN_SHOP, OPEN_CASINO, HEAL_PLAYER)
- Casino with three complete mini-games
- NPC editor with live sprite preview, quest linking, shop item management, and dialogue tree editor integration
- Town/overworld NPC persistence

### 4.2 Remaining Gaps

- **Unused NPC Types:**
  `TRAINER`, `HEALER`, `BLACKSMITH`, and `GUARD` have no type-specific behavior in `talk()`. They function identically to `TOWNSFOLK`. If dialogue trees handle all their behavior, these enum values are cosmetic labels only.

- **Single Quest per NPC (Legacy Path):**
  The `questId` field links one quest. Multiple quests per NPC require either a dialogue tree with multiple `GIVE_QUEST` actions or changing `questId` to a list.

- **Hard-Coded Inn Cost:**
  `visitInn()` uses a fixed 15g cost. Not configurable per-NPC or per-town.

- **No Load-Time Dialogue Validation:**
  The dialogue tree editor provides on-demand validation (orphan nodes, broken refs, empty text), but there is no automatic validation on `.rfmap` load. Invalid trees still fail silently at runtime.

- **Deprecated `getDialog()` Still Defined:**
  No remaining call sites, but the method body remains in `NPC.java`.

### 4.3 Known Bugs

1. ~~**SoundManager missing keys:**~~ **Fixed.** `altarChime` and `throne` are registered in `soundMap`.

### 4.4 Future Enhancements (Prioritized)

**High Priority:**
1. **Type-Specific Handlers** — if dialogue trees don't cover all cases, add handlers for HEALER (paid heal), TRAINER (stat training), BLACKSMITH (repair/upgrade), GUARD (bounty quests).

**Medium Priority:**
2. **Multiple Quests per NPC** — change `questId` → `List<String> questIds` for NPCs using the legacy path. Dialogue tree NPCs can already offer multiple quests via separate `GIVE_QUEST` action nodes.
3. **NPC Schedules / Day-Night** — `dayX/dayY`, `nightX/nightY` fields; `findNearbyNPC()` checks game time.
4. **Load-Time Dialogue Validation** — warn on `.rfmap` load if a `nextNodeId` references a missing node.
5. **Dialogue Tree Editor Enhancements** — registry-backed combo dropdowns for quest/item/spell targets (currently free-text fields); drag-and-drop node reordering; visual graph editor alternative to JTree navigator.

**Low Priority:**
6. **NPC Relationships / Favor** — per-NPC reputation score affecting prices, dialog branches, and quest availability.
7. **Dynamic Vendor Stock** — shops restock on timer or town re-entry; randomized items.
8. **Escort / Companion NPCs** — flag an NPC as escortable with follow AI.

---

## 5. Acceptance Criteria

- NPC Editor can create/edit/delete/save NPCs with sprite preview and quest linking.
- Dialogue Tree Editor can create/edit/delete nodes, choices, actions, and conditions via form-based UI.
- Dialogue Tree Editor validates trees for orphan nodes, broken references, and empty text/labels.
- Dialogue Tree Editor uses safe deep-copy workflow (cancel discards changes, OK applies).
- Dialogue Tree Editor is accessible from NPC Editor button and RetroForge EDITORS menu.
- NPCs with dialogue trees open `DialogueOverlay` with branching choices, conditions, and actions.
- NPCs without dialogue trees fall back to legacy behavior (type dispatch + quest flow).
- `OPEN_SHOP` action opens `ShopOverlay` with the NPC's `shopItemIds`.
- `OPEN_CASINO` action opens `CasinoOverlay`.
- `HEAL_PLAYER` action restores HP and refreshes spell slots.
- `GIVE_QUEST`, `GIVE_ITEM`, `GIVE_GOLD`, `TAKE_ITEM` actions modify player state correctly.
- `SET_FLAG` / `CLEAR_FLAG` actions persist across saves.
- All condition types correctly gate choice visibility.
- DELIVER, COLLECT, and KILL quest turn-ins work via legacy NPC path.
- Towns and overworlds load NPCs correctly from `.rfmap` files.
- Player flags are null-safe for old saves.

---

## 6. Content Status

**NPCs with dialogue trees (as of v3.0):**
- Moonhaven: 5 NPCs (including Lucky Len / Casino)
- Stonehaven: 3 NPCs (including Crusty / Shopkeeper)
- Lirandel overworld: Kael

**End of Document**
