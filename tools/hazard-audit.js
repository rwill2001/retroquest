// Can the player leave every dungeon level without stepping on something that moves them?
//
// A pit drops you a level with no prompt; a chute and a teleporter send you elsewhere. If
// the only route to a level's stairs crosses one of those, leaving is a matter of luck
// rather than navigation. That was true of ember_caverns_1: the sole corridor out of
// Pyralis's dungeon had a pit in the middle of it, so the only way out was to fall deeper.
const fs = require('fs');
const R = require('path').resolve(__dirname, '..') + '/';
const tj = JSON.parse(fs.readFileSync(R + 'data/tiles.json', 'utf8'));
const TL = Array.isArray(tj) ? tj : tj.tiles;
const T = {}; for (const x of TL) if (!(x.id in T)) T[x.id] = x;
const walk = c => { const d = T[c]; return d ? !!d.walkable : false; };
const HAZARD = 'Ptc';                 // pit, teleporter, chute

let bad = 0, n = 0;
for (const f of fs.readdirSync(R + 'data/dungeons').filter(x => x.endsWith('.rfmap')).sort()) {
  const m = JSON.parse(fs.readFileSync(R + 'data/dungeons/' + f, 'utf8'));
  const t = m.tiles, H = t.length, W = t[0].length;
  const goals = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++)
    if (t[y][x] === 's' || t[y][x] === 'D') goals.push([x, y]);
  if (!goals.length) { console.log('!! ' + f.replace('.rfmap','') + ' has no stairs and no exit'); bad++; continue; }
  n++;
  const seen = Array.from({ length: H }, () => new Uint8Array(W));
  const q = [[m.interiorEntryX, m.interiorEntryY]];
  seen[m.interiorEntryY][m.interiorEntryX] = 1;
  while (q.length) {
    const [x, y] = q.pop();
    for (const [dx, dy] of [[0,1],[0,-1],[1,0],[-1,0]]) {
      const nx = x+dx, ny = y+dy;
      if (nx<0||ny<0||nx>=W||ny>=H||seen[ny][nx]) continue;
      const ch = t[ny][nx];
      if (!walk(ch) || HAZARD.includes(ch)) continue;
      seen[ny][nx] = 1; q.push([nx, ny]);
    }
  }
  if (!goals.some(([x, y]) => seen[y][x])) {
    console.log('!! ' + f.replace('.rfmap','').padEnd(24)
        + 'the stairs can only be reached by stepping on a pit, chute or teleporter');
    bad++;
  }
}
console.log(n + ' levels checked, ' + bad + ' where leaving is a matter of luck');
process.exit(bad ? 1 : 0);
