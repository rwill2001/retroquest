# Island 1 Test Plan — Lirandel

> **Warning:** This test plan was written during initial Island 1 development. Specific coordinates, NPC positions, and quest flows may have shifted since then. Verify against the current `.rfmap` files and `quests.json` before using as a regression checklist.

**Purpose:** Step-by-step walkthrough of a new character from spawn to the Island 2 portal.
Validates all quest chains, NPC dialogue trees, dungeon progression, and loot.

**Estimated total playtime:** 90–120 minutes for full coverage.

**Setup complete.** Island Portal (`J`) placed at (116,67) in lirandel overworld.
A mountain ring surrounds the portal; the locked Portal Gate (`^`) at (116,70) requires the Key of Tides to open.

---

## Pre-Test Setup

- [ ] Start a **new game** — use the character creation screen
- [ ] Choose any class (test at least one martial and one caster class across runs)
- [ ] Verify spawn point lands on the Lirandel overworld near the beach (south-west area)
- [ ] Open Quest Log (Q) — should be empty
- [ ] Open Inventory (I) — should have starter gear only

---

## Phase 1 — Beach Landing (5–10 min)

**Goal:** Meet Kael, get a healing potion, orient to the world.

### Step 1.1 — Kael the Beachcomber
- [ ] Walk to Kael on the beach (south-west overworld area), press T
- [ ] First visit: "first_meeting" node (FLAG `kael_met` not set)
- [ ] Explore branches: about_scar, about_island, point_moonhaven
- [ ] Reach "first_farewell" → GIVE_ITEM (healing_potion), SET_FLAG (kael_met)
- [ ] Verify healing potion in inventory
- [ ] Re-visit: routes to "return_visit" — no second potion given
- [ ] *(Come back at level 5+)* — "deep_lore" branch unlocks (PLAYER_LEVEL >= 5)

### Step 1.2 — Ghostly Sailor (shipwreck)
- [ ] Find Ghostly Sailor on the overworld (shipwreck site)
- [ ] First visit: GIVE_GOLD (80) + GIVE_ITEM (rusty_sword), SET_FLAG (sailor_rewarded)
- [ ] Re-visit: routes to "return_visit" — no second reward

### Step 1.3 — Basic combat
- [ ] Engage a low-level enemy (crab or wolf near the beach)
- [ ] Verify combat overlay opens, turn-based combat functions
- [ ] Verify XP and gold gained on kill

---

## Phase 2 — Moonhaven: First Visit (20–30 min)

**Goal:** Enter town, meet all 8 NPCs, collect initial quests.

> **Access:** Town entrance at overworld position (20, 32).
> Moonhaven has 8 NPCs: Bram (inn), Acolyte, Mira (healer), Guard Garrett,
> Lucky Len (casino), Maren (shop), Wolfie (east side), Aldric (east side).

### Step 2.1 — Enter Moonhaven
- [ ] Walk to the Moonhaven entrance tile and enter
- [ ] Verify town map loads (20×20)
- [ ] Verify all 8 NPCs are visible

### Step 2.2 — Innkeeper Bram
- [ ] Talk to Bram — FLAG `bram_visited` set
- [ ] Rest for 15 gold → HEAL_PLAYER fires, HP restored
- [ ] Explore "gossip" → "gossip_deeper" (mentions the Acolyte)
- [ ] Explore "island_lore" → "island_lore_2" (mentions the Scar)
- [ ] Accept cave_explore quest: "Have you heard anything strange from underground?" →
  GIVE_QUEST (cave_explore), FLAG `bram_cave_quest_given`
- [ ] Verify "Into the Depths" in Quest Log

### Step 2.3 — Maren the Merchant
- [ ] Talk to Maren — shop opens with: healing_potion, iron_sword, chain_mail,
  scroll_of_cure_light, scroll_of_sleep, scroll_of_protection
- [ ] Buy and sell an item, verify gold changes correctly
- [ ] Choose "Do you have any work for me?" →
  GIVE_QUEST (potion_delivery), GIVE_ITEM (healing_potion ×3), FLAG `maren_quest_given`
- [ ] Verify "Potion Delivery" in Quest Log and 3 healing potions in inventory
- [ ] Re-talk to Maren — "Do you have any work?" choice should be hidden (FLAG set)

### Step 2.4 — Acolyte (rising_tide)
- [ ] Talk to Acolyte — explore island_danger and lirandel_info branches
- [ ] Accept rising_tide: GIVE_QUEST, FLAG `acolyte_quest_given`
- [ ] Verify "The Rising Tide" in Quest Log (kill 5 Corrupted Crabs)

### Step 2.5 — Healer Mira
- [ ] Talk to Mira
- [ ] Heal for 10 gold (HAS_GOLD condition) → GIVE_GOLD (-10), HEAL_PLAYER
- [ ] Teach spell: TEACH_SPELL (Cure Light Wounds) — verify in Spellbook (S)
- [ ] Accept moonbloom_collection: "I heard you need Moonbloom Herbs" →
  GIVE_QUEST, FLAG `mira_quest_given`
- [ ] Verify "Moonbloom for the Sick" in Quest Log (collect 4)
- [ ] Deliver potions: "I have the potions Maren asked me to bring." →
  routes to potion_delivery_done (QUEST_ACTIVE + HAS_ITEM ×3) →
  TAKE_ITEM (×3), COMPLETE_QUEST (potion_delivery) → 100 gold + 250 XP
- [ ] Verify 3 healing potions removed from inventory
- [ ] Verify "Potion Delivery" moves to completed in Quest Log

### Step 2.6 — Town Guard (Garrett)
- [ ] Talk to Garrett — explore area_info, east_warning, forest_info, combat_advice
- [ ] *(Come back at level 10+)* — scar_lore branch unlocks, FLAG `heard_scar_lore` set

### Step 2.7 — Lucky Len (Casino)
- [ ] Talk to Lucky Len — OPEN_CASINO fires, verify casino overlay opens
- [ ] Play at least one game, verify gold changes
- [ ] Explore len_backstory and shipwreck_story branches

### Step 2.8 — Wolfie the Orc
- [ ] Talk to Wolfie (east side, position 17,8)
- [ ] Explore greeting, wolf_problem, wolfie_backstory branches
- [ ] Accept wolf_menace: GIVE_QUEST, FLAG `wolfie_quest_given`
- [ ] Verify "The Wolf Menace" in Quest Log (kill 1–5 Wolves, randomized)

### Step 2.9 — Aldric the Alchemist
- [ ] Talk to Aldric (east side, position 17,14)
- [ ] Accept find_the_hermit: GIVE_QUEST, FLAG `aldric_quest_given`
- [ ] Verify "Find the Hermit" in Quest Log (talk to Lyren the Pale in Mooncrest)

---

## Phase 3 — Overworld Quests: Crabs and Wolves (20–30 min)

**Goal:** Complete rising_tide and wolf_menace, chain into follow-up quests.

### Step 3.1 — Hunt Corrupted Crabs (rising_tide)
- [ ] Find Corrupted Crabs on the southern coast
- [ ] Kill 5 — verify Quest Log count updates each kill
- [ ] Return to Acolyte: "The tide-spawn are cleared." →
  COMPLETE_QUEST (rising_tide) → 150 gold + 400 XP
- [ ] Quest Log: rising_tide completed

### Step 3.2 — Acolyte follow-up: skeleton_menace
- [ ] After rising_tide completes, Acolyte greeting shows
  "The undead still walk these lands." (QUEST_COMPLETE rising_tide + FLAG_NOT_SET)
- [ ] Accept skeleton_menace: GIVE_QUEST, FLAG `skeleton_quest_given`
- [ ] Verify "Skeleton Menace" in Quest Log (kill 1–6 Skeletons, randomized)
- [ ] Hunt Skeletons near the burial grounds (northeast overworld)
- [ ] Return to Acolyte: "The skeletons are defeated." →
  COMPLETE_QUEST (skeleton_menace) → 100 gold + 250 XP
- [ ] Acolyte offers "I seek a holy trial." → bless_quest_offer

### Step 3.3 — Acolyte follow-up: cleric_rite_of_bless
- [ ] Accept cleric_rite_of_bless (QUEST_COMPLETE skeleton_menace + FLAG_NOT_SET) →
  GIVE_QUEST, FLAG `bless_quest_given`
- [ ] Verify "Rite of Blessing" in Quest Log (kill 3 Skeletons)
- [ ] Kill 3 more Skeletons
- [ ] Return: "The rite is fulfilled." → COMPLETE_QUEST →
  spell reward: Bless learned → verify in Spellbook

### Step 3.4 — Acolyte follow-up: purge_dreamwake (defer until dungeon)
- [ ] After cleric_rite_of_bless complete, Acolyte shows shrine offer
- [ ] Accept purge_dreamwake: GIVE_QUEST, FLAG `shrine_quest_given`
- [ ] Verify "Purge the Dreamwake Caverns" in Quest Log (destroy 3 shrines)
- [ ] *(Completion deferred to Phase 7 — shrines are in the dungeon)*

### Step 3.5 — Hunt Wolves (wolf_menace)
- [ ] Find Wolves in the forest/ravine area
- [ ] Kill required number (randomized 1–5)
- [ ] Return to Wolfie: "I've dealt with the wolves." →
  COMPLETE_QUEST (wolf_menace) → 150 gold + 400 XP
- [ ] "wolfie_future" node hints at standing stones mystery
- [ ] Wolfie follow-up: "Do you have more research for me?" →
  GIVE_QUEST (iron_sword_collect), FLAG `wolfie_sword_given`
- [ ] Verify "A Worthy Blade" in Quest Log (collect 1 Iron Sword)

### Step 3.6 — Kael reaction
- [ ] Return to Kael — "crabs_done" then "return_advice" nodes (QUEST_COMPLETE rising_tide)
- [ ] Kael mentions altars and cubes (dungeon hints)

---

## Phase 4 — Waterfall Cave (5–10 min)

**Goal:** Pick up Waterfall Haunting quest.

> **Access:** Town entrance at overworld position (52, 22).

### Step 4.1 — Ghostly Woman
- [ ] Enter waterfallcave (12×10 map)
- [ ] Talk to Ghostly Woman (position 6,3) — legacy dialogue fires
- [ ] waterfall_haunting quest auto-accepted (COLLECT: Silver Locket)
- [ ] Verify "The Waterfall Haunting" in Quest Log
- [ ] Note: Silver Locket is `lootable: true`, UNCOMMON, tier 1 — drops from dungeon floor/chest loot

---

## Phase 5 — Mooncrest (15–20 min)

**Goal:** Complete find_the_hermit, pick up alchemists_stone, get Mooncrest Stone.

> **Access:** Town entrance at overworld position (97, 39).

### Step 5.1 — Enter Mooncrest (64×64 map)
- [ ] Navigate east overworld to Mooncrest entrance
- [ ] Verify town loads

### Step 5.2 — Oswin the Innkeeper
- [ ] Talk to Oswin — rest works correctly

### Step 5.3 — Lyren the Pale (find_the_hermit)
- [ ] Talk to Lyren — `progressQuest(TALK, "Lyren the Pale", 1)` auto-fires
- [ ] find_the_hermit auto-completes (1/1) → 50 gold + 200 XP awarded immediately
- [ ] With find_the_hermit active: "Aldric sent me to find you." → hermit_found → why_locked / ley_lines branches available
- [ ] hermit_accept sets LOG_MESSAGE; SET_FLAG lyren_met fires on hermit_found and who_are_you
- [ ] On first visit without any quest: "Just exploring. Who are you?" → who_are_you → ley_lines / why_locked

### Step 5.4 — Dagan the Merchant
- [ ] Talk to Dagan — verify shop opens with level-appropriate weapons/armor

### Step 5.5 — Return to Aldric for alchemists_stone
- [ ] Return to Moonhaven, talk to Aldric
- [ ] "I spoke with Lyren." choice visible (QUEST_COMPLETE find_the_hermit + FLAG_NOT_SET alch_stone_given)
- [ ] Routes to give_stone_quest → GIVE_QUEST (alchemists_stone), FLAG `alch_stone_given`
- [ ] Verify "The Alchemist's Stone" in Quest Log

### Step 5.6 — Return to Lyren for the stone
- [ ] Travel back to Mooncrest, talk to Lyren
- [ ] "Aldric needs a Mooncrest Stone from you." → stone_request → stone_consider / stone_favor paths
- [ ] Both paths converge at stone_give: GIVE_ITEM (mooncrest_stone), SET_FLAG (lyren_stone_given)
- [ ] stone_aftermath: Lyren warns about restless ley lines; optional ley_warning branch sets lyren_ley_warning flag
- [ ] mooncrest_stone in inventory
- [ ] Return visits after stone: "Good to see you again, Lyren." → return_visit with ask_menu options

### Step 5.7 — Turn in to Aldric
- [ ] Return to Moonhaven, talk to Aldric
- [ ] "I have the Mooncrest Stone." choice (HAS_ITEM mooncrest_stone) →
  stone_complete: TAKE_ITEM (mooncrest_stone) + COMPLETE_QUEST → 600 XP
- [ ] mooncrest_stone removed from inventory
- [ ] Aldric follow-up: "Do you have another task?" →
  GIVE_QUEST (mage_trial_lightning), FLAG `aldric_lightning_given`
- [ ] Verify "Trial by Storm" in Quest Log (kill 5 any enemies)

---

## Phase 6 — Side Quests and Loot (time varies)

**Goal:** Complete remaining surface quests before the dungeon run.

### Step 6.1 — Trial by Storm (mage_trial_lightning)
- [ ] Kill 5 enemies (any type counts — wolves, crabs, skeletons)
- [ ] Return to Aldric: "I have proven my mettle." (QUEST_ACTIVE mage_trial_lightning) →
  COMPLETE_QUEST → spell reward: Lightning Bolt learned → verify in Spellbook

### Step 6.2 — Iron Sword (iron_sword_collect)
- [ ] Obtain an Iron Sword (buy from Maren's shop or find as loot)
- [ ] Return to Wolfie: "I found the Iron Sword." (QUEST_ACTIVE + HAS_ITEM iron_sword) →
  TAKE_ITEM (iron_sword) + COMPLETE_QUEST → 100 gold + 250 XP

---

## Phase 7 — Dungeon Run (30–45 min)

**Goal:** Enter the dungeon, complete cave_explore, collect Silver Locket and Moonbloom Herbs,
destroy 3 Corrupted Shrines, defeat the Corrupted Moon Guardian boss, receive Key of Tides.

> **Access:** Dungeon entrance tile on the overworld (stone archway sprite).

### Step 7.1 — Enter and explore level 1
- [ ] Walk onto dungeon entrance tile → transition to dungeon depth 1
- [ ] `progressQuest(EXPLORE, "Dungeon Level 1", 1)` fires automatically on entry
- [ ] Fog of war active, level 1 monsters are levels 1–3
- [ ] Check floor loot: Moonbloom Herb (`lootable: true`, tier 1, COMMON) and
  Silver Locket (`lootable: true`, tier 1, UNCOMMON) can both drop here
- [ ] Interact with altar: verify altar special fires (prayer options, favor)
- [ ] Interact with fountain: verify fountain special fires
- [ ] Find a chest: verify loot rolls, item added to inventory

### Step 7.2 — cave_explore turn-in
- [ ] Exit dungeon and return to Moonhaven
- [ ] Talk to Bram: "I explored the dungeon depths." (QUEST_ACTIVE cave_explore) →
  COMPLETE_QUEST → 200 gold + 500 XP
- [ ] Quest Log: cave_explore completed

### Step 7.3 — Deeper dungeon: shrines and loot
- [ ] Re-enter dungeon, descend to depth 2 via stairs
- [ ] Find and activate a Corrupted Shrine (altar tile at depths 1–2) →
  "Destroy it?" prompt → YES → purge_dreamwake progress (1/3, then 2/3)
- [ ] Collect Silver Locket if not yet found (UNCOMMON floor/chest drop)
- [ ] Collect 4 Moonbloom Herbs if not yet found (COMMON floor/chest drop)

### Step 7.4 — Moonbloom Herb turn-in
- [ ] Exit dungeon, return to Mira in Moonhaven
- [ ] "I found the Moonbloom Herbs." (QUEST_ACTIVE moonbloom_collection) visible when
  HAS_ITEM moonbloom_herb ×4
- [ ] COMPLETE_QUEST → 100 gold + 300 XP + healing_potion reward
- [ ] +10 Lirandel Divine Favor awarded (wired in Player.completeQuest)
- [ ] 4 Moonbloom Herbs removed from inventory

### Step 7.5 — Waterfall Haunting turn-in
- [ ] With Silver Locket in inventory, return to waterfallcave
- [ ] Talk to Ghostly Woman: quest complete dialog fires
  ("You found it... my daughter's portrait...")
- [ ] Silver Locket removed, 50 gold + 150 XP awarded

### Step 7.6 — Boss fight at depth 3
- [ ] Descend to dungeon depth 3
- [ ] Find and approach an altar tile → prompt fires:
  "A Corrupted Shrine pulses with dark energy. The Corrupted Moon Guardian rises!"
- [ ] Combat begins: Corrupted Moon Guardian (HP 65, attack 5, XP 100)
- [ ] Defeat boss
- [ ] Post-fight choice:
  - **"Destroy it (pure path)"** → Key of Tides + full heal + MOONBLESSED boon + +20 Lirandel favor
  - **"Absorb its power (corruption)"** → Key of Tides + Shard of Corruption + PYRALIS_TEMPER boon + +5 Pyralis favor
- [ ] Verify Key of Tides in inventory either path
- [ ] purge_dreamwake progress: 3/3 complete
- [ ] Return to Acolyte: "The shrines are purged." →
  COMPLETE_QUEST (purge_dreamwake) → 200 gold + 400 XP + ring_of_minor_protection

---

## Phase 8 — Island Portal (5 min)

**Goal:** Use the Key of Tides to unlock the Portal Gate and activate the Island 2 portal.

> **Location:** Far east coast of the Lirandel overworld at approximately (116, 67).
> A 7×7 mountain ring encloses the portal; the only entrance is the Portal Gate (`^`) at (116, 70).

### Step 8.1 — Reach the gate
- [ ] Navigate east across the overworld to the mountain fortress on the far east coast
- [ ] Walk up to the Portal Gate (`^`) at (116, 70)
- [ ] **Without Key of Tides:** log shows "The gate is sealed. You need the Key of Tides to pass." — cannot enter
- [ ] **With Key of Tides:** log shows "The gate swings open! The portal to the next island beckons..."
- [ ] Key of Tides is consumed from inventory; gate tile mutates to grass (`.`) — permanently open

### Step 8.2 — Activate the portal
- [ ] Walk through the now-open gate into the mountain enclosure
- [ ] Step onto the Island Portal (`J`) at (116, 67)
- [ ] Log shows portal shimmer message
  *(Island 2 map not yet created — portal shows "coming soon" placeholder)*

---

## Quest Completion Checklist

All Island 1 quests and their givers:

| Quest | Giver | Turn-in | Reward |
|---|---|---|---|
| rising_tide | Acolyte | Acolyte | 150g + 400 XP |
| skeleton_menace | Acolyte (after rising_tide) | Acolyte | 100g + 250 XP |
| cleric_rite_of_bless | Acolyte (after skeleton_menace) | Acolyte | Bless spell |
| purge_dreamwake | Acolyte (after cleric_rite_of_bless) | Acolyte | 200g + 400 XP + ring |
| moonbloom_collection | Mira | Mira | 100g + 300 XP + potion + 10 Lirandel favor |
| potion_delivery | Maren | Mira | 100g + 250 XP |
| cave_explore | Bram | Bram | 200g + 500 XP |
| wolf_menace | Wolfie | Wolfie | 150g + 400 XP |
| iron_sword_collect | Wolfie (after wolf_menace) | Wolfie | 100g + 250 XP |
| find_the_hermit | Aldric | auto (on talking to Lyren) | 50g + 200 XP |
| alchemists_stone | Aldric (after find_the_hermit) | Aldric | 600 XP |
| mage_trial_lightning | Aldric (after alchemists_stone) | Aldric | Lightning Bolt spell |
| waterfall_haunting | Ghostly Woman | Ghostly Woman | 50g + 150 XP |

---

## Content Holes Status

| # | Status | Description |
|---|---|---|
| 1 | ✅ RESOLVED | Stonehaven consolidated — Wolfie and Aldric moved to Moonhaven |
| 2 | ✅ RESOLVED | Island Portal `J` placed at (116,67) in lirandel overworld, surrounded by mountain ring (`#`) with locked Portal Gate (`^`) at (116,70) — requires Key of Tides to open |
| 3 | ✅ RESOLVED | Boss (Corrupted Moon Guardian) fully implemented at dungeon depth 3 |
| 4 | ✅ RESOLVED | Moonbloom Herb is `lootable: true` tier 1 — drops from dungeon floor/chest loot |
| 5 | ✅ RESOLVED | Silver Locket is `lootable: true` tier 1 UNCOMMON — drops from dungeon loot |
| 6 | ✅ RESOLVED | All 7 orphan quests assigned to Moonhaven NPCs |
| 7 | ✅ RESOLVED | Boss favor awards (+20 Lirandel / +5 Pyralis) wired in DungeonController |
| 8 | ✅ RESOLVED | Moonbloom favor (+10 Lirandel) wired in Player.completeQuest() |
| 9 | ✅ RESOLVED | purge_dreamwake wired — depths 1–2 shrines call progressQuest(EXPLORE, "corrupted_shrine", 1) |
| 10 | ✅ RESOLVED | Ghostly Woman converted to full dialogue tree (routing, plea, searching, turn-in, at-rest nodes) |
| 11 | ✅ RESOLVED | Dagan (Mooncrest) shop populated: steel_sword, plate_mail, ring_protection, healing_potion, greater_healing, antidote |

---

## Regression Checks

Run these after any future changes:

- [ ] Save mid-quest and reload — all quest progress and flags preserved
- [ ] Player flags persist across save/load cycles
- [ ] Quest Log shows correct progress text for each quest type (KILL/COLLECT/DELIVER/TALK/EXPLORE)
- [ ] Dying in dungeon and respawning does not corrupt quest state
- [ ] Each quest grants the correct XP/gold/item/spell on completion
- [ ] Repeatable quest `wolf_menace` can be accepted again after completion
- [ ] All dialogue tree conditions route correctly (QUEST_ACTIVE, QUEST_COMPLETE, HAS_ITEM, FLAG_SET)
- [ ] Dialogue actions fire after text is fully revealed (not before typewriter)
