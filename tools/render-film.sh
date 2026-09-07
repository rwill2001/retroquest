#!/usr/bin/env bash
# render-film.sh — build the finished 3:15 MP4 from the recorded clips, offline.
#
#   sh tools/render-film.sh [out.mp4]
#
# Three passes:
#   1. PICTURE  — every clip trimmed to its slot and concatenated, with the ground colour
#                 filling the stretches that carry no gameplay (Acts 0, 5 and 7 are typography).
#   2. TITLES   — FilmTitles renders the whole typography layer to transparent PNGs.
#   3. COMPOSITE— overlay titles on picture, lay the score under it, encode.
#
# This exists because the browser export is a black box that can stall, and because a film that
# can only be produced by a GUI cannot be regenerated when a clip changes. Everything here is
# deterministic: same inputs, same output, no window ever opens.
#
# The shot table below is the authority, and it must agree with hype-video.jsx and
# docs/video/01-SCRIPT.md. Each row is:  start  duration  clip-id  in-point
# A clip-id of "-" holds the ground colour for that stretch.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${RQ_RECORD_OUT:-$REPO/out}"
FINAL="${1:-$OUT/_release/retroquest-hype-3m15.mp4}"
FPS=30
GROUND=0x060810

die() { printf 'render: %s\n' "$*" >&2; exit 1; }
native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

command -v ffmpeg >/dev/null 2>&1 || die "ffmpeg is not on PATH"
command -v java   >/dev/null 2>&1 || die "java is not on PATH"

# start  dur   clip            in
SHOTS="
0     5    -              0
5     3    -              0
8     13   s05-chargen    0
21    5    s05-chargen    6.9
26    7    s08-crawl      0
33    5    s09-overworld  0.3
38    5    s10-town       0
43    5    s11-dialogue   0
48    5    s12-shop       0
53    1.4  s13a-casino    3.0
54.4  1.4  s13b-sky       3.0
55.8  1.4  s13c-nature    3.0
57.2  1.4  s13d-war       3.0
58.6  1.4  s13e-arena     3.0
60    5    s14-divine     0
65    1.5  s09-overworld  4.2
66.5  2.5  s16-combat     0
69    6    s16-combat     2.5
75    7    s17-spellbook  0
82    5    s18-edge       0
87    4    s19-boss       0
91    4    s20-descent    0
95    7    s21-topdown    0
102   7    s22-wireframe  0
109   8    s23-bark       0
117   1.75 s24a-block     1
118.75 1.75 s24b-cobble   1
120.5 1.75 s24c-slab      1
122.25 1.75 s24d-cyclopean 1
124   8    s25-raycast    0
132   6    s26-lit        0
138   5    s27-cradle     0
143   8    -              0
151   4    s30-forge      0
155   5    s31-paint      0.3
160   4    s32-dialogue   0
164   1.333 s33-quest     1
165.333 1.333 s33-item    1
166.666 1.334 s33-monster  1
168   5    s33-tile       0
173   4    s32-image      0
177   3    s36-spawn      1
180   2.1  s30-forge      2.0
182.1 2.9  s10-town       0
185   5    -              0
190   5    -              0
"

work="$OUT/_film"
rm -rf "$work"; mkdir -p "$work/seg" "$(dirname "$FINAL")" || die "cannot create $work"

# ── 1. PICTURE ───────────────────────────────────────────────────────────────
printf 'render: picture — trimming segments\n'
list="$work/list.txt"; : > "$list"
n=0; total=0
while read -r start dur id in; do
  [ -z "${start:-}" ] && continue
  n=$((n + 1))
  seg="$(printf '%s/seg/%03d.mp4' "$work" "$n")"
  if [ "$id" = "-" ]; then
    ffmpeg -y -loglevel error -f lavfi -i "color=c=${GROUND}:s=1920x1080:r=${FPS}:d=${dur}" \
      -c:v libx264 -preset veryfast -crf 18 -pix_fmt yuv420p "$seg" || die "colour segment $n failed"
  else
    src="$OUT/$id/$id.mp4"
    [ -f "$src" ] || die "missing clip $id — run: sh tools/record.sh $id"
    # -ss before -i seeks fast; re-encoding guarantees an exact cut, because -c copy can only
    # cut on a keyframe and a 1.4s trim would silently come back the wrong length.
    ffmpeg -y -loglevel error -ss "$in" -i "$src" -t "$dur" \
      -vf "fps=${FPS},scale=1920:1080:flags=neighbor,setsar=1" \
      -c:v libx264 -preset veryfast -crf 18 -pix_fmt yuv420p -an "$seg" || die "trim $id failed"
  fi
  printf "file '%s'\n" "$(native_path "$seg")" >> "$list"
  total=$(awk -v a="$total" -v b="$dur" 'BEGIN{printf "%.3f", a + b}')
done <<< "$(printf '%s\n' "$SHOTS" | sed '/^[[:space:]]*$/d')"

printf 'render: picture — %s segments, %ss\n' "$n" "$total"
ffmpeg -y -loglevel error -f concat -safe 0 -i "$list" -c copy "$work/picture.mp4" || die "concat failed"

# ── 2. TITLES ────────────────────────────────────────────────────────────────
printf 'render: titles — rendering typography layer\n'
mkdir -p "$work/titles"
( cd "$REPO" && javac -d "$work" tools/FilmTitles.java ) || die "javac FilmTitles failed"
( cd "$REPO" && java -cp "$work" FilmTitles "$work/titles" ) | tail -3 || die "FilmTitles failed"

# ── 3. COMPOSITE ─────────────────────────────────────────────────────────────
score="$OUT/audio/score.wav"
[ -f "$score" ] || die "no score at $score — run: java -cp out/audio ChiptuneScore out/audio"

printf 'render: compositing\n'
ffmpeg -y -loglevel error \
  -i "$work/picture.mp4" \
  -framerate "$FPS" -i "$work/titles/frame_%06d.png" \
  -i "$score" \
  -filter_complex "[0:v][1:v]overlay=0:0:format=auto,format=yuv420p[v]" \
  -map "[v]" -map 2:a \
  -c:v libx264 -preset slow -crf 17 -c:a aac -b:a 192k -movflags +faststart \
  -shortest "$FINAL" || die "composite failed"

printf 'render: %s — %s, %s\n' "$FINAL" \
  "$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$FINAL")s" \
  "$(du -h "$FINAL" | cut -f1)"
