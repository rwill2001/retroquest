// Is each island survivable by the character it is meant to open with?
//
// Models the real combat maths — d20 + level + DEX vs monster AC for the player, d20 +
// monster level vs player AC coming back, damage reduction on every landed hit — against
// what each island's spawn bands can actually roll. "Margin" is rounds-to-die divided by
// rounds-to-kill: below 1 the monster wins a straight fight.
const fs = require('fs');
const R = require('path').resolve(__dirname, '..') + '/';
const M = JSON.parse(fs.readFileSync(R + 'data/monsters.json', 'utf8'));
const itemsDoc = JSON.parse(fs.readFileSync(R + 'data/items.json', 'utf8'));
const ITEMS = Array.isArray(itemsDoc) ? itemsDoc : itemsDoc.items;
const byId = id => ITEMS.find(i => i.id === id) || { value: 0, price: 0, rarity: 'COMMON' };
const RARITY = { COMMON: 1, UNCOMMON: 1.5, RARE: 2.5, EPIC: 5, LEGENDARY: 12 };
const cost = id => { const i = byId(id); return Math.round(i.price * (RARITY[i.rarity] || 1)); };

const clamp = v => Math.max(0.05, Math.min(0.95, v));

// The character an island opens with: previous island's shop gear, that island's level.
const ARRIVE = [
  { isle:'lirandel',  lvl:1,  wep:'rusty_dagger',      arm:['leather_armor'] },
  { isle:'pyralis',   lvl:6,  wep:'steel_sword',       arm:['chain_mail','wooden_shield','leather_cap'] },
  { isle:'zephyrion', lvl:10, wep:'obsidian_blade',    arm:['forged_steel_plate','ember_shield','iron_helm'] },
  { isle:'sylvandar', lvl:13, wep:'stormcaller_blade', arm:['windweave_armor','gale_shield','zephyr_helm'] },
  { isle:'thalorax',  lvl:17, wep:'verdant_blade',     arm:['rootweave_armor','amber_shield','zephyr_helm'] },
  { isle:'umbryn',    lvl:21, wep:'pressure_blade',    arm:['abyssal_plate','coral_shield','steel_helm'] },
  { isle:'bellorak',  lvl:25, wep:'shadow_blade',      arm:['memory_mail','echo_shield','steel_helm'] },
];
// What that island itself sells, and the level you should reach on it.
const LOCAL = {
  lirandel:  { lvl:5,  wep:'iron_sword',       arm:['chain_mail','wooden_shield'] },
  pyralis:   { lvl:9,  wep:'obsidian_blade',   arm:['forged_steel_plate','ember_shield'] },
  zephyrion: { lvl:12, wep:'stormcaller_blade',arm:['windweave_armor','gale_shield','zephyr_helm'] },
  sylvandar: { lvl:16, wep:'verdant_blade',    arm:['rootweave_armor','amber_shield'] },
  thalorax:  { lvl:20, wep:'pressure_blade',   arm:['abyssal_plate','coral_shield'] },
  umbryn:    { lvl:24, wep:'shadow_blade',     arm:['memory_mail','echo_shield'] },
  bellorak:  { lvl:28, wep:'iron_warsword',    arm:['battle_plate','champion_shield'] },
};
// Reachable spawn-difficulty band per island, measured from the maps.
const BANDS = { lirandel:[0,10], pyralis:[11,20], zephyrion:[15,24], sylvandar:[23,30],
                thalorax:[31,38], umbryn:[39,46], bellorak:[47,54] };

const CON = 13, STR = 12, DEX = 12;   // an average kept roll
function build(lvl, wep, arm) {
  const armour = arm.reduce((s, id) => s + byId(id).value, 0);
  return {
    lvl,
    hp:  (CON + 10) + (lvl - 1) * (3 + Math.floor((CON - 10) / 3)),
    dmg: byId(wep).value + (STR - 10) + Math.floor(lvl / 3),
    ac:  10 + Math.floor((DEX - 10) / 2) + Math.min(armour, lvl + 2),
    dr:  Math.max(0, Math.floor((CON - 10) / 3)) + Math.floor(Math.max(0, armour - (lvl + 2)) / 4),
    kit: cost(wep) + arm.reduce((s, id) => s + cost(id), 0),
  };
}
function foes(d) {
  const t = 1 + Math.round(d * 49 / 99);
  return M.filter(m => !m.friendly && m.hp > 0 && m.level >= t - 3 && m.level <= t + 2);
}
function worst(p, band) {
  const seen = new Map();
  for (let d = band[0]; d <= band[1]; d++) for (const m of foes(d)) seen.set(m.name, m);
  const rows = [];
  for (const m of seen.values()) {
    const pHit = clamp((21 - (m.ac - p.lvl - Math.floor((DEX - 10) / 2))) / 20);
    const mHit = clamp((21 - (p.ac - m.level)) / 20);
    const rk = m.hp / (p.dmg * pHit);
    const rd = p.hp / (Math.max(1, m.damage - p.dr) * mHit);
    rows.push({ n: m.name, l: m.level, margin: rd / rk });
  }
  rows.sort((a, b) => a.margin - b.margin);
  return rows;
}

console.log('ON ARRIVAL — the character the island opens with, against everything it can meet');
console.log('island      lvl  hp  dmg  AC   foes  beaten by   worst');
for (const a of ARRIVE) {
  const p = build(a.lvl, a.wep, a.arm);
  const rows = worst(p, BANDS[a.isle]);
  const lose = rows.filter(r => r.margin < 1).length;
  console.log(a.isle.padEnd(11) + String(p.lvl).padStart(3) + String(p.hp).padStart(5)
    + String(p.dmg).padStart(5) + String(p.ac).padStart(4) + String(rows.length).padStart(7)
    + String(lose).padStart(11) + '   ' + rows[0].n + ' (L' + rows[0].l + ') ' + rows[0].margin.toFixed(2));
}
console.log('');
console.log('AFTER SHOPPING THERE — the same island once you have bought its own gear');
console.log('island      lvl  hp  dmg  AC   foes  beaten by   worst                    kit costs');
for (const a of ARRIVE) {
  const l = LOCAL[a.isle];
  const p = build(l.lvl, l.wep, l.arm);
  const rows = worst(p, BANDS[a.isle]);
  const lose = rows.filter(r => r.margin < 1).length;
  console.log(a.isle.padEnd(11) + String(p.lvl).padStart(3) + String(p.hp).padStart(5)
    + String(p.dmg).padStart(5) + String(p.ac).padStart(4) + String(rows.length).padStart(7)
    + String(lose).padStart(11) + '   ' + (rows[0].n + ' (L' + rows[0].l + ') ' + rows[0].margin.toFixed(2)).padEnd(26)
    + p.kit + ' g');
}
