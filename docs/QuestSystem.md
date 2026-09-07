# RetroQuest Quest System - Requirements Specification

**Document Version:** 7.1
**Date:** September 2026
**Scope:** Complete quest system specification covering all quest types, editor, registry, NPC integration, and game hooks.

---

## 1. Overview

The quest system is a lightweight, editor-driven, JSON-persisted feature tightly integrated with NPCs, player progression, overworld/dungeon movement, and combat.

**Core Features:**
- 5 quest types with type-specific progress text and turn-in logic.
- Template (registry/editor) vs. runtime instances (randomized repeatable quests).
- NPC-linked quest offering, TALK auto-progress, COLLECT turn-in (inventory consumption), generalized DELIVER turn-in, and generalized EXPLORE hooks.
- KILL quest progression on combat victory via `CombatOverlay`.
- Quest prerequisites (`prereqQuestId`) for quest chaining.
- Full in-game quest log overlay.
- Auto-rewards on completion (gold/XP/item/spell) + JOptionPane popup.
- Starter quest `rising_tide` auto-granted on new character.

**Philosophy:** Retro, low-overhead, content-driven. Everything flows through `NpcController.talk()` and specific dungeon/combat hooks.

---

## 2. Functional Requirements

### 2.1 Data Model (`Quest.java`)
- **Fields (18):** `id`, `title`, `description`, `type`, `target`, `requiredAmount`, `goldReward`, `xpReward`, `itemRewardId`, `spellRewardId`, `giverName`, `deliverItemId`, `prereqQuestId`, `progress`, `status`, `repeatable`, `minAmount`, `maxAmount`. There is **no multi-objective model** — one `target`, one `requiredAmount`, one int `progress`, and `isComplete()` is simply `progress >= requiredAmount`.
- **Type Enum:** `KILL`, `COLLECT`, `TALK`, `DELIVER`, `EXPLORE`.
- **Status Enum:** `AVAILABLE`, `IN_PROGRESS`, `COMPLETED`.
- **Runtime State:** `progress`, `status`, `repeatable`, `minAmount`/`maxAmount`.
- **Key Methods:**
  - `createInstance()` — clones + randomizes KILL/COLLECT amounts if minAmount > 0 + replaces `{amount}` token in description. Copies `deliverItemId` and `prereqQuestId`.
  - `progress(int amount)`, `isComplete()`, `getProgressText()`.
  - `getItemReward()` — fetches item from `ItemRegistry`.
  - `getSpellRewardId()` — returns spell name to learn on completion.
  - Builder methods: `withSpellRewardId()`, `withItemRewardId()`, `withDeliverItemId()`.
  - `getPrereqQuestId()` / `setPrereqQuestId(String id)` — prerequisite quest ID.

### 2.2 Registry & Persistence (`QuestRegistry.java`)
- Static templates in `data/quests.json` — **66 quests** today. Path is `System.getProperty("user.dir") + "/data/quests.json"`. A file that exists but will not parse leaves the registry `loadFailed` and saves are refused.
- `createDefaultQuests()` auto-creates `wolf_menace` (repeatable) and `potion_delivery` if file is empty.
- Full CRUD via editor: `getById()`, `addQuest()`, `updateQuest()`, `removeQuest()`.

### 2.3 Quest Editor (`QuestEditorDialog.java`)
- CRT-styled editor with dynamic target (text vs. item dropdown).
- Description field is positioned directly below Title for easy editing.
- **Giver NPC dropdown** (`giverNpcCombo`) — selects the NPC who handles quest turn-in. Populated from all NPCs in town/overworld `.rfmap` files. Maps to `Quest.giverName`.
- Supports repeatable + min/max amount ranges.
- Spell reward dropdown (`spellRewardCombo`) with the 37 spells organized by level (1-6).
- Item reward dropdown (`itemRewardCombo`) with all items from `ItemRegistry`, formatted as `"Name  (id)"`.
- Deliver item dropdown (`deliverItemCombo`) — visible only when type is DELIVER.
- Prerequisite quest dropdown (`prereqCombo`) — selects which quest must be completed before this quest can be offered.
- Quest list is sorted alphabetically by title.
- `progress` and `status` fields are no longer saved to `quests.json` (they are per-player instance state, not template data).

### 2.4 Quest Log UI (`QuestLogOverlay.java`)
- In-game overlay with keyboard/mouse navigation, sections, icons, and "TURN IN" indicator.
- Opened with **L** key.

### 2.5 Player Quest Management (`Player.java`)
- `activeQuests` / `completedQuests`.
- `addQuest()`, `progressQuest(Quest.Type, String target, int amount)` — central dispatcher (matches type + target, case-insensitive).
- `addItemAndProgress(Item)` — adds item to inventory and auto-progresses COLLECT quests. Used by combat loot, dungeon pickups (altar, throne, chest, cube), and shop purchases.
- `completeQuest()` — rewards flow:
  1. Grants gold via `addGold()`.
  2. Grants XP via `addXP()` (may trigger level-up).
  3. Item reward via `getItemReward()` + `ItemRegistry` fallback.
  4. Spell reward via `learnSpell(spellRewardId)`.
  - `KEY`-type item rewards are routed to `Player.keyRing` rather than the inventory, so they cannot be lost, dropped or sold.

### 2.6 NPC Integration (`NPC.java` + `NpcController.java`)
- `questId` + `questCompleteDialog`.
- `talk()` flow:
  1. Post-completion dialog (if NPC's quest already completed).
  2. Shop/inn dispatch.
  3. Turn-in checks (in order): `checkForDeliverTurnIn()`, `checkForCollectTurnIn()`, `checkForKillQuestTurnIn()`.
  4. `giveQuests()` (creates instance via `createInstance()`) — only if no turn-in occurred.
  5. `progressQuest(TALK, npc.name, 1)`.
- `giveQuests()` checks `prereqQuestId` — skips offering if prerequisite not yet completed.
- `checkForDeliverTurnIn()` — matches any DELIVER quest where `target` equals the NPC name, looks up item via `deliverItemId`, counts inventory, prompts turn-in.
- `checkForCollectTurnIn()` — consumes items from inventory + completes COLLECT quests.
- `checkForKillQuestTurnIn()` — handles KILL, EXPLORE, and TALK quest turn-ins.

### 2.7 Game Integration (`Retroquest.java`)
- `startGame()` auto-adds `"rising_tide"` on new character (if no quests exist).

### 2.8 Dungeon & Encounter Integration
- **KILL:** Progressed in `CombatOverlay` on monster defeat — `player.progressQuest(Quest.Type.KILL, monster.getName(), 1)`.
- **EXPLORE:** Hooks:
  - `DungeonController.enterDungeon()` — `progressQuest(EXPLORE, "Dungeon Level " + depth, 1)`.
  - `DungeonController.goDownDungeonLevel()` — `progressQuest(EXPLORE, "Dungeon Level " + depth, 1)`.
  - `NavigationController.finishTownEntry()` — `progressQuest(EXPLORE, townName, 1)`.
  - `DungeonController.handleCorruptedShrine()` — `progressQuest(EXPLORE, "corrupted_shrine", 1)`.

### 2.9 Full Quest Lifecycle
1. NPC -> `giveQuests()` -> prereq check -> instance added (or auto-granted like `rising_tide`).
2. Progress via type-specific hooks (KILL on combat, TALK on conversation, COLLECT on pickup, EXPLORE on area entry, DELIVER on NPC turn-in).
3. Completion -> rewards (gold/XP/item/spell) + popup.
4. Next talk with giver -> custom dialog.

---

## 3. Non-Functional Requirements
- Performance: O(n) on tiny lists.
- Persistence: JSON templates + Gson player save.
- Retro UI: Monospaced CRT everywhere.
- Extensibility: Editor-driven.

---

## 4. Remaining Gaps (Low Priority)

- **Timed quests / failure states** — no mechanism to fail a quest currently exists.
- **Multi-objective support** — quest chaining via `prereqQuestId` covers most use cases.

---

## 5. Acceptance Criteria
- KILL quests complete on monster defeat.
- All default quests (including `rising_tide`) load and work.
- Spell rewards granted on quest completion.
- Repeatable randomization + `{amount}` token description system.
- NPC talk cycle (offer -> progress -> turn-in -> complete) functions end-to-end.
- Quest log overlay accurate.
- No console warnings for missing IDs.
- COLLECT auto-progress on item pickup.
- Item reward picker in quest editor.
- General DELIVER turn-in logic.
- General EXPLORE progression hooks.
- Quest prerequisites (chain quests).

---

## 6. Current Quest Registry

`data/quests.json` holds **66 quests**. The full list is no longer duplicated here — read the file,
or the Quest Editor. 52 of the 66 declare a `giverName`; only `alchemists_stone` and
`pilgrims_journey` use `prereqQuestId`.

### The island key chain

Seven `KEY`-type rewards gate the seven islands, one per island, in this order:

| # | Key | Granted by |
|---|---|---|
| 1 | `key_of_tides` | Dreamwake Caverns depth-3 boss (Corrupted Moon Guardian), both moral outcomes — `DungeonController.handleCorruptedShrine()` |
| 2 | `key_of_embers` | Quest `pyralis_crucible_trial`, giver Emberpriest Cael (Forge Keep); also handed out by his `crucible_done` dialogue node |
| 3 | `key_of_gales` | Quest `zephyrion_trial` ("Trial of the Gale"), giver **Reva** in Stormspire, gated behind both `stormspire_trial_reva` and `stormspire_trial_milo` |
| 4 | `key_of_roots` | The Amber Sage (Amber Grove), `verdant_done` dialogue node, after `sylvandar_verdant_trial` |
| 5 | `key_of_depths` | Quest `thalorax_trial` plus all four choices in `handlePressureTempleTrial()` |
| 6 | `key_of_echoes` | Archive of Tears trial, all three choices — `handleArchiveOfTearsTrial()` |
| 7 | `key_of_iron` | Bellorak arena trial (Seraphine), all three choices — `handleBellorakArenaTrial()` |

Keys 1, 5, 6 and 7 are granted from `DungeonController` code rather than quest data, and key 4 from
an `.rfmap` dialogue action, so `quests.json` alone does not describe the chain.

---

**Note:** Quest descriptions in `quests.json` use `{amount}` tokens (e.g., "Kill {amount} wolves.") which are resolved at runtime by `createInstance()`.
