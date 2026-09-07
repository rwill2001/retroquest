# RetroQuest — Quest Design Guide

## Table of Contents

1. [Overview](#overview)
2. [Quest Types Reference](#quest-types-reference)
3. [Tools and Editors](#tools-and-editors)
4. [Creating Each Quest Type (Step by Step)](#creating-each-quest-type)
   - [KILL Quest](#kill-quest)
   - [COLLECT Quest](#collect-quest)
   - [DELIVER Quest](#deliver-quest)
   - [TALK Quest](#talk-quest)
   - [EXPLORE Quest](#explore-quest)
5. [Multi-Step Quest Chains](#multi-step-quest-chains)
6. [Dialogue Tree Turn-In Pattern](#dialogue-tree-turn-in-pattern)
7. [Conditions and Actions Reference](#conditions-and-actions-reference)
8. [Field Reference](#field-reference)
9. [Known Constraints](#known-constraints)

---

## Overview

Quests in RetroQuest have two layers:

- **Template** — stored in `data/quests.json`. Defines type, target, rewards, etc. Created in the Quest Editor.
- **Instance** — a live copy given to the player at runtime. Created via `Quest.createInstance()` when a NPC gives the quest.

Every quest flows through the same lifecycle:

```
Template in QuestRegistry
      ↓
NPC gives quest → createInstance() → player.activeQuests
      ↓
Game events update progress (kills, pickups, etc.)
      ↓
Quest becomes complete (progress >= requiredAmount)
      ↓
Player returns to NPC → turn-in → player.completeQuest()
      ↓
Rewards applied, quest moves to player.completedQuests
```

There are **two delivery mechanisms** for giving and completing quests:

| Mechanism | When to use |
|---|---|
| **Legacy NPC** (`questId` field on NPC, no dialogue tree) | Simple quests, one-line interactions |
| **Dialogue Tree** (NPC has a tree; `GIVE_QUEST` / `COMPLETE_QUEST` actions) | Branching narrative, conditional unlock, multi-step chains |

**Critical rule:** If an NPC has a dialogue tree, ALL quest logic (offer, progress feedback, turn-in) must be handled inside that tree. Legacy auto-turn-in methods are bypassed when a dialogue tree is present.

---

## Quest Types Reference

| Type | Progress triggered by | Turn-in method (legacy) | Turn-in method (dialogue tree) |
|---|---|---|---|
| **KILL** | `CombatOverlay` on monster death | `checkForKillQuestTurnIn` | `COMPLETE_QUEST` action (requires `isComplete()`) |
| **COLLECT** | `Player.addItemAndProgress()` on pickup | `checkForCollectTurnIn` (removes items) | `TAKE_ITEM` + `COMPLETE_QUEST` actions |
| **DELIVER** | `Player.addItemAndProgress()` on pickup; decrements on `removeItem()` | `checkForDeliverTurnIn` (counts + removes items) | `TAKE_ITEM` + `COMPLETE_QUEST` actions |
| **TALK** | `NpcController.talk()` fires on any conversation | `checkForKillQuestTurnIn` | Auto: `progressQuest(TALK,npcName,1)` fires before tree; `COMPLETE_QUEST` for reward |
| **EXPLORE** | Manual `progressQuest(EXPLORE,target,1)` call | `checkForKillQuestTurnIn` | `COMPLETE_QUEST` action |

---

## Tools and Editors

### Quest Editor (`RetroForge → Quests`)

Creates and edits quest **templates** in `data/quests.json`.

| Field | Purpose |
|---|---|
| ID | Unique string key (e.g. `wolf_menace`). No spaces. Used everywhere as a reference. |
| Title | Player-facing name shown in Quest Log |
| Description | Supports `{amount}` token which is replaced with the actual required count at instance creation. Shown directly below Title in the editor. |
| Type | KILL / COLLECT / DELIVER / TALK / EXPLORE |
| Target | Monster name (KILL), item name (COLLECT), NPC name (DELIVER/TALK), location key (EXPLORE) |
| Giver NPC | NPC name for quest turn-in. Dropdown populated from all town/overworld maps. Used by `NpcController` auto-turn-in logic for KILL/COLLECT quests. |
| Amount | Fixed required count; overridden by Min/Max if those are set |
| Min / Max | Randomizes amount at instance creation (KILL/COLLECT only — spinner disabled for other types) |
| Repeatable | Whether players can accept again after completing |
| Gold / XP | Rewards applied on completion |
| Item Reward | Optional item given from registry on completion |
| Spell Reward | Optional spell taught on completion |
| Deliver Item | For DELIVER quests: item ID the player must hand over |
| Prereq | Quest ID that must be in `completedQuests` before this can be offered |

**Ctrl+S saves to `data/quests.json` immediately.**

---

### NPC Editor (`RetroForge → select map → double-click NPC`)

Edits an NPC placed on a map. Key fields for quest integration:

| Field | Purpose |
|---|---|
| Quest (legacy) | Attach a single quest ID for simple one-shot offering (no dialogue tree required) |
| Quest Complete Dialog | Text shown after legacy turn-in. Only fires on non-tree NPCs. |
| Edit Dialogue Tree | Opens the Dialogue Tree Editor for this NPC. Button shows node count when a tree exists. |
| Default Dialog | Fallback text if NPC is a TOWNSFOLK and has no tree |

**Note:** The `questId` field and `questCompleteDialog` are ignored at runtime when the NPC has a dialogue tree. All quest logic must live in the tree.

---

### Dialogue Tree Editor (`NPC Editor → Edit Dialogue Tree`)

Creates branching conversations. The editor has a tree navigator (left) and a card panel (right) that swaps between node/choice/action/condition views.

**Left panel — navigator:**
- `+Node` — creates a new node with a default farewell choice
- `Delete` — removes selected node/choice/action/condition
- `Set Start` — marks the selected node as `startNodeId`
- Nodes shown with `(start)` marker; orphan nodes visible but not reachable during play

**Right panel — cards:**

| Card | Shown when |
|---|---|
| Node Editor | A node is selected |
| Choice Editor | A choice is selected under a node |
| Action Editor | An action is selected |
| Condition Editor | A condition is selected on a choice |

**Node fields:** `id` (unique within tree), `text` (supports `\n`; use `"..."` for routing-only nodes that auto-advance), choices list, actions list.

**Choice fields:** `label` (player text), `next node` (dropdown of all node IDs), conditions list. A choice is hidden if ANY condition fails.

**Action fields:** `type` dropdown with dynamic secondary fields.

**Condition fields:** `type` dropdown with dynamic secondary fields; `Negate` checkbox inverts the result.

---

## Creating Each Quest Type

### KILL Quest

**Use when:** Player must defeat a number of specific enemies (or any enemy).

**Step 1 — Create the template (Quest Editor)**

| Field | Value |
|---|---|
| ID | `wolf_menace` |
| Type | KILL |
| Target | `Wolf` — exact monster display name. Use `any` to match every monster. |
| Amount | Fixed count, OR set Min/Max for randomization |
| Description | `Kill {amount} wolves near Stonehaven.` |
| Rewards | Gold, XP, optional item/spell |

**Step 2a — Legacy NPC path (no dialogue tree)**

1. Open NPC Editor for the quest giver.
2. Set **Quest** field to `wolf_menace`.
3. Set **Quest Complete Dialog** to text shown after turn-in.
4. Save the map.

At runtime: NPC offers quest on first talk. Player kills wolves — `CombatOverlay` calls `player.progressQuest(KILL, "Wolf", 1)` per kill. Return to NPC when complete; `checkForKillQuestTurnIn` fires automatically.

**Step 2b — Dialogue tree path**

1. Open NPC Editor → **Edit Dialogue Tree**.
2. In the node that should offer the quest (e.g. `offer_help`), add action **GIVE_QUEST** → target = `wolf_menace`.
3. Create a `quest_complete` node with turn-in text.
4. In `quest_complete`'s actions: add **COMPLETE_QUEST** → target = `wolf_menace` *(fires only when progress ≥ required)*.
5. In the `greeting` node (or any re-entry node), add a choice **"I've dealt with the wolves."** → `quest_complete` with condition **QUEST_ACTIVE** target = `wolf_menace`.
6. Apply and save.

At runtime: `COMPLETE_QUEST` checks `quest.isComplete()` before granting rewards. If the player clicks the turn-in choice early, they see a progress message (e.g. `"Wolf killed: 2/5"`) and the quest stays active.

---

### COLLECT Quest

**Use when:** Player must acquire a certain quantity of a named item (via looting, purchase, or any other means).

**Step 1 — Create the template (Quest Editor)**

| Field | Value |
|---|---|
| ID | `moonbloom_collection` |
| Type | COLLECT |
| Target | `Moonbloom Herb` — must exactly match the item's display **name** (case-insensitive) |
| Amount | How many |
| Description | `Collect {amount} Moonbloom Herbs for Healer Mira.` |
| Item / Spell Reward | Optional |

*Note: The item must be obtainable in the world (monster drop, chest, shop) and have the correct display name. The target is matched by name, not ID.*

**Step 2a — Legacy NPC path**

Same as KILL — set `questId` on the NPC. At turn-in, `checkForCollectTurnIn` counts items by name and removes them from inventory automatically.

**Step 2b — Dialogue tree path**

1. In the offering node: add **GIVE_QUEST** → `moonbloom_collection`.
2. Create a `quest_complete` node with reward text.
3. In `quest_complete` actions:
   - **TAKE_ITEM** → item ID, amount = required count *(removes items from inventory)*
   - **COMPLETE_QUEST** → `moonbloom_collection` *(grants XP/gold/rewards)*
4. Add a choice in the greeting: **"I found the herbs."** → `quest_complete`, condition **QUEST_ACTIVE** `moonbloom_collection`.

*Important: For dialogue tree COLLECT turn-ins, add a **HAS_ITEM** condition on the turn-in choice (item ID + required count) so the choice is only visible when the player actually has enough items. This prevents the player from reaching the `quest_complete` node empty-handed.*

Example condition for the "I found the herbs." choice:
- Type: `QUEST_ACTIVE`, target: `moonbloom_collection`
- Type: `HAS_ITEM`, target: `moonbloom_herb` (item ID), amount: `4`

Both conditions must pass for the choice to appear.

---

### DELIVER Quest

**Use when:** The quest giver wants items delivered to a *different* NPC.

**Step 1 — Create the template (Quest Editor)**

| Field | Value |
|---|---|
| ID | `potion_delivery` |
| Type | DELIVER |
| Target | `Mira the Innkeeper` — exact NPC name of the *recipient* |
| Deliver Item | `healing_potion` — item ID from Item Registry |
| Amount | How many to deliver |
| Description | `Deliver {amount} Healing Potions to Mira.` |

*Note: Min/Max randomization does not apply to DELIVER quests. The spinner is disabled in the Quest Editor.*

**Step 2 — The giver NPC**

The giver must give the player both the quest AND the item(s) to deliver.

**Legacy NPC path:**
- Set `questId` = `potion_delivery` on the giver NPC.
- The giver should have a shop or the items should be placed as loot — the quest assumes the player already has or will obtain the items.

**Dialogue tree path (recommended):**
1. In the offering node, add actions in order:
   - **GIVE_QUEST** → `potion_delivery`
   - **GIVE_ITEM** → `healing_potion`, amount = 3 *(gives the items to deliver)*
2. Ensure **GIVE_QUEST fires before GIVE_ITEM** so the quest is active when the item enters inventory, allowing progress to be counted immediately.

**Step 3 — The recipient NPC**

**Legacy NPC path:**
- The recipient needs NO `questId` set.
- `checkForDeliverTurnIn` checks `quest.target == npc.name` and counts the item in inventory.
- Turn-in is automatic when the player talks to the named recipient while carrying enough items.

**Dialogue tree path:**
- In the recipient's dialogue tree, add a `quest_complete` node with:
  - **TAKE_ITEM** → `healing_potion`, amount = 3
  - **COMPLETE_QUEST** → `potion_delivery`
- Add a turn-in choice in the greeting gated by **QUEST_ACTIVE** `potion_delivery` + **HAS_ITEM** `healing_potion` (3).

**Order dependency:** If GIVE_QUEST fires after GIVE_ITEM in the same node's action list, the item lands in inventory before the quest is active, so `addItemAndProgress` won't count it. The Quest Log will show `0/3`. To avoid this, always list GIVE_QUEST before GIVE_ITEM in the action list.

---

### TALK Quest

**Use when:** Player must speak with a specific NPC.

**Step 1 — Create the template (Quest Editor)**

| Field | Value |
|---|---|
| ID | `find_the_hermit` |
| Type | TALK |
| Target | `Lyren the Pale` — exact NPC name (case-insensitive) |
| Amount | Always `1` (can only talk once to complete) |
| Description | `Find Lyren the Pale in Mooncrest and speak with him.` |

*Min/Max spinners are disabled for TALK quests.*

**Step 2 — Quest giver NPC**

Same as any other type — set `questId` or use GIVE_QUEST in a dialogue tree.

**Step 3 — The target NPC**

Progress fires automatically in `NpcController.talk()` before any dialogue tree opens:

```java
player.progressQuest(Quest.Type.TALK, npc.getName(), 1);
```

This means simply walking up to the target NPC and pressing talk (T) is enough to progress the quest. **The target NPC needs no special setup.**

**Step 4 — Turn-in**

Because TALK auto-completes (1/1 satisfied on first talk), turn-in can happen back at the giver. The giver NPC — whether legacy or dialogue tree — detects `quest.isComplete()` via the normal KILL turn-in path or COMPLETE_QUEST action respectively.

If you want the conversation with the TARGET to also complete the quest immediately (quest giver and target are different NPCs), add a **COMPLETE_QUEST** action in the target NPC's dialogue tree node that fires after the talk is logged.

---

### EXPLORE Quest

**Use when:** Player must reach a location, activate an object, or clear N occurrences of something in the world.

**Step 1 — Create the template (Quest Editor)**

| Field | Value |
|---|---|
| ID | `purge_dreamwake` |
| Type | EXPLORE |
| Target | A key string (e.g. `corrupted_shrine`, `dungeon_level_1`) — must match what the code calls `progressQuest` with |
| Amount | How many times the objective must be triggered |
| Description | `Destroy {amount} Corrupted Shrines in the Dreamwake Caverns.` |

**Step 2 — Wire progress in code**

EXPLORE quests require a `progressQuest` call in `DungeonController` or `NavigationController` at the exact moment the objective is triggered (e.g. player steps on a special tile, activates an altar, enters a room):

```java
game.getPlayer().progressQuest(Quest.Type.EXPLORE, "corrupted_shrine", 1);
```

The `target` string must exactly match the quest template's `target` field (case-insensitive).

**Step 3 — Turn-in**

Same as KILL — return to the giver NPC. Legacy path uses `checkForKillQuestTurnIn`; dialogue tree uses COMPLETE_QUEST.

---

## Multi-Step Quest Chains

Multi-step chains are built from **independent quests linked by `prereqQuestId`**, combined with **dialogue tree conditions** that unlock new conversation branches after earlier quests are done.

### Chain via `prereqQuestId` (Quest Editor)

1. Create quest A: `find_the_hermit` (no prereq).
2. Create quest B: `alchemists_stone`. Set **Prereq** = `find_the_hermit  (find_the_hermit)`.
3. Quest B will not be offered by any NPC until `find_the_hermit` is in `player.completedQuests`.

This works for **both legacy NPCs and dialogue tree NPCs** — the `giveQuests()` method and the GIVE_QUEST dialogue action both check prerequisites before creating an instance.

### Chain via Dialogue Tree Conditions

Use **QUEST_COMPLETE** conditions on choices to gate new dialogue branches:

**Example — three-part chain:**

```
Node: greeting
  Choice "I need to speak with you."
    [condition: QUEST_COMPLETE = "find_the_hermit"]  ← only visible after part 1
    → nextNode: "give_stone_quest"

Node: give_stone_quest
  Actions: GIVE_QUEST "alchemists_stone"

Node: turn_in_stone
  [reached when QUEST_ACTIVE "alchemists_stone" + HAS_ITEM "mooncrest_stone"]
  Actions: TAKE_ITEM "mooncrest_stone", COMPLETE_QUEST "alchemists_stone", GIVE_QUEST "next_quest"
```

This pattern allows a single NPC to manage an entire chain:
- First visit: gives quest A
- Return after A: gives quest B (QUEST_COMPLETE A condition gates the choice)
- Return after B: gives quest C, etc.

### Flag-Based Gating

For finer-grained control (e.g. hide a hint until a specific earlier conversation happened):

1. In the earlier node, add **SET_FLAG** → target = `met_aldric`, value = `true`.
2. In the later choice, add condition **FLAG_SET** → target = `met_aldric`.

Flags persist in the player save file and survive game restarts.

### Multi-Step Chain — Full Example

**Quest chain: "The Alchemist's Request"**

| # | Quest ID | Type | Giver | Turn-in |
|---|---|---|---|---|
| 1 | `find_the_hermit` | TALK | Aldric | Aldric (auto after talk) |
| 2 | `alchemists_stone` | COLLECT | Aldric | Aldric |
| 3 | `unlock_elder_ward` | EXPLORE | Aldric | Aldric |

**Quest Editor setup:**
- `find_the_hermit`: type TALK, target `Lyren the Pale`, amount 1, no prereq
- `alchemists_stone`: type COLLECT, target `Mooncrest Stone`, amount 1, prereq = `find_the_hermit`
- `unlock_elder_ward`: type EXPLORE, target `elder_ward`, amount 1, prereq = `alchemists_stone`

**Aldric's dialogue tree (simplified):**

```
greeting
  → "I need your help."       [no condition]            → offer_intro
  → "About the stone..."      [QUEST_ACTIVE alch_stone]  → stone_active
  → "I have the stone."       [QUEST_ACTIVE alch_stone + HAS_ITEM mooncrest_stone] → stone_complete
  → "The ward is unlocked."   [QUEST_ACTIVE unlock_ward] → ward_complete
  → "Farewell."               [no condition]             → farewell

offer_intro
  Actions: GIVE_QUEST "find_the_hermit"

stone_active
  text: "Have you found it yet?"
  → "Still searching."  → farewell

stone_complete
  Actions: TAKE_ITEM "mooncrest_stone", COMPLETE_QUEST "alchemists_stone"
           GIVE_QUEST "unlock_elder_ward"

ward_complete
  Actions: COMPLETE_QUEST "unlock_elder_ward"
```

Note that `find_the_hermit` is a TALK quest — it auto-completes when the player first speaks to Lyren. Aldric's `GIVE_QUEST "alchemists_stone"` will silently no-op until the prereq is met; once the player has spoken to Lyren, the next time they talk to Aldric the prereq is satisfied and the quest is offered. The dialogue tree should use QUEST_COMPLETE conditions to direct the conversation appropriately rather than relying on silent no-ops.

---

## Dialogue Tree Turn-In Pattern

For any quest given via a dialogue tree, this is the standard turn-in node pattern:

### KILL / EXPLORE turn-in node

```
Node: quest_complete
  text: "[NPC reaction to quest completion]"
  Choices:
    → "Thank you." → farewell  [no condition]
  Actions (executed on entry):
    COMPLETE_QUEST  target = "quest_id"
    LOG_MESSAGE     target = "Narration text for the message log."  value = "INFO"
```

The turn-in choice in the greeting that routes here should carry a **QUEST_ACTIVE** condition.

### COLLECT / DELIVER turn-in node

```
Node: quest_complete
  text: "[NPC reaction]"
  Choices:
    → "Glad I could help." → farewell  [no condition]
  Actions:
    TAKE_ITEM       target = "item_id"   amount = required_count
    COMPLETE_QUEST  target = "quest_id"
    LOG_MESSAGE     target = "Narration."  value = "INFO"
```

The turn-in choice in the greeting should carry both:
- **QUEST_ACTIVE** → `quest_id`
- **HAS_ITEM** → `item_id`, amount = required_count

This ensures the player cannot reach the `quest_complete` node without actually having the items.

### Protecting against early turn-in

`COMPLETE_QUEST` in the dialogue overlay checks `quest.isComplete()` before completing. If a player somehow reaches a turn-in node with an incomplete quest (e.g. missing items), they see a message:

> `"Not yet — Wolf killed: 2/5."`

The quest remains active and no rewards are granted. Always combine QUEST_ACTIVE conditions with HAS_ITEM (for item quests) for clean UX.

---

## Conditions and Actions Reference

### Dialogue Conditions (`DialogueCondition.Type`)

Used on choices to show/hide them based on player state. All conditions on a choice must pass for the choice to appear. Use **Negate** to invert any condition.

| Type | `target` field | `amount` field | Notes |
|---|---|---|---|
| `QUEST_COMPLETE` | Quest ID | — | True if quest is in `completedQuests` |
| `QUEST_ACTIVE` | Quest ID | — | True if quest is in `activeQuests` (any progress level) |
| `HAS_ITEM` | Item **ID** | Minimum count | True if player inventory has ≥ amount of that item |
| `HAS_GOLD` | — | Minimum gold | True if `player.gold ≥ amount` |
| `PLAYER_LEVEL` | — | Minimum level | True if `player.level ≥ amount` |
| `PLAYER_STAT` | Stat name (`str`, `dex`, `con`, `int`, `wis`, `cha`) | Minimum value | |
| `FLAG_EQUALS` | Flag key | — | `value` field = expected value |
| `FLAG_SET` | Flag key | — | True if flag exists (any value) |
| `FLAG_NOT_SET` | Flag key | — | True if flag does not exist |

### Dialogue Actions (`DialogueAction.Type`)

Executed when a node is *entered* (before text is shown). Multiple actions on one node all fire in order.

| Type | `target` field | `amount` field | `value` field | Notes |
|---|---|---|---|---|
| `GIVE_QUEST` | Quest ID | — | — | Safe to call multiple times; no-ops if already active or done |
| `COMPLETE_QUEST` | Quest ID | — | — | Only completes if `quest.isComplete()` is true |
| `GIVE_ITEM` | Item **ID** | Quantity (default 1) | — | Also fires COLLECT progress if matching quest active |
| `TAKE_ITEM` | Item **ID** | Quantity | — | Removes from inventory; silent if not found |
| `GIVE_GOLD` | — | Amount (negative = take) | — | |
| `SET_FLAG` | Flag key | — | Value to store (default `"true"`) | Persists in save |
| `CLEAR_FLAG` | Flag key | — | — | Removes flag entirely |
| `HEAL_PLAYER` | — | HP amount | — | 0 or negative = full heal + 300 food |
| `TEACH_SPELL` | Spell name | — | — | Case-sensitive; must match spell name exactly |
| `OPEN_SHOP` | — | — | — | Closes dialogue, opens NPC's shop |
| `OPEN_CASINO` | — | — | — | Closes dialogue, opens casino |
| `LOG_MESSAGE` | Message text | — | `MessageLog.Type` name | Valid types: `INFO`, `GOOD`, `DANGER`, `LOOT`, `DIM` |
| `CLOSE_DIALOGUE` | — | — | — | Immediately ends the conversation |

---

## Field Reference

### Quest Template Fields (data/quests.json)

```json
{
  "id": "wolf_menace",
  "title": "The Wolf Menace",
  "description": "Kill {amount} wolves near Stonehaven.",
  "type": "KILL",
  "target": "Wolf",
  "requiredAmount": 5,
  "goldReward": 150,
  "xpReward": 400,
  "itemRewardId": "healing_potion",
  "spellRewardId": "",
  "deliverItemId": "",
  "prereqQuestId": "",
  "repeatable": true,
  "minAmount": 3,
  "maxAmount": 8
}
```

- `deliverItemId` — only for DELIVER quests; the item ID the player hands over
- `prereqQuestId` — quest ID that must be in `completedQuests` before this can be offered
- `minAmount` / `maxAmount` — if both > 0 and `maxAmount >= minAmount`, `requiredAmount` is randomised at instance creation (KILL/COLLECT only). `{amount}` in description is replaced with the actual number.

### Progress Text Format (Quest Log display)

| Type | Format |
|---|---|
| KILL | `Wolf killed: 3/5` |
| COLLECT | `Moonbloom Herb collected: 2/4` |
| DELIVER | `Delivered 2/3 Mira the Innkeeper` (target = recipient NPC name) |
| TALK | `Talked to Lyren the Pale` |
| EXPLORE | `Exploration progress: 1/3` |

---

## Known Constraints

1. **COMPLETE_QUEST action does not remove items.** For COLLECT/DELIVER turn-ins via dialogue tree, always pair `TAKE_ITEM` before `COMPLETE_QUEST`. The legacy NPC path (`checkForCollectTurnIn`, `checkForDeliverTurnIn`) removes items automatically, but dialogue tree actions do not.

2. **DELIVER quest: item order in GIVE_QUEST + GIVE_ITEM actions.** List `GIVE_QUEST` before `GIVE_ITEM` in a node's action list. If the item enters inventory before the quest is active, the Quest Log will show `0/N` even though the player has the items.

3. **Dialogue tree bypasses legacy turn-in.** Any NPC with a dialogue tree will never trigger `checkForKillQuestTurnIn`, `checkForCollectTurnIn`, or `checkForDeliverTurnIn`. The questId and questCompleteDialog fields on the NPC are ignored. All quest logic must live inside the tree.

4. **QUEST_ACTIVE condition does not distinguish "in progress" from "ready to turn in."** Both states return true. Use `COMPLETE_QUEST`'s built-in `isComplete()` check as the guard, or add `HAS_ITEM` conditions for item-based quests.

5. **COLLECT target is matched by item display name, not ID.** `HAS_ITEM` and `TAKE_ITEM` use item ID. Know both the item name (for the quest `target` field) and the item ID (for dialogue actions/conditions).

6. **EXPLORE quests require a code-side `progressQuest` call.** There is no automatic EXPLORE tracking. A developer must add `player.progressQuest(Quest.Type.EXPLORE, "target_key", 1)` at the relevant game event (tile step, dungeon special, etc.).

7. **Min/Max randomisation only applies to KILL and COLLECT.** The Quest Editor disables these spinners for other types. Setting them on DELIVER/TALK/EXPLORE has no effect.

8. **Repeatable quests and QUEST_COMPLETE conditions.** When a repeatable quest is re-offered, the completed entry is removed from `completedQuests`. Any dialogue conditions checking `QUEST_COMPLETE` on that quest will return `false` again, unlocking the offer branch correctly.
