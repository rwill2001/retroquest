# Quest Creation Guide

A step-by-step procedure for adding a quest to RetroQuest using the RetroForge editor.

---

## 1. Overview of Quest Types

Choose the quest type that matches the task you want the player to perform.

| Type | When to use | Turn-in |
|------|-------------|---------|
| `KILL` | Kill N enemies of a specific type (target = monster name, case-insensitive) | Auto-completes when count reached; player confirms at giver NPC |
| `COLLECT` | Bring back N copies of a named item (target = item display name) | Auto-completes when count reached; player confirms at giver NPC |
| `TALK` | Speak with a specific NPC; completes when COMPLETE_QUEST fires in their dialogue | Manual via COMPLETE_QUEST action |
| `DELIVER` | Carry a specific item to an NPC recipient; target = recipient NPC name, `deliverItemId` = item to hand over | Manual via COMPLETE_QUEST action (auto-removes delivery items) |
| `EXPLORE` | Reach a location or trigger an event; complete manually via COMPLETE_QUEST action | Manual via COMPLETE_QUEST action |

**Auto vs. manual completion**: `KILL` and `COLLECT` quests track progress automatically as enemies die or items enter inventory. The player still confirms turn-in at the giver NPC. For `TALK`, `DELIVER`, and `EXPLORE` quests, you control completion entirely through a `COMPLETE_QUEST` action in a dialogue node.

---

## 2. Key Concept: Giver vs. Recipient

Most quests involve a single NPC (the **giver**) who offers the quest and accepts the turn-in. `DELIVER` quests are the exception: they involve two NPCs.

| Role | Who | Where defined |
|------|-----|---------------|
| **Giver** | The NPC who offers the quest. Their dialogue tree contains the `GIVE_QUEST` action. | `giverName` field (set automatically from the NPC's name when the quest is given) |
| **Recipient** | The NPC who receives the delivered item. Only used for `DELIVER` quests. | `target` field in the quest template |

**Example**: Aldric (giver) asks you to deliver potions to Mira (recipient).
- `giverName` = `"Aldric"` -- who offered the quest
- `target` = `"Mira the Innkeeper"` -- who receives the item
- `deliverItemId` = `"healing_potion"` -- what to hand over

For all other quest types, `target` means the thing being hunted/collected/talked-to, and `giverName` is the NPC who gives and accepts turn-in.

---

## 3. Step 1 -- Create the Quest Template

Open RetroForge -> **Quest Editor** (from the EDITORS menu).

Fill in every field:

| Field | Description |
|-------|-------------|
| **ID** | Unique lowercase snake_case key, e.g. `moonbloom_collect`. Used in all dialogue actions and conditions. |
| **Title** | Short display name shown in the Quest Log, e.g. `Moonbloom Harvest`. |
| **Description** | One or two sentences. May include the token `{amount}` which is replaced at runtime with the actual required amount. |
| **Type** | `KILL`, `COLLECT`, `TALK`, `DELIVER`, or `EXPLORE`. Changes which other fields are visible. |
| **Target** | Depends on type -- see table below. |
| **Giver NPC** | The NPC who offers this quest. Dropdown populated from all NPCs in town/overworld maps. Required for `COLLECT` and `KILL` quests (used to match who the player turns in to). For dialogue-tree-only quests, this is set automatically when GIVE_QUEST fires. |
| **Deliver Item** | Only for `DELIVER` quests. The item ID the player must hand over to the recipient NPC. Dropdown from ItemRegistry. |
| **Required Amount** | How many kills/items/deliveries are needed. For `TALK` and `EXPLORE`, set to `1`. |
| **Min/Max Amount** | Optional random range for `KILL`/`COLLECT` quests. If both are `0`, the fixed Required Amount is used. At runtime, a random value between min and max replaces the fixed amount. |
| **Gold Reward** | Gold awarded on completion (`0` for none). |
| **XP Reward** | XP awarded on completion. |
| **Item Reward ID** | Optional item ID from `ItemRegistry` to give on completion. Leave blank for none. |
| **Spell Reward ID** | Optional spell to teach on completion. Leave blank for none. |
| **Prereq Quest** | Optional quest ID that must be completed before this quest can be offered. |
| **Repeatable** | If checked, the quest can be accepted again after completion. The engine resets status and re-adds it as active. |

### Target field by quest type

| Type | Target dropdown | What it means |
|------|-----------------|---------------|
| `KILL` | Monster name (from MonsterRegistry) | Which monster to kill |
| `COLLECT` | Item display name (from ItemRegistry) | Which item to collect |
| `DELIVER` | NPC name (from all town/overworld maps) | The **recipient** NPC, not the giver |
| `TALK` | Free text | NPC or location name (informational only) |
| `EXPLORE` | Free text | Location name (informational only) |

Click **Save**. The quest is written to `data/quests.json` immediately.

---

## 4. Step 2 -- Create or Select the Quest-Giver NPC

Open RetroForge -> load the target map -> **NPC Editor**.

Either create a new NPC or select an existing one.

- Set **Name**, **Sprite**, and **Type** as appropriate.
- The **QUEST (legacy)** field at the bottom is deprecated -- leave it at `(None)`. Use the Dialogue Tree instead (Step 3).
- The **Normal Dialog** and **Post-Quest Dialog** text areas are used only if this NPC has *no* dialogue tree. Once a tree is assigned, those fields are ignored at runtime.

Click **Save NPC**.

---

## 5. Step 3 -- Build the Dialogue Tree

With the NPC selected in NPC Editor, click **Edit Dialogue Tree...**.

### Recommended node structure

A standard fetch/kill quest uses this structure:

```
greeting (text: "...")
  |-- [not active, not complete] --> offer
  |-- [quest active]             --> remind
  |-- [quest complete]           --> done

offer (NPC asks for help)
  |-- "Yes, I'll help."  --> accepted
  |-- "Not now."          --> farewell

accepted (GIVE_QUEST action fires here)
  |-- "Farewell."         --> null (ends)

remind (progress check)
  |-- "Farewell."         --> null

done (COMPLETE_QUEST action fires here)
  |-- "Farewell."         --> null

farewell
  |-- "Farewell."         --> null
```

For a `DELIVER` quest, you need two dialogue trees -- one on the **giver** NPC and one on the **recipient** NPC. See the worked example in Section 10.

### 5a. Set the start node

Click the root `DialogueTree` in the navigator. In the **Tree Properties** card, set **Start Node** to `greeting` (or whichever is first).

### 5b. Add GIVE_QUEST to the "accepted" node

1. Select the `accepted` node in the tree.
2. In the **Node Editor** card, click **+Action**.
3. Double-click the new action to open the **Action Editor**.
4. Set **TYPE** to `GIVE_QUEST`.
5. The **TARGET** field switches to a quest dropdown. Select your quest (format: `quest_id  --  Quest Title`).
6. Click **Save Action**, then **Save Node Changes**.

### 5c. Add conditions to the "greeting" choices

Select the `greeting` node and open **Choice Editor** for the "offer" choice:

1. Click **+Condition**.
2. Type: `QUEST_ACTIVE` -- select your quest ID. Add **Negate** (tick) so it shows when the quest is *not* active.
3. Add a second condition: `QUEST_COMPLETE` for your quest ID, also negated.

For the "remind" choice:
- Condition: `QUEST_ACTIVE` for your quest ID (not negated).

For the "done" choice:
- Condition: `QUEST_COMPLETE` for your quest ID (not negated).

### 5d. Add COMPLETE_QUEST to the "done" node

1. Select the `done` node.
2. Click **+Action** -> double-click to edit.
3. TYPE: `COMPLETE_QUEST`. TARGET: select your quest from the dropdown.
4. Click **Save Action**, **Save Node Changes**.

The engine will call `Player.completeQuest()` which:
- Marks the quest `COMPLETED`
- Awards gold, XP, item reward, and spell reward from the quest template
- Logs a completion message

You may also add `GIVE_GOLD` or `LOG_MESSAGE` actions on the same node for custom flavour text alongside the standard reward message.

### 5e. Routing-only nodes ("..." sentinel)

Set any node's text to `"..."` (three dots) and give it exactly one choice per branch (with appropriate conditions). At runtime the engine skips the node and jumps directly to whichever choice passes its conditions. This is the standard pattern for `greeting` nodes -- automatic branching based on quest state without showing an empty text box.

---

## 6. Action Ordering Rules

When a dialogue node has multiple actions, they execute **in list order, top to bottom**. Getting the order wrong can cause subtle bugs. Follow these rules:

### Rule 1: GIVE_QUEST before GIVE_ITEM

If the giver hands the player an item as part of accepting the quest (common for DELIVER quests), the `GIVE_QUEST` action must come first:

```
accepted node actions (correct order):
  1. GIVE_QUEST  --> potion_delivery
  2. GIVE_ITEM   --> healing_potion (amount: 3)
```

**Why**: If GIVE_ITEM fires first, the items enter inventory before the quest is active. For COLLECT quests, progress would not register for those items. For DELIVER quests, the quest tracker wouldn't know about them.

### Rule 2: COMPLETE_QUEST before TAKE_ITEM

If the turn-in node also takes an item from the player, `COMPLETE_QUEST` must come first:

```
done node actions (correct order):
  1. COMPLETE_QUEST --> waterfall_haunting
  2. TAKE_ITEM      --> old_locket (amount: 1)
```

**Why**: For DELIVER quests, `COMPLETE_QUEST` automatically removes the delivery items from inventory. If `TAKE_ITEM` ran first, it could remove the item before the quest engine counts it.

**Note**: For DELIVER quests, you usually do not need a separate TAKE_ITEM action at all -- `COMPLETE_QUEST` handles item removal automatically. Only add TAKE_ITEM if you need to remove a *different* item beyond the delivery items.

### Rule 3: COMPLETE_QUEST before reward actions

Place any custom `GIVE_GOLD`, `GIVE_ITEM`, or `TEACH_SPELL` reward actions *after* `COMPLETE_QUEST`. The standard quest rewards (from the quest template) are granted inside `COMPLETE_QUEST` automatically.

### General principle

Think of action order as: **state change first, side effects second**. Quest status changes (GIVE_QUEST, COMPLETE_QUEST) should always precede inventory changes (GIVE_ITEM, TAKE_ITEM) on the same node.

---

## 7. Step 4 -- Validate the Tree

In the **Tree Properties** card, click **Validate Tree**.

The validator checks:
- Start node exists.
- All `nextNodeId` references resolve to real nodes.
- No orphaned (unreachable) nodes.
- All `GIVE_QUEST` / `COMPLETE_QUEST` targets exist in `QuestRegistry`.
- All `GIVE_ITEM` / `TAKE_ITEM` targets exist in `ItemRegistry`.
- All `QUEST_ACTIVE` / `QUEST_COMPLETE` / `HAS_ITEM` condition targets exist in the respective registries.

Fix any `ERROR` entries before testing. `WARN` entries are advisory.

Click **OK -- Apply** to write the tree back to the NPC, then save the map in RetroForge.

---

## 8. Step 5 -- Save and Test In-Game

1. In RetroForge, save the map (**File -> Save Map** or Ctrl+S).
2. Launch the game. Navigate to the NPC.
3. Verify:
   - The offer dialogue appears on first contact.
   - Accepting adds the quest to the Quest Log (`Q` key).
   - For `KILL`/`COLLECT` quests, progress increments as enemies die or items are picked up.
   - Returning to the NPC shows the "done" branch only when the quest is complete.
   - Rewards are granted and the quest moves to the completed list.
4. For `DELIVER` quests, also verify:
   - The giver NPC gives the delivery items (if applicable).
   - The recipient NPC's dialogue tree correctly detects the active quest and item in inventory.
   - Handing over the items removes them from inventory and completes the quest.
5. Edge case: pick up a quest item *before* accepting the quest, then accept and immediately turn in. The engine re-counts inventory on COMPLETE_QUEST, so previously-collected items are counted correctly.

---

## 9. Worked Example: Fetch Quest (COLLECT)

**Scenario**: Elara in Moonhaven wants 2 Moonbloom Flowers.

### Quest template (`data/quests.json` entry)

```json
{
  "id": "moonbloom_collect",
  "title": "Moonbloom Harvest",
  "description": "Elara needs {amount} Moonbloom Flowers for a healing salve.",
  "type": "COLLECT",
  "target": "Moonbloom Flower",
  "requiredAmount": 2,
  "goldReward": 50,
  "xpReward": 150,
  "itemRewardId": "healing_potion"
}
```

### Dialogue tree nodes (Elara)

**greeting** (text: `"..."`)
- Choice `"..."` -> `offer`
  Conditions: NOT QUEST_ACTIVE(moonbloom_collect), NOT QUEST_COMPLETE(moonbloom_collect)
- Choice `"..."` -> `remind`
  Condition: QUEST_ACTIVE(moonbloom_collect)
- Choice `"..."` -> `done`
  Condition: QUEST_COMPLETE(moonbloom_collect)

**offer** (text: `"Traveler, could you gather two Moonbloom Flowers from the eastern fields?"`)
- Choice `"Of course, I'll gather them."` -> `accepted`
- Choice `"I'm too busy right now."` -> `farewell`

**accepted** (text: `"Wonderful! Please hurry -- I need them for tonight's salve."`)
- Action: GIVE_QUEST -> `moonbloom_collect`
- Choice `"I'll return soon."` -> `null` (ends)

**remind** (text: `"Have you found the Moonbloom Flowers yet? I need them urgently."`)
- Choice `"Still searching."` -> `null`

**done** (text: `"You've found them! Here, take this as thanks."`)
- Action: COMPLETE_QUEST -> `moonbloom_collect`
- Choice `"Happy to help."` -> `null`

**farewell** (text: `"Come back if you change your mind."`)
- Choice `"Farewell."` -> `null`

### How it works at runtime

1. Player talks to Elara. `greeting` is a `"..."` node, so the engine auto-routes to `offer` (neither active nor complete).
2. Player picks "Of course" -> `accepted` node fires `GIVE_QUEST`. Quest is now IN_PROGRESS.
3. Player finds Moonbloom Flowers in the world. Each pickup calls `addItemAndProgress()`, which increments the quest's progress counter.
4. When progress reaches 2, the quest auto-completes internally.
5. Player returns to Elara. `greeting` routes to `done`. `COMPLETE_QUEST` fires, granting 50 gold, 150 XP, and a Healing Potion.

**Edge case**: If the player already had 2 Moonbloom Flowers before talking to Elara, the engine re-counts inventory items when `COMPLETE_QUEST` fires on the `done` node, so the quest completes correctly.

---

## 10. Worked Example: Deliver Quest

**Scenario**: Aldric in Stonehaven asks the player to deliver 1 Healing Potion to Mira the Innkeeper in Moonhaven.

A DELIVER quest requires dialogue trees on **two** NPCs: the giver (Aldric) and the recipient (Mira).

### Quest template

```json
{
  "id": "potion_delivery",
  "title": "Potion Delivery",
  "description": "Aldric asked you to deliver a Healing Potion to Mira the Innkeeper.",
  "type": "DELIVER",
  "target": "Mira the Innkeeper",
  "deliverItemId": "healing_potion",
  "requiredAmount": 1,
  "giverName": "Aldric",
  "goldReward": 100,
  "xpReward": 250
}
```

Field breakdown:
- **`target`** = `"Mira the Innkeeper"` -- the **recipient** NPC (who the player delivers to)
- **`deliverItemId`** = `"healing_potion"` -- the item the player must hand over
- **`giverName`** = `"Aldric"` -- the NPC who offers the quest (set from NPC name at runtime)

### Giver dialogue tree (Aldric in Stonehaven)

**greeting** (text: `"..."`)
- Choice `"..."` -> `offer`
  Conditions: NOT QUEST_ACTIVE(potion_delivery), NOT QUEST_COMPLETE(potion_delivery)
- Choice `"..."` -> `remind`
  Condition: QUEST_ACTIVE(potion_delivery)
- Choice `"..."` -> `thanks`
  Condition: QUEST_COMPLETE(potion_delivery)

**offer** (text: `"Could you deliver a Healing Potion to Mira the Innkeeper in Moonhaven? I owe her a debt."`)
- Choice `"Sure, I'll deliver it."` -> `accepted`
- Choice `"Not right now."` -> `farewell`

**accepted** (text: `"Thank you! Here's the potion. Give it directly to Mira."`)
- Action 1: GIVE_QUEST -> `potion_delivery`
- Action 2: GIVE_ITEM -> `healing_potion` (amount: 1)
- Choice `"I'm on my way."` -> `null`

**Important**: `GIVE_QUEST` must be action 1, `GIVE_ITEM` must be action 2. See Section 6, Rule 1.

**remind** (text: `"Have you delivered the potion to Mira yet?"`)
- Choice `"Not yet."` -> `null`

**thanks** (text: `"Mira told me you came through. You have my gratitude."`)
- Choice `"Glad to help."` -> `null`

### Recipient dialogue tree (Mira the Innkeeper in Moonhaven)

Mira already has her own dialogue tree (she's an innkeeper). Add quest-related branches to her existing `greeting` node:

**greeting** (text: `"..."`)
- Choice `"..."` -> `deliver`
  Conditions: QUEST_ACTIVE(potion_delivery), HAS_ITEM(healing_potion)
- Choice `"..."` -> `waiting`
  Condition: QUEST_ACTIVE(potion_delivery)
- Choice `"..."` -> `normal_greeting`
  (no conditions -- fallback to her regular dialogue)

**deliver** (text: `"You brought the potion from Aldric! Thank you so much."`)
- Action: COMPLETE_QUEST -> `potion_delivery`
- Choice `"Glad to help."` -> `null`

**waiting** (text: `"Aldric said he was sending someone with a potion... still waiting on that."`)
- Choice `"I'll be back."` -> `null`

### How it works at runtime

1. Player talks to Aldric. `GIVE_QUEST` fires first (quest is now active), then `GIVE_ITEM` adds the potion to inventory.
2. Player travels to Moonhaven and talks to Mira.
3. Mira's `greeting` routes to `deliver` because QUEST_ACTIVE is true and HAS_ITEM is true.
4. `COMPLETE_QUEST` fires: the engine detects this is a DELIVER quest, automatically removes the healing potion(s) from inventory, then grants rewards (100 gold, 250 XP).
5. Back at Aldric, `greeting` now routes to `thanks`.

**Note**: You do **not** need a separate TAKE_ITEM action on Mira's `deliver` node. `COMPLETE_QUEST` automatically removes the delivery items for DELIVER quests.

---

## 11. Tips and Common Mistakes

### Quest IDs must match exactly
The ID in the quest template, GIVE_QUEST target, COMPLETE_QUEST target, and all condition targets must be the exact same string. Use the dropdown pickers in the editor to avoid typos.

### COLLECT progress is automatic
When any item enters inventory via `addItemAndProgress()`, the engine scans active COLLECT quests for a matching `target` (item display name). The item's `name` field must exactly match the quest `target`. Progress also works retroactively -- if items were collected before accepting the quest, `COMPLETE_QUEST` re-counts inventory.

### KILL progress is automatic
`Player.progressQuest(KILL, monsterName, 1)` is called after combat. The monster's display name must match the quest `target` (case-insensitive).

### DELIVER: COMPLETE_QUEST removes items automatically
For DELIVER quests, `COMPLETE_QUEST` removes the `deliverItemId` items from inventory. Do not add a separate TAKE_ITEM for the delivery items -- that would try to remove items that are already gone.

### DELIVER: target is the recipient, not the item
The `target` field for DELIVER quests is the **recipient NPC's name**, not the item. The item is specified separately in `deliverItemId`. This is the most common DELIVER quest mistake.

### GIVE_QUEST is safe to call repeatedly
`GIVE_QUEST` is a no-op if the quest is already active or completed. `COMPLETE_QUEST` is a no-op if already completed.

### Quest not completing at turn-in
If COMPLETE_QUEST logs a dim message saying the quest isn't active, the player reached the done node without having accepted the quest. Add `QUEST_ACTIVE` conditions to the done choice to prevent this.

### Repeatable quests
Set `repeatable = true` in the quest template. When `GIVE_QUEST` fires and the quest was previously completed, the engine resets its status and re-adds it as active. Structure your `greeting` node the same way as a normal quest -- `QUEST_COMPLETE` returns false once the quest is re-accepted, routing the player to `remind` until they complete it again.

### Multi-step quests
Use `SET_FLAG` to track intermediate steps and `FLAG_EQUALS` / `FLAG_SET` conditions to gate nodes. Example: after delivering item A, set `"step2_ready"` = `"true"`, then condition the second stage on that flag.

### Testing with LOG_MESSAGE
Add `LOG_MESSAGE` actions (type: DIM) to intermediate nodes to emit debug messages in the message log during testing. Remove them before shipping.

### Prerequisite quests
Set the `prereqQuestId` field to require another quest to be completed before this one can be offered. The engine checks this automatically in both the legacy NPC quest path and the dialogue GIVE_QUEST action.

---

## 12. Quick Reference: Dialogue Action Types for Quests

| Action | Target | Amount | Effect |
|--------|--------|--------|--------|
| `GIVE_QUEST` | Quest ID | -- | Adds quest to player's active list. No-op if already active/completed (unless repeatable). |
| `COMPLETE_QUEST` | Quest ID | -- | Marks quest completed, grants template rewards. For DELIVER quests, auto-removes delivery items. For COLLECT quests, re-counts inventory first. |
| `GIVE_ITEM` | Item ID | Count | Adds items to inventory. Place after GIVE_QUEST if items relate to the quest. |
| `TAKE_ITEM` | Item ID | Count | Removes items from inventory. Place after COMPLETE_QUEST. |
| `GIVE_GOLD` | -- | Amount | Awards gold. |
| `TEACH_SPELL` | Spell ID | -- | Teaches a spell. |
| `SET_FLAG` | Flag name | Value | Sets a persistent player flag (survives save/load). |
| `LOG_MESSAGE` | Message text | Type | Writes to the in-game message log. |

---

## 13. Quick Reference: Dialogue Condition Types for Quests

| Condition | Target | Negate? | Use |
|-----------|--------|---------|-----|
| `QUEST_ACTIVE` | Quest ID | Yes/No | Is the quest currently in the player's active list? |
| `QUEST_COMPLETE` | Quest ID | Yes/No | Has the player completed this quest? |
| `HAS_ITEM` | Item ID | Yes/No | Does the player have this item in inventory? |
| `HAS_GOLD` | -- | Yes/No | Does the player have enough gold? (value field = amount) |
| `PLAYER_LEVEL` | -- | Yes/No | Is the player at or above a level? (value field = level) |
| `FLAG_SET` | Flag name | Yes/No | Is this flag set to any value? |
| `FLAG_EQUALS` | Flag name | Yes/No | Does this flag equal a specific value? (value field = expected) |
