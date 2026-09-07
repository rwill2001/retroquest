# Item & Inventory System — RetroQuest

**Last updated:** September 2026

## 1. Overview

The item system spans 7 files:

- **`Item.java`** — Core data model (type, slot, value, rarity, tier, effects, spell binding).
- **`ItemSlot.java`** — Enum for equipment slot categories.
- **`Rarity.java`** — Enum controlling drop frequency, price scaling, and UI color.
- **`InventorySlot.java`** — Wrapper for one inventory position (item + stack quantity).
- **`ItemRegistry.java`** — Singleton CRUD registry backed by `data/items.json`.
- **`InventoryOverlay.java`** — In-panel overlay for viewing/using/equipping/dropping items.
- **`ShopOverlay.java`** — In-panel BUY/SELL overlay with tab switching.

---

## 2. Item Data Model (`Item.java`)

### Type Enum

```
WEAPON, ARMOR, POTION, RING_PROTECTION, RING_REGEN,
SCROLL, WAND, KEY, MISC, RING, AMULET, HELM, SHIELD
```

`RING_PROTECTION` and `RING_REGEN` are kept for save-file compatibility; new ring items should use `RING` with an explicit `Effect`.

### Equipment Slots (`ItemSlot` Enum)

| Slot | Used For | Player Field |
|------|----------|-------------|
| WEAPON | Main-hand weapon | `player.weapon` |
| ARMOR | Body armor | `player.armor` |
| HELM | Head slot | `player.helm` |
| AMULET | Neck slot | `player.amulet` |
| SHIELD | Off-hand shield | `player.shield` |
| RING | Rings (two slots supported) | `player.ring1`, `player.ring2` |
| CONSUMABLE | Potions, scrolls, wands | inventory only |
| KEY | Quest keys | inventory only |
| MISC | Gems, quest objects, etc. | inventory only |

### Core Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | String | auto-generated | Unique key; `"auto_" + timestamp` if null |
| `name` | String | — | Display name |
| `type` | Type | — | Item category |
| `slotType` | ItemSlot | derived from type | Equipment slot |
| `value` | int | — | For weapons: damage bonus. For armor/shield/helm: AC bonus (adds to `getAC()`). For potions: HP restored. |
| `price` | int | `value * 3` | Base sell price |
| `description` | String | auto | Flavor text |
| `spriteName` | String | `"unknown.png"` | PNG filename |
| `maxStackSize` | int | 1 | Stack limit (up to 99 for consumables) |
| `tier` | int | 1 | Power tier 1–12+; used by `LootGenerator` |
| `rarity` | Rarity | COMMON | Drop frequency and price multiplier |
| `lootable` | boolean | true | If false, never drops from chests |
| `shopAvailable` | boolean | false | If true, appears in shop stock |
| `effects` | List\<Effect\> | empty | Passive effects when equipped (PROTECTION adds to AC, REGENERATION heals) |
| `spellName` | String | null | For SCROLL/WAND: bound spell name |
| `charges` | int | 0 | For WAND: number of uses |
| `spellResist` | int | 0 | 0–50% spell resistance bonus when equipped |

### Value vs. Effects — One Mechanism, Not Two

`value` is no longer a second AC path. `Player.getAC()` and `Player.getDamage()` read **only** the
`activeEffects` list (plus boons); `armor.getValue()` is never added directly.

`Item.rebuildEffects()` closes the gap after deserialization: when `value > 0` and the effects list
has no effect of the matching type, it *injects* one —

| Type | Injected effect |
|---|---|
| WEAPON | `DAMAGE` of `value` |
| ARMOR, SHIELD, HELM, AMULET, RING | `PROTECTION` of `value` |
| POTION | `HEALING` of `value` |

An explicit effect in the JSON always wins, so a hand-authored item is unaffected. Note that an
item carrying **both** `value` and an explicit PROTECTION effect gets only the explicit one — the
injection is skipped. Do not use both to express two stacking sources.

It also migrates the two legacy ring types (`RING_PROTECTION`, `RING_REGEN` rebuild their effect
from `value`) and converts a legacy `spellResist` field into a `SPELL_RESIST` effect.

Remember the cap: the summed PROTECTION and boon AC is clamped to `level + 2` in `getAC()`, so
piling on protective gear past that point does nothing.

### Price Calculation

`getPrice()` returns `basePrice * rarity.getPriceMultiplier()` (rounded to int).

### Post-Load Migration

`rebuildEffects()` is called after Gson deserialization. It clears the effects list and re-attaches implied effects for the two legacy ring types only:
- `RING_PROTECTION` → adds PROTECTION effect with `value`.
- `RING_REGEN` → adds REGENERATION effect with `value`.

All other types (AMULET, HELM, SHIELD, RING) carry their `effects` list through Gson directly and are **not** modified by `rebuildEffects()`.

---

## 3. Rarity System (`Rarity.java`)

| Rarity | Weight | Price Mult | Color | Display |
|--------|--------|-----------|-------|---------|
| COMMON | 100 | 1.0× | (200,220,255) | Common |
| UNCOMMON | 40 | 1.5× | (0,200,80) | Uncommon |
| RARE | 15 | 2.5× | (0,120,255) | Rare |
| EPIC | 4 | 5.0× | (160,50,255) | Epic |
| LEGENDARY | 1 | 12.0× | (255,160,30) | Legendary |

- **Weight** controls relative drop frequency in `LootGenerator`'s weighted random selection.
- **Price multiplier** is applied on top of base price at buy time.

---

## 4. Inventory (`InventorySlot.java`, `Player.java`)

- Player has **20 fixed slots** (`InventorySlot[20]`).
- Each slot holds an `Item` reference + `quantity` (1–maxStackSize).
- `addItem()` first tries to stack with an existing matching item, then finds the first empty slot.
- Starting inventory: one Healing Potion (25 HP).

### Equipment Slots (Paper Doll)

The player has **7 dedicated equipment slots**. All are shown in `InventoryOverlay`'s paper-doll panel.

All seven contribute through `activeEffects` only — `rebuildEffects()` on `Player` concatenates
every equipped item's effects list, and `getAC()` / `getDamage()` read that.

| Slot | Default | Contribution |
|------|---------|----------------|
| Weapon | Rusty Dagger | DAMAGE effects |
| Armor | Leather Armor | PROTECTION effects |
| Helm | empty | PROTECTION effects |
| Amulet | empty | PROTECTION effects |
| Shield | empty | PROTECTION effects |
| Ring 1 | empty | any effects |
| Ring 2 | empty | any effects |

### Equipping Logic (`Player.equip()`)

- WEAPON → replaces weapon; old weapon returned to inventory.
- ARMOR → replaces armor; old armor returned to inventory.
- HELM → replaces helm; old helm returned to inventory.
- AMULET → replaces amulet; old amulet returned to inventory.
- SHIELD → replaces shield; old shield returned to inventory.
- RING → fills ring1, then ring2, then displaces ring1 (round-robin).
- Calls `rebuildEffects()` to update all active passive effects from all 7 slots.

---

## 5. Item Usage (`InventoryOverlay`)

| Item Type | Action on Use |
|-----------|---------------|
| POTION | Heals `item.getValue()` HP; consumed (decrements stack). **Antidote**: cures poison (only consumed if poisoned). |
| SCROLL | Teaches bound spell permanently; consumed. Resurrection scroll charges ward instead |
| WAND | Casts bound spell; decrements charges; removed when depleted |
| WEAPON | Equips to weapon slot; old weapon returned to inventory |
| ARMOR | Equips to armor slot; old armor returned to inventory |
| HELM | Equips to helm slot; old helm returned to inventory |
| AMULET | Equips to amulet slot; old amulet returned to inventory |
| SHIELD | Equips to shield slot; old shield returned to inventory |
| RING / RING_PROTECTION / RING_REGEN | Equips to ring1 then ring2 then displaces ring1 |
| Other | "You have no use for that item right now." |

### Drop Flow

Press **D** → confirmation prompt → **Y** to confirm → item removed from slot.

### Detect Magic Highlight

When `player.isDetectMagicActive()`, equippable items in the list pulse with a purple tint (alpha oscillates via sin wave).

---

## 6. Shop System (`ShopOverlay`)

### Opening

- If NPC has specific `itemIds`: loads those items from registry.
- Otherwise: loads all items where `isShopAvailable() == true`, sorted by base price.

### Buy Flow

1. Check gold sufficient.
2. Check inventory not full.
3. Deduct gold, add item to inventory.

### Sell Flow

1. Uses `SellEntry` record mapping display-row to actual inventory slot index.
2. Remove item, add gold, refresh sell list.

### Sell Price Formula

Sell price is now a **fraction of the buy price**, never a multiple of `value`. `Item.getSellPrice()`:

```java
int buy = getPrice();                       // price * rarity.getPriceMultiplier()
if (buy <= 0 || type == null) return 0;     // zero-price quest tokens are worthless
int pct = switch (type) {
    case POTION, SCROLL, WAND -> 25;
    case KEY, MISC            -> 35;
    default                   -> 40;        // weapons, armour, helms, shields, rings, amulets
};
return Math.max(1, buy * pct / 100);
```

Charisma then adjusts both sides in `ShopOverlay`, with `chaBonus = max(0, CHA - 10)`:

- Buy multiplier `1.0 - chaBonus * 0.02` (CHA 20 → 0.80)
- Sell multiplier `1.0 + chaBonus * 0.01` (CHA 20 → 1.10)

Even at the best case the player sells at `0.40 × 1.10 = 0.44` of base and buys at `0.80`, so
buy-then-sell arbitrage is impossible.

---

## 7. Item Registry (`ItemRegistry.java`)

- Backed by `data/items.json` (Gson, pretty-printed) — **126 items** today.
- Type breakdown: SCROLL 32, MISC 26, WEAPON 14, KEY 11, SHIELD 10, POTION 9, ARMOR 9, HELM 5,
  AMULET 5, RING_PROTECTION 3, RING 1, RING_REGEN 1. No `WAND` items exist despite the enum value.
- Rarity breakdown: UNCOMMON 47, RARE 28, COMMON 24, EPIC 14, LEGENDARY 13. Tiers 1-9 are used;
  nothing is tier 10 or above.
- `createDefaultItems()` only runs when the registry loads empty and seeds 26 items — it is
  effectively dead code against the shipped 126-item catalogue. The full catalogue is maintained
  through RetroForge's Item Editor.
- A `data/items.json` that exists but fails to parse leaves the registry marked `loadFailed`, and
  saves are then refused so the file is not overwritten.
- `getAllItems()` returns unmodifiable list.
- `getAllShopItems()` returns shop-available items sorted by base price.
- `getAllLootable()` returns lootable items for `LootGenerator`.
- CRUD: `addItem()`, `removeItem(int)`, `updateItem(int, Item)` — all auto-save.

### Item Categories in items.json

| Category | Count | Tiers | Notes |
|----------|-------|-------|-------|
| Potions | ~3 | 1–3 | Healing Potion shop-available |
| Weapons | ~4 | 1–5 | Iron Sword, Mace, etc. |
| Armor | 2 | 1–2 | Chain Mail (value 1), Plate Mail (value 3) |
| Shields | 5 | 1–8 | Wooden → Shield of Reflection |
| Helms | 5 | 1–8 | Leather Cap → Great Helm |
| Amulets | 5 | 2–10 | All AC/regen/spellResist via effects |
| Rings | ~5 | 1–3 | Mix of RING_REGEN, RING_PROTECTION |
| Scrolls | 32 | 1–12 | One per spell; tiers match spell level |
| Wands | 3 | 2–5 | Lightning (3), Fire (4), Sleep (3) charges |
| Keys/Quest | 3 | — | lootable=false |

### Key Ring Integration

KEY-type items are automatically routed to `Player.keyRing` instead of the 20-slot inventory:
- `completeQuest()` routes KEY item rewards to the key ring.
- `DialogueOverlay` GIVE_ITEM action routes KEY items to `addKey()`.
- Keys cannot be sold, dropped, or lost.
- `NavigationController.playerHasItem()` and `DialogueCondition` HAS_ITEM check the key ring before inventory.

---

## 8. Loot Integration

See `CombatSystem.md` §6 for loot generation details. Key points:
- **Monster loot** (`LootGenerator.getRandomLootForMonster`): base `tier = max(1, (level + 1) / 2)`, capped at player level.
- **Chest loot** (`LootGenerator.getRandomLoot`): base `tier = max(1, (depth + 4) / 5)`, capped at player level.
- **Player level cap**: No loot can drop with a tier above the player's current level, from any source.
- **Uniform tier distribution**: A random tier from 1 to `min(contextTier, playerLevel)` is chosen with equal probability (e.g., level 5 → 20% chance per tier 1–5).
- Fallback chain: exact tier match → ±1 window (capped) → any lootable ≤ maxTier → safety net.
- Weight boost at tier ≥ 5: RARE ×2, EPIC ×4, LEGENDARY ×6.
- 7.35% drop chance on monster kill.
- **Shop level gating**: Shops only display items with `tier ≤ playerLevel`.
- Shields, helms, and amulets are all `lootable: true` and distributed across tiers 1–10 so they appear at appropriate dungeon depths.

---

## 9. Item Editor (`ItemEditorDialog.java`)

Launched from RetroForge → EDITORS → Item Editor. Fields:

| Field | Notes |
|-------|-------|
| ID | Unique string key; auto-generated on new item |
| Name | Display name |
| Type | `Item.Type` enum (WEAPON, ARMOR, HELM, AMULET, SHIELD, etc.) |
| Slot | `ItemSlot` enum; should match type |
| Value | AC bonus for armor/shield/helm; damage bonus for weapons; HP for potions |
| Price | Base price (before rarity multiplier) |
| Tier | 1–20; controls depth at which item appears in loot |
| Rarity | Drop frequency and price scaling |
| Max Stack | Stack limit; >1 meaningful for consumables only |
| Lootable | Whether item drops from dungeon chests/monsters |
| In Shop | Whether item appears in shop buy list |
| Sprite | PNG filename (relative to `tiles/items/`) |
| Spell Resist % | 0–50; contributes to player spell resistance when equipped |
| Effects | List of passive effects (PROTECTION/REGENERATION/HEALING + value). Add/remove via +/− buttons. Preserved on save. |
| Spell | For SCROLL/WAND only: bound spell name |
| Charges | For WAND only: number of uses |
| Desc | Flavor text; should accurately reflect value, effects, and spellResist |

The form panel scrolls vertically when the window is too small to show all fields.

---

## 10. Known Gaps

1. **Sell pricing inconsistency** — sell formula is hardcoded per type, not derived from buy price. Rings always sell for 45g regardless of tier.
2. **Wand charges not shown in shop** — shop detail line doesn't display charge count for wands.
3. **Generic `RING` sell price** — `RING` type (with explicit effects) falls through to 10g flat instead of 45g like the legacy `RING_PROTECTION`/`RING_REGEN` types.
4. **Missing item sprites** — most item sprite filenames reference non-existent PNGs; the `unknown.png` fallback is used at runtime.
5. **No effect editing UI for values** — effects can be added/removed in the editor but the value of an existing effect cannot be edited in-place; delete and re-add instead.
