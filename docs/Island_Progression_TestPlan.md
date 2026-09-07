# Island Progression Test Plan

> **Note:** Portal coordinates and key item IDs should be verified against current `.rfmap` files before test execution. Last validated: March 2026.

Critical path from Island 1 (Lirandel) to Island 7 (Bellorak) and the Cradle of Shards endgame.

## Key Ring System

As of 2026-03-28, all KEY-type items are stored on a **permanent key ring** (`Player.keyRing`) rather than in the 20-slot inventory. Keys are:
- **Never lost** -- immune to inventory full, cannot be sold/dropped
- **Permanent** -- once obtained, persist forever (survive save/load)
- **Separate storage** -- do not consume inventory slots
- **Backward compatible** -- old saves without `keyRing` field auto-initialize to empty list

### Files Changed

| File | Change |
|------|--------|
| `Player.java` | Added `keyRing` field, `addKey()`, `hasKey()`, `getKeyRing()` methods. `completeQuest()` routes KEY items to key ring. |
| `DungeonController.java` | All trial boss key awards use `player.addKey(id)` instead of `addItemAndProgress()`. |
| `NavigationController.java` | `playerHasItem()` checks key ring first. `tryUnlockTile()` checks key ring first. |
| `DialogueOverlay.java` | `GIVE_ITEM` action routes KEY items to `addKey()`. |
| `DialogueCondition.java` | `HAS_ITEM` condition checks key ring before inventory. |
| `data/quests.json` | Added `"itemRewardId": "key_of_gales"` to `zephyrion_trial`. |

## Fastest Route Summary

| Step | Island | Levels | Key Needed | Key Earned | How |
|------|--------|--------|------------|------------|-----|
| 1 | Lirandel | 1-5 | (none) | key_of_tides | Dreamwake Caverns L3 boss choice |
| 2 | Pyralis | 6-10 | key_of_tides | key_of_embers | Kill 3 Forge Golems for Emberpriest Cael |
| 3 | Zephyrion | 8-11 | key_of_embers | key_of_gales | Kill 6 enemies for Reva (zephyrion_trial) |
| 4 | Sylvandar | 12-15 | key_of_gales | key_of_roots | Kill 3 Blight Mothers for The Amber Sage |
| 5 | Thalorax | 16-19 | key_of_gales (reused) | key_of_depths | Pressure Temple boss (4-way choice) |
| 6 | Umbryn | 20-23 | key_of_depths | key_of_echoes | Archive of Tears L3 boss (3-way choice) |
| 7 | Bellorak | 24-27 | key_of_echoes | key_of_iron | Bellorak trial boss (3-way choice) |
| 8 | Cradle | 28-35 | key_of_iron | (endgame) | 8-level dungeon, 9 endings |

## Test Cases

### TC-01: Island 1 to Island 2 (Lirandel to Pyralis)

**Portal:** Lirandel overworld (55, 85), tile `G`
**Key:** `key_of_tides`

1. Create new character, grind to Level 5 in procedural dungeons
2. Walk to portal at (55, 85) WITHOUT key -- verify locked message appears
3. Enter Dreamwake Caverns, descend to Level 3
4. Defeat Corrupted Moon Guardian boss
5. Choose either Pure (destroy shard) or Corruption (absorb) path
6. Verify `key_of_tides` appears on key ring (not in inventory slots)
7. Walk to portal at (55, 85) -- verify teleport to Pyralis (28, 132)
8. Verify return portal `U` tile works freely back to Lirandel

**Rewards to verify (Pure path):** key_of_tides (key ring), MOONBLESSED boon, +20 Lirandel favor
**Rewards to verify (Corruption path):** key_of_tides (key ring), shard_of_corruption (inventory), +5 Pyralis favor

---

### TC-02: Island 2 to Island 3 (Pyralis to Zephyrion)

**Portal:** Pyralis overworld, tile `V`
**Key:** `key_of_embers`
**Quest:** `pyralis_crucible_trial` (giver: Emberpriest Cael, Forge Keep)

1. Travel to Forge Keep town on Pyralis
2. Talk to Emberpriest Cael -- accept "The Crucible Trial"
3. Walk to portal WITHOUT key -- verify locked message
4. Kill 3 Forge Golems (Level 8 monsters) in overworld or dungeon
5. Return to Emberpriest Cael -- verify quest completes
6. Verify `key_of_embers` on key ring (not in inventory)
7. Verify +20 Pyralis favor and PYRALIS_TEMPER boon granted
8. Walk to portal -- verify teleport to Zephyrion (110, 78)

---

### TC-03: Island 3 to Island 4 (Zephyrion to Sylvandar)

**Portal:** Zephyrion overworld (25, 165), tile `\uE068`
**Key:** `key_of_gales`
**Quest:** `zephyrion_trial` (giver: Reva)

**BUG FIX APPLIED:** Added `"itemRewardId": "key_of_gales"` to `zephyrion_trial` in `data/quests.json`. Previously, this quest only awarded the Chain Lightning spell but not the key, blocking all progression past Island 3.

1. Find Reva on Zephyrion -- accept "Trial of the Gale"
2. Walk to portal at (25, 165) WITHOUT key -- verify locked message: "...You need the Key of Gales."
3. Kill 6 enemies (any type) on Zephyrion
4. Verify quest auto-completes on 6th kill
5. Verify ALL rewards granted:
   - `key_of_gales` added to key ring (not inventory)
   - Chain Lightning spell learned
   - 600 XP
   - +20 Zephyrion favor
   - ZEPHYRION_GRACE boon (+1 AC)
6. Walk to portal at (25, 165) -- verify teleport to Sylvandar (65, 160)
7. Verify return portal `\uE069` works freely back to Zephyrion

---

### TC-04: Island 4 to Island 5 (Sylvandar to Thalorax)

**Portal:** Sylvandar overworld (162, 160), tile `\uE078`
**Key:** `key_of_gales` (REUSED from TC-03 -- no new key needed)

**Note:** The Sylvandar-to-Thalorax portal also requires `key_of_gales`. Since keys are permanent on the key ring, the same key from TC-03 opens this portal too.

1. Travel to Amber Grove town on Sylvandar
2. Talk to The Amber Sage -- accept "The Verdant Trial" (`sylvandar_verdant_trial`)
3. Kill 3 Blight Mothers (Level 15, found in Sylvandar overworld/dungeons)
4. Return to Amber Sage -- verify quest completes, receive `key_of_roots` on key ring
5. Verify +20 Sylvandar favor and SYLVANDAR_ROOTS boon
6. Walk to portal at (162, 160) -- verify it accepts `key_of_gales` (not key_of_roots)
7. Verify teleport to Thalorax (60, 98)
8. Verify return portal `\uE079` works freely back to Sylvandar

**Important:** `key_of_roots` is earned here but is NOT used for any overworld portal. It may be used inside the Cradle of Shards endgame dungeon (Level 4 section). Verify it persists on key ring.

---

### TC-05: Island 5 to Island 6 (Thalorax to Umbryn)

**Portal:** Thalorax overworld (170, 45), tile `\uE088`
**Key:** `key_of_depths`

1. Enter Pressure Temple dungeon on Thalorax
2. Reach the trial altar
3. Defeat Abyssal Leviathan (400 HP, 22 ATK)
4. Choose one of 4 moral paths -- ALL give `key_of_depths`
   - Choice 1 (One god rules): +10 Thalorax, TIDAL_ENDURANCE boon
   - Choice 2 (Destroy gods): +15 Thalorax / +5 Lirandel, TIDAL_ENDURANCE boon
   - Choice 3 (Wake Aqualon): +5 Thalorax, shard_of_corruption (NO boon)
   - Choice 4 (Refuse): +20 Thalorax, TIDAL_ENDURANCE boon
5. Verify `key_of_depths` on key ring
6. Walk to portal at (170, 45) -- verify teleport to Umbryn (115, 180)

---

### TC-06: Island 6 to Island 7 (Umbryn to Bellorak)

**Portal:** Umbryn overworld (60, 30), tile `\uE098`
**Key:** `key_of_echoes`

1. Enter Archive of Tears dungeon on Umbryn, reach Level 3
2. Defeat Grief Incarnate (500 HP, 24 ATK)
3. Choose one of 3 moral paths -- ALL give `key_of_echoes`
   - Choice 1 (Share burden): +20 Umbryn, UMBRYN_MEMORY boon
   - Choice 2 (Let forget): +10 Umbryn, shard_of_corruption
   - Choice 3 (Must endure): +5 Umbryn (no boon)
4. Verify `key_of_echoes` on key ring
5. Walk to portal at (60, 30) -- verify teleport to Bellorak (115, 180)

---

### TC-07: Island 7 to Cradle of Shards (Bellorak to Endgame)

**Portal:** Bellorak overworld (115, 95), tile `\uE09F`
**Key:** `key_of_iron`

1. Enter Bellorak trial dungeon
2. Defeat Seraphine the Eternal (multi-form boss)
3. Choose one of 3 moral paths -- ALL give `key_of_iron`
   - Choice 1 (Grant peace): +20 Bellorak, IRON_BROTHERHOOD boon
   - Choice 2 (Absorb power): +5 Bellorak, shard_of_corruption
   - Choice 3 (Free her): +15 Bellorak, seraphine_freed flag
4. Verify `key_of_iron` on key ring
5. Walk to portal at (115, 95) -- verify entry to Cradle of Shards

---

### TC-08: Key Ring System Verification

1. **No inventory slots used:** Earn any key, verify inventory slot count unchanged
2. **Duplicate prevention:** Trigger same trial twice (if possible) -- verify key not duplicated on ring
3. **Save/load persistence:** Save after earning keys, reload, verify `player.getKeyRing()` matches
4. **Old save compatibility:** Load a save file created before key ring feature -- verify no crash, keys initialize to empty list
5. **Dialogue HAS_ITEM check:** Talk to NPC with `HAS_ITEM` condition for a key -- verify condition evaluates true from key ring
6. **Locked door check:** Approach locked door requiring a key on the key ring -- verify door opens
7. **GIVE_ITEM dialogue action:** Trigger dialogue that gives a KEY item -- verify it goes to key ring, not inventory
8. **Quest reward:** Complete a quest with KEY itemRewardId -- verify key ring, not inventory

---

## Regression Notes

- **Key ring is permanent:** Keys cannot be lost, sold, dropped, or removed. This eliminates the old soft-lock risk.
- **Backward compatible:** `playerHasItem()` and `tryUnlockTile()` check key ring FIRST, then fall back to inventory scan. This means old saves with keys still in inventory slots will still work.
- **Return portals:** Every island has a free return portal (no key required). Test both directions.
- **key_of_gales reuse:** This single key opens TWO portals (Zephyrion->Sylvandar AND Sylvandar->Thalorax). This is intentional.
- **Non-key items unchanged:** shard_of_corruption and other non-KEY items still use normal inventory.

## Bug Fix Log

| Date | Issue | Fix | File |
|------|-------|-----|------|
| 2026-03-28 | `key_of_gales` never awarded -- `zephyrion_trial` quest missing `itemRewardId` | Added `"itemRewardId": "key_of_gales"` to quest definition | `data/quests.json:505` |
| 2026-03-28 | Keys consumed inventory slots, risking soft-lock if inventory full | Key ring system: separate permanent storage for KEY items | `Player.java`, `DungeonController.java`, `NavigationController.java`, `DialogueOverlay.java`, `DialogueCondition.java` |
