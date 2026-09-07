#!/usr/bin/env bash
# record.sh — render a RetroQuest shot to MP4.
#
#   sh tools/record.sh <shot-id> [...]   # one or more shots from tools/shots/
#   sh tools/record.sh --list            # what is available
#   sh tools/record.sh --keep-frames <id>
#   sh tools/record.sh --clean           # reclaim kept frame directories
#
# A shot is `tools/shots/<id>.shot`: sourced bash setting a handful of variables.
# The runner supplies everything that makes a render reproducible, so a shot only
# has to say what it is *of*.
#
# The capability lives in the game, not in this script: RetroRecorder paints
# GamePanel into a BufferedImage and writes frame_%06d.png. This wrapper picks
# the scene, runs it from the repo root, and hands the sequence to ffmpeg.
#
# Two things this script insists on, both learned the hard way in a sibling
# project's recorder:
#
#   - **The repo root as cwd.** Every data path in the game is resolved against
#     the working directory (`new File("data/...")`). Rendering from anywhere
#     else silently reads a different content tree, or creates an empty one, and
#     the clip comes back of the wrong world with nothing saying so.
#   - **Animations skipped by default.** `-Dretroquest.skipAnimations=true`
#     collapses the cinematics to a single frame. A dungeon entry animation
#     firing under a scene shot would eat the head of the clip.
#
# ffmpeg is invoked as a subprocess and never linked.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHOTS="$REPO/tools/shots"
OUT="${RQ_RECORD_OUT:-$REPO/out}"
KEEP_FRAMES=0

die() { printf 'record: %s\n' "$*" >&2; exit 1; }

list_shots() {
  printf 'shots:\n'
  local found=0
  for f in "$SHOTS"/*.shot; do
    [ -e "$f" ] || break
    found=1
    printf '  %-22s %s\n' "$(basename "$f" .shot)" "$(sed -n 's/^# *//p' "$f" | head -1)"
  done
  [ "$found" = 1 ] || printf '  (none in %s)\n' "$SHOTS"
}

clean() {
  local n=0
  for d in "$OUT"/*/frames; do
    [ -d "$d" ] || continue
    printf '  frames  %s\n' "$d"
    rm -rf "$d" && n=$((n + 1))
  done
  printf 'record: %d frame directories reclaimed\n' "$n"
}

record_one() {
  local id="$1"
  local shot="$SHOTS/$id.shot"
  [ -f "$shot" ] || die "no such shot: $id (try --list)"

  # Defaults. A shot overrides only what it cares about.
  local SCENE=dungeon ISLAND=lirandel DUNGEON="" LEVEL=1 MODE="" AT="" FACING=0 MOVE=""
  local OVERLAY="" TOWN="" MONSTER="" MONSTER_LEVEL="" XP="" LEARN_ALL="" EDITOR=""
  local DAMAGE="" NO_ESCAPE="" ANIMATION="" SKIP_ANIMATIONS=on FORGE_ZOOM=""
  local SECONDS_LEN=6 FPS=30 WIDTH=1920 HEIGHT=1080 CAMERA_RATE=1.0
  local MINIMAP=off SMOOTH=on REALTIME=on DESC="" UPSCALE="" ZOOM=0 CROP="" PAD=""
  # shellcheck disable=SC1090
  . "$shot"

  local out_dir="$OUT/$id"
  local frames="$out_dir/frames"
  rm -rf "$frames"
  mkdir -p "$frames" || die "cannot create $frames"

  local args=(
    --out "$frames" --w "$WIDTH" --h "$HEIGHT" --fps "$FPS" --seconds "$SECONDS_LEN"
    --scene "$SCENE" --island "$ISLAND" --level "$LEVEL" --facing "$FACING"
    --minimap "$MINIMAP" --smooth "$SMOOTH" --realtime "$REALTIME" --camera-rate "$CAMERA_RATE" --zoom "$ZOOM"
  )
  [ -n "$DUNGEON" ] && args+=(--dungeon "$DUNGEON")
  [ -n "$MODE" ]    && args+=(--mode "$MODE")
  [ -n "$AT" ]      && args+=(--at "$AT")
  [ -n "$MOVE" ]    && args+=(--move "$MOVE")
  [ -n "$OVERLAY" ] && args+=(--overlay "$OVERLAY")
  [ -n "$TOWN" ]    && args+=(--town "$TOWN")
  [ -n "$MONSTER" ] && args+=(--monster "$MONSTER")
  [ -n "$MONSTER_LEVEL" ] && args+=(--monster-level "$MONSTER_LEVEL")
  [ -n "$XP" ]      && args+=(--xp "$XP")
  [ -n "$LEARN_ALL" ] && args+=(--learn-all "$LEARN_ALL")
  [ -n "$EDITOR" ]   && args+=(--editor "$EDITOR")
  [ -n "$DAMAGE" ]   && args+=(--damage "$DAMAGE")
  [ -n "$NO_ESCAPE" ] && args+=(--no-escape "$NO_ESCAPE")
  [ -n "$ANIMATION" ] && args+=(--animation "$ANIMATION")
  [ -n "$FORGE_ZOOM" ] && args+=(--forge-zoom "$FORGE_ZOOM")

  printf 'record: %s — %s\n' "$id" "${DESC:-no description}"

  # The animation scene needs the cinematics to actually play. Everything else wants them
  # collapsed to a frame, so a dungeon entry does not eat the head of a scene shot.
  local skipflag="-Dretroquest.skipAnimations=true"
  [ "$SKIP_ANIMATIONS" = off ] && skipflag="-Dretroquest.skipAnimations=false"

  # cwd is the repo root, always. See the header note.
  ( cd "$REPO" && java "$skipflag" \
       -cp "target/classes;$(cat "$REPO/cp.txt")" \
       io.cannonforge.retroquest.core.RetroRecorder "${args[@]}" ) \
    > "$out_dir/$id.log" 2>&1
  local rc=$?

  grep -E '^recorder: (WARNING|[a-z])' "$out_dir/$id.log" | sed 's/^/  /'
  if [ "$rc" != 0 ] && [ "$rc" != 3 ]; then
    mv "$out_dir/$id.log" "$out_dir/$id-failed.log"
    die "$id FAILED — see $out_dir/$id-failed.log"
  fi

  local n
  n="$(find "$frames" -name 'frame_*.png' | wc -l | tr -d ' ')"
  local want=$(( SECONDS_LEN * FPS ))
  [ "$n" -gt 0 ] || die "$id produced no frames — see $out_dir/$id.log"
  if [ "$n" != "$want" ]; then
    printf 'record: %s — %s frames, expected %s\n' "$id" "$n" "$want" >&2
  fi

  local mp4="$out_dir/$id.mp4"
  # CRF 16 and yuv420p: high enough for a master, and a pixel format every NLE and
  # browser will actually open.
  #
  # UPSCALE renders small and blows the frames up with nearest-neighbour. The top-down
  # dungeon and the tile map draw at a *fixed* tile size no matter how big the frame is,
  # so asking the recorder for 1920x1080 directly leaves the grid swimming in black.
  # Rendering at a third of that and tripling it fills the frame — and integer
  # nearest-neighbour is the one scaler that does not smear 32x32 sprite art.
  #
  # The factor must be an exact integer. Nearest-neighbour at 2.4x duplicates some source
  # pixels twice and others three times, so monospace UI and 32x32 sprite edges shimmer
  # unevenly and clips cut together look like they came from different sources. Three
  # shots shipped that way before anyone noticed; this refuses rather than repeating it.
  local chain=""
  # CROP trims a frame to the region the UI actually fills. RetroForge's canvas has a fixed
  # tile viewport, so it cannot stretch to 1920x1080 and leaves black to the right and below —
  # cropping to its real box (1540x932) and padding to frame is lossless, where scaling to fit
  # would resample every glyph in the editor. The editor then reads as a window, which is what
  # it is.
  [ -n "$CROP" ] && chain="crop=${CROP}"

  if [ -n "$UPSCALE" ]; then
    local target_w="${UPSCALE%%:*}"
    local factor=$(( target_w / WIDTH ))
    if [ $(( factor * WIDTH )) != "$target_w" ] || [ "$factor" -lt 2 ]; then
      die "$id: UPSCALE ${UPSCALE} from ${WIDTH}x${HEIGHT} is not an exact integer factor.
       Legal shapes: 1920x1080 native, 960x540 at 2x, 640x360 at 3x."
    fi
    chain="${chain:+$chain,}scale=${UPSCALE}:flags=neighbor"
  fi

  # PAD centres the frame on the ground colour without touching a pixel of it.
  [ -n "$PAD" ] && chain="${chain:+$chain,}pad=${PAD}:(ow-iw)/2:(oh-ih)/2:0x060810"

  local vf=()
  [ -n "$chain" ] && vf=(-vf "$chain")
  if ! ffmpeg -y -loglevel error -framerate "$FPS" -i "$frames/frame_%06d.png" \
       "${vf[@]}" -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p "$mp4"; then
    printf 'record: %s FAILED — ffmpeg; frames kept at %s\n' "$id" "$frames" >&2
    return 1
  fi

  printf 'record: %s -> %s (%s frames, %s)\n' "$id" "$mp4" "$n" \
    "$(du -h "$mp4" | cut -f1)"
  if [ "$KEEP_FRAMES" = 1 ]; then
    printf 'record: frames kept at %s — sh tools/record.sh --clean reclaims them\n' "$frames"
  else
    rm -rf "$frames"
  fi
}

ids=()
ALL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --list)        list_shots; exit 0 ;;
    --clean)       clean; exit 0 ;;
    --keep-frames) KEEP_FRAMES=1 ;;
    --all)         ALL=1 ;;
    -*)            die "unknown option $1" ;;
    *)             ids+=("$1") ;;
  esac
  shift
done

# --all exists because out/ is gitignored: the shots are versioned and the footage is not,
# so anyone who checks this branch out has every recipe and no clips. This is the one
# command that turns one into the other. It paces to the wall clock, so it takes roughly
# as long as the footage it produces plus JVM startup per shot — a few minutes, not hours.
if [ "$ALL" = 1 ]; then
  for f in "$SHOTS"/*.shot; do
    [ -e "$f" ] || die "no shots in $SHOTS"
    ids+=("$(basename "$f" .shot)")
  done
fi

command -v ffmpeg >/dev/null 2>&1 || die "ffmpeg is not on PATH"
command -v java   >/dev/null 2>&1 || die "java is not on PATH"
[ -f "$REPO/cp.txt" ] || die "cp.txt missing — run: mvn dependency:build-classpath -Dmdep.outputFile=cp.txt"
[ -d "$REPO/target/classes" ] || die "target/classes missing — run: mvn compile"

if [ "${#ids[@]}" = 0 ]; then list_shots; exit 0; fi

fails=0
for id in "${ids[@]}"; do record_one "$id" || fails=$((fails + 1)); done
[ "$fails" = 0 ] || die "$fails shot(s) failed"
