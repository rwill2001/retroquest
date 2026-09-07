// Two ways an NPC becomes unreachable, both found by hand in the strategy audit:
//   1. entombed  — no walkable tile within Chebyshev 1, so findNearbyNPC can never see them
//   2. shadowed  — an earlier NPC in the list covers every tile from which they are reachable
//                  (findNearbyNPC returns the FIRST match in list order)
// Sweep every map for both.
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";
const tj = JSON.parse(fs.readFileSync(R + 'data/tiles.json', 'utf8'));
const TL = Array.isArray(tj) ? tj : tj.tiles;
const T = {}; for (const x of TL) if (!(x.id in T)) T[x.id] = x;
const walk = c => { const d = T[c]; return d ? !!d.walkable : false; };

function reachable(m, sx, sy) {
  const t = m.tiles, H = t.length, W = t[0].length;
  const seen = Array.from({ length: H }, () => new Uint8Array(W));
  if (sx == null || sy == null || sy < 0 || sy >= H || sx < 0 || sx >= W) return seen;
  const q = [[sx, sy]]; seen[sy][sx] = 1;
  const blocked = new Set();
  for (const n of (m.npcs || [])) blocked.add(n.x + ',' + n.y);
  while (q.length) {
    const [x, y] = q.pop();
    for (const [dx, dy] of [[0,1],[0,-1],[1,0],[-1,0]]) {
      const nx = x+dx, ny = y+dy;
      if (nx<0||ny<0||nx>=W||ny>=H||seen[ny][nx]) continue;
      if (!walk(t[ny][nx]) || blocked.has(nx+','+ny)) continue;
      seen[ny][nx] = 1; q.push([nx, ny]);
    }
  }
  return seen;
}

const ARRIVAL = { lirandel:[20,48], pyralis:[20,92], zephyrion:[110,78], sylvandar:[61,156],
                  thalorax:[60,98], umbryn:[119,150], bellorak:[115,180] };

// Towns an island actually points at. The rest are orphans and their NPCs cannot be
// reached however they are placed.
const LINKED = new Set();
for (const f of fs.readdirSync(R + 'data/overworlds').filter(x => x.endsWith('.rfmap'))) {
  const m = JSON.parse(fs.readFileSync(R + 'data/overworlds/' + f, 'utf8'));
  for (const te of (m.townEntrances || [])) LINKED.add(te.townName.toLowerCase());
}

let problems = 0, orphaned = 0;
const orphanTowns = new Set();
for (const dir of ['overworlds', 'towns']) {
  for (const f of fs.readdirSync(R + 'data/' + dir).filter(x => x.endsWith('.rfmap'))) {
    const m = JSON.parse(fs.readFileSync(R + 'data/' + dir + '/' + f, 'utf8'));
    const npcs = m.npcs || [];
    if (!npcs.length) continue;
    const name = f.replace('.rfmap', '');
    const live = dir === 'overworlds' || LINKED.has(name.toLowerCase());
    if (!live) { orphaned += npcs.length; orphanTowns.add(name); continue; }
    const start = dir === 'overworlds' ? ARRIVAL[name] : [m.interiorEntryX, m.interiorEntryY];
    if (!start) continue;
    const seen = reachable(m, start[0], start[1]);
    const t = m.tiles, H = t.length, W = t[0].length;

    // tiles from which each NPC can be talked to
    const spots = npcs.map(n => {
      const s = [];
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const x = n.x + dx, y = n.y + dy;
        if (x<0||y<0||x>=W||y>=H) continue;
        if (seen[y][x]) s.push(x + ',' + y);
      }
      return s;
    });

    npcs.forEach((n, i) => {
      if (!spots[i].length) {
        problems++;
        console.log('ENTOMBED  ' + name.padEnd(20) + String(n.name).padEnd(26)
            + '(' + n.x + ',' + n.y + ') tile=' + (T[t[n.y] && t[n.y][n.x]] ? T[t[n.y][n.x]].name : '?'));
        return;
      }
      // shadowed: every spot that reaches me also reaches someone earlier in the list
      const covered = spots[i].every(sp => {
        for (let j = 0; j < i; j++) if (spots[j].includes(sp)) return true;
        return false;
      });
      if (covered) {
        problems++;
        const by = [];
        for (let j = 0; j < i; j++) if (spots[j].some(sp => spots[i].includes(sp))) by.push(npcs[j].name);
        console.log('SHADOWED  ' + name.padEnd(20) + String(n.name).padEnd(26)
            + '(' + n.x + ',' + n.y + ') behind ' + by.join(', '));
      }
    });
  }
}
console.log('\n' + problems + ' NPC(s) that can never be spoken to');
if (orphaned) console.log('(plus ' + orphaned + ' in ' + orphanTowns.size
    + ' town file(s) no island links to: ' + [...orphanTowns].join(', ') + ')');
process.exit(problems ? 1 : 0);
