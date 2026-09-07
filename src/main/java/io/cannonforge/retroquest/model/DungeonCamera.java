package io.cannonforge.retroquest.model;

/**
 * Where the Island 6 renderer is actually looking from, as opposed to where the player logically
 * stands.
 *
 * <p>Every other dungeon view snaps: you press left and the world is instantly ninety degrees
 * over. A raycaster can render any angle, so the camera keeps its own floating position and
 * heading and eases towards the player's tile and facing over a few frames. Gameplay is untouched
 * — the step, the encounter roll and the tile effect all still resolve the instant the key is
 * pressed. Only the picture lags, and only briefly.
 *
 * <p>When smoothing is off, or when the player is moved somewhere a walk could not have taken them
 * (a teleporter, a chute, a level change, a load), the camera snaps instead. Easing across half a
 * map would look like flying.
 */
public class DungeonCamera {

    /** A step or turn resolves visually in this long. Long enough to read, short enough to spam. */
    private static final float MOVE_MS = 130f;
    private static final float TURN_MS = 150f;

    /** Beyond this many tiles of travel, easing is wrong — the player was moved, not walked. */
    private static final float SNAP_DISTANCE = 1.9f;

    /** Closer than this to the target, the ease is over. See the note in {@link #update}. */
    private static final float POSITION_SNAP = 0.006f;
    private static final float ANGLE_SNAP    = 0.004f;

    private static final float TWO_PI = (float) (Math.PI * 2);

    private float x, y, angle;
    private float targetX, targetY, targetAngle;
    private boolean smooth = true;

    /** Places the camera with no interpolation. */
    public void snapTo(int tileX, int tileY, int facing) {
        targetX = x = tileX + 0.5f;
        targetY = y = tileY + 0.5f;
        targetAngle = angle = angleFor(facing);
    }

    /** Aims the camera at a tile, easing unless the jump is too far to be a walk. */
    public void moveTo(int tileX, int tileY) {
        targetX = tileX + 0.5f;
        targetY = tileY + 0.5f;
        float dx = targetX - x, dy = targetY - y;
        if (!smooth || dx * dx + dy * dy > SNAP_DISTANCE * SNAP_DISTANCE) {
            x = targetX;
            y = targetY;
        }
    }

    /** Turns the camera to a facing, taking the short way round. */
    public void turnTo(int facing) {
        targetAngle = angleFor(facing);
        if (!smooth) {
            angle = targetAngle;
            return;
        }
        // Unwrap the current angle to within half a turn of the target, so a 270-degree
        // interpolation becomes the 90-degree one the player actually asked for.
        while (angle - targetAngle > Math.PI)  angle -= TWO_PI;
        while (targetAngle - angle > Math.PI)  angle += TWO_PI;
    }

    /** Advances the easing. Returns true while the camera still has somewhere to be. */
    public boolean update(long deltaMs) {
        if (!smooth) {
            x = targetX; y = targetY; angle = targetAngle;
            return false;
        }
        boolean moving = false;

        // Easing is exponential, which means it approaches the target but never arrives. Left
        // alone the last one percent takes as long as the first eighty and the camera creeps for
        // a second after it looks stopped — so anything inside the snap thresholds is finished.
        float mt = Math.min(1f, deltaMs / MOVE_MS);
        float dx = targetX - x, dy = targetY - y;
        if (dx * dx + dy * dy > POSITION_SNAP * POSITION_SNAP) {
            x += dx * mt;
            y += dy * mt;
            moving = true;
        } else {
            x = targetX; y = targetY;
        }

        float tt = Math.min(1f, deltaMs / TURN_MS);
        float da = targetAngle - angle;
        if (Math.abs(da) > ANGLE_SNAP) {
            angle += da * tt;
            moving = true;
        } else {
            angle = targetAngle;
        }
        return moving;
    }

    /** 0 = north (-Y), 1 = east (+X), 2 = south (+Y), 3 = west (-X). */
    private static float angleFor(int facing) {
        return (float) (facing * Math.PI / 2.0);
    }

    public float getX()     { return x; }
    public float getY()     { return y; }
    public float getAngle() { return angle; }

    public boolean isSmooth()            { return smooth; }
    public void    setSmooth(boolean s)  { smooth = s; if (!s) update(0); }
    public boolean toggleSmooth()        { setSmooth(!smooth); return smooth; }

    /** True while the picture has not caught up with the player. */
    public boolean isSettling() {
        float dx = targetX - x, dy = targetY - y;
        return dx * dx + dy * dy > POSITION_SNAP * POSITION_SNAP
            || Math.abs(targetAngle - angle) > ANGLE_SNAP;
    }
}
