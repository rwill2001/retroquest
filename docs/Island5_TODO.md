# Island 5 — The Abyssal Depths (Thalorax) — TODO

> **Status: IMPLEMENTED** — All tasks in this document have been completed. Retained for reference on design decisions and data specifications. See `RETROQUEST_DESIGN_DOC.md` §6 for current Island 5 status.

**Theme:** Cosmic horror, deep ocean, pressure, bioluminescence
**Level Range:** 16–19
**God:** Thalorax — "The Crushing Deep" — deep ocean, pressure, inevitability, hidden knowledge
**Design Source:** STORY_BIBLE.md lines 1374–1490

---

## Task 1: Create AbyssalSpriteGen.java — Generate All Island 5 Sprites

Generate 18 32x32 PNG sprites with a deep ocean color palette (dark blues, teals, bioluminescent greens/cyans).

**Overworld tiles (8):**
- `deep_ocean_floor` — dark seabed with subtle texture
- `pressure_vent` — volcanic vent with heat shimmer
- `coral_reef` — colorful coral formations
- `kelp_forest` — tall swaying kelp
- `bioluminescent_sand` — glowing sand patches
- `abyssal_rock` — dark jagged rock
- `bone_field` — scattered leviathan bones
- `pressure_ward` — glowing ward rune on stone

**Town tiles (5):**
- `dome_floor` — polished stone under dome
- `coral_wall` — living coral wall
- `kelp_platform` — woven kelp platform
- `pressure_glass` — transparent dome panel
- `bioluminescent_lamp` — glowing lamp fixture

**Monster sprites (5):**
- `pressure_hulk` — compressed armored deep creature
- `anglerfish_horror` — massive fish with lure
- `rune_scarred_shark` — shark with glowing runes
- `deep_coral_construct` — animated coral guardian
- `abyssal_wraith` — ghostly leviathan spirit

**Pattern:** `tools/SylvandarSpriteGen.java` — fixed Random seed, hardcoded RGB colors per sprite

**Output dirs:**
- `src/main/resources/tiles/overworld/`
- `src/main/resources/tiles/town/`
- `src/main/resources/tiles/monsters/`

---

## Task 2: Register Island 5 Tiles in TileRegistry (PUA \uE070–\uE07E)

Add ~15 new tile chars in Unicode PUA range `\uE070`–`\uE07E` to `TileRegistry.migrateNewTiles()`.

| Char | ID | Name | Category | Walkable | Sprite Key |
|------|----|------|----------|----------|------------|
| \uE070 | DOCEAN | Deep Ocean Floor | Overworld | true | overworld/deep_ocean_floor |
| \uE071 | PVENT | Pressure Vent | Overworld | true (damage) | overworld/pressure_vent |
| \uE072 | CREEF | Coral Reef | Overworld | false | overworld/coral_reef |
| \uE073 | KFOREST | Kelp Forest | Overworld | true | overworld/kelp_forest |
| \uE074 | BSAND | Bioluminescent Sand | Overworld | true | overworld/bioluminescent_sand |
| \uE075 | AROCK | Abyssal Rock | Overworld | false | overworld/abyssal_rock |
| \uE076 | BFIELD | Bone Field | Overworld | true | overworld/bone_field |
| \uE077 | PWARD | Pressure Ward | Overworld | false | overworld/pressure_ward |
| \uE078 | PORTAL_THALORAX | Portal to Thalorax | Special | true | (island_portal effect) |
| \uE079 | PORTAL_RET5 | Return Portal to Sylvandar | Special | true | (island_portal effect) |

Town tiles use existing town tile system (dome_floor, coral_wall, etc. registered as town category).

**File:** `src/main/java/io/cannonforge/retroquest/core/TileRegistry.java`

---

## Task 3: Add 5 Monsters to monsters.json (Levels 16–19)

Scale stats from Island 4 (L12-15). Follow existing JSON structure.

| Monster | Level | HP | DMG | AC | SpellResist | Spells | CastChance | Power | Gold | XP |
|---------|-------|----|-----|-----|-------------|--------|------------|-------|------|----|
| pressure_hulk | 16 | 280 | 16 | 19 | 20 | — | 0% | 0 | 58 | 650 |
| deep_coral_construct | 16 | 240 | 15 | 18 | 15 | — | 0% | 0 | 55 | 620 |
| anglerfish_horror | 17 | 250 | 17 | 15 | 25 | Fear, Sleep | 40% | 16 | 62 | 700 |
| rune_scarred_shark | 18 | 300 | 19 | 17 | 25 | Drain | 35% | 15 | 68 | 780 |
| abyssal_wraith | 19 | 340 | 20 | 17 | 40 | Drain, Fear, Fireball | 55% | 22 | 75 | 880 |

**File:** `data/monsters.json`

---

## Task 4: Add Island 5 Items to items.json

**Quest items:**
- `key_of_depths` — MISC, legendary, lootable: false — Thalorax trial reward, opens portal to Island 6
- `shard_of_corruption` — MISC, legendary, lootable: false — optional trial reward (choose "Aqualon wakes")
- `pressure_ward_fragment` — MISC, rare, maxStackSize: 5, lootable: false — collect quest item for Cracking Dome
- `seraphine_journal_5` — MISC, rare, lootable: false — found in Pressure Temple, lore breadcrumb

**Equipment (tier 5):**
- `pressure_blade` — WEAPON, value: 9, price: 75, uncommon
- `abyssal_plate` — ARMOR, value: 8, price: 70, spellResist: 8, uncommon
- `coral_shield` — SHIELD, value: 7, price: 65, spellResist: 12, uncommon
- `deep_trident` — WEAPON, value: 8, price: 70, uncommon
- `abyssal_healing_draught` — POTION, value: 80, price: 18, stackSize: 99

**File:** `data/items.json`

---

## Task 5: Add Island 5 Quests to quests.json

| Quest ID | Type | Target | Giver | XP | Gold | Notes |
|----------|------|--------|-------|----|------|-------|
| cracking_dome | COLLECT | 3 pressure_ward_fragment | The Drowned Engineer | 1000 | 60 | Urgent collection quest |
| the_deep_reading | TALK | Presskeeper Nym (after temple) | Presskeeper Nym | 1100 | 0 | Lore/investigation quest |
| thalorax_trial | KILL | (boss/trial) | — | 1200 | 0 | God trial, gives key_of_depths |
| varsa_choice | TALK | Tide-Priestess Varsa | Tide-Priestess Varsa | 600 | 40 | Side quest, Lirandel callback |
| abyssal_wraith_bounty | KILL | 5 Abyssal Wraiths | Presskeeper Nym | 550 | 40 | Repeatable bounty |

**File:** `data/quests.json`

---

## Task 6: Create AbyssalDepthsGen.java — Generate Overworld Map

Generate `data/overworlds/thalorax.rfmap` (~230x190 tiles).

**Terrain layout:**
- Base: deep ocean floor everywhere
- Central large atoll cluster (where towns are)
- Coral reef borders around atolls (impassable barriers with gaps)
- Kelp forests in mid-depth zones
- Pressure vent clusters (damage tiles, hazard zones)
- Boneyard Trench: long bone_field region in the south
- Bioluminescent sand paths connecting towns (road equivalent)
- Abyssal rock formations (impassable, scattered)
- Pressure ward ring around Pressure Temple area

**Key locations:**
- Abyssport entrance (~65, 95) — 30x30 domed city
- Kelp Towers entrance (~165, 105) — 25x25 vertical settlement
- Brightcoral entrance (~115, 38) — 20x20 coral village
- Pressure Temple dungeon entrance (central-south)
- Portal FROM Sylvandar (edge, requires key_of_gales)
- Return portal TO Sylvandar (free, near spawn)

**Difficulty grid:** 16-19 scaling with distance from Abyssport

**Pattern:** `tools/SylvandarGen.java` — fixed Random seed, ellipse + noise for island shape

---

## Task 7: Create Town Generators — Abyssport, Kelp Towers, Brightcoral

### AbyssportGen.java (30x30)
- Domed underwater city, pressure-ward walls forming dome outline
- Central plaza, Drowned Engineer's workshop, inn, shop
- **NPCs:**
  - The Drowned Engineer — quest giver (Cracking Dome), has dialogue tree
  - Tide-Priestess Varsa — quest giver (Varsa's Choice), Lirandel spy
  - Abyssport Shopkeeper — sells tier 5 gear
  - Abyssport Innkeeper — standard inn

### KelpTowersGen.java (25x25)
- Vertical kelp settlement, kelp_platform floors, vine_curtain doorways
- Multi-level feel with ramps/stairs
- **NPCs:**
  - Presskeeper Nym — quest giver (Deep Reading, Wraith Bounty)
  - Kelp Towers merchant
  - Lore NPC (ancient kelp-keeper)

### BrightcoralGen.java (20x20)
- Beautiful bioluminescent coral village, glowing lamps everywhere
- Eerie undertone — coral slowly replacing residents
- **NPCs:**
  - Coral Builder elder — lore/dialogue about transformation
  - Brightcoral merchant
  - Coral-child NPC (unsettling innocence)

**All NPCs need dialogue trees** following `DialogueTree` model from Island 4 towns.

---

## Task 8: Wire Sylvandar → Thalorax Portal

**On Sylvandar overworld (sylvandar.rfmap):**
- Place portal tile (new char, e.g. \uE078) at appropriate edge location
- Step effect: `island_portal` with `requiredKeyId: "key_of_gales"`, destination coords on thalorax map
- Locked message: "The portal pulses with crushing pressure but remains sealed. You need the Key of Gales."

**On Thalorax overworld (thalorax.rfmap):**
- Entry point where player arrives from Sylvandar
- Return portal (free, \uE079) near spawn area, destination back to Sylvandar

**Pattern:** Zephyrion→Sylvandar portal in NavigationController

---

## Task 9: Wire Favor Awards for Island 5 (Thalorax)

| Event | Favor Change |
|-------|-------------|
| Complete "The Deep Reading" | +10 Thalorax |
| Trial — Choose "one god rules" | +10 Thalorax |
| Trial — Choose "destroy all gods" | +15 Thalorax, +5 Lirandel |
| Trial — Choose "Aqualon wakes" | +5 Thalorax (he fears this) |
| Trial — Refuse to choose | +20 Thalorax (highest) |
| Varsa — Extract her | +10 Lirandel |
| Varsa — Send her deeper | +5 Thalorax, -5 Lirandel |
| Complete "Cracking Dome" | +5 Thalorax |

**Files:** `NpcController.java`, `DungeonController.java`

---

## Task 10: Update Memory and Documentation

- Create `project_abyssal_depths.md` in memory directory with implementation details
- Update `MEMORY.md` index with Island 5 entry
- Verify STORY_BIBLE.md alignment — note any design deviations
- Document tile char assignments, portal coordinates, NPC locations
