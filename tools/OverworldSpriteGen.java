import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Sprite generator for RetroQuest overworld tiles.
 * Run from the project root:
 *   javac tools/OverworldSpriteGen.java -d tools/
 *   java -cp tools OverworldSpriteGen
 *
 * Writes PNGs to src/main/resources/tiles/overworld/
 */
public class OverworldSpriteGen {

    static Random rng = new Random(42);

    // WARNING: Do not re-enable — sprites may have been hand-edited in RetroForge.
    // Helper methods below are kept for reference. Use a one-shot SpriteGen for targeted regeneration.
    public static void main(String[] args) {
        System.out.println("Disabled to protect hand-edited sprites. Use a one-shot SpriteGen instead.");
    }

    // ── Desert ────────────────────────────────────────────────────────────────
    // Sandy dune ripples + small cactus
    static BufferedImage desert() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(210, 178, 72);
        Color light = new Color(232, 202, 98);
        Color dark  = new Color(185, 152, 52);
        Color crest = new Color(242, 215, 115);
        Color shade = new Color(172, 138, 44);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Sand grain noise
        rng.setSeed(101);
        for (int i = 0; i < 130; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Dune ripple lines (gentle sine wave horizontals)
        for (int dy : new int[]{4, 11, 18, 25}) {
            for (int x = 0; x < 32; x++) {
                int y = dy + (int)(1.8 * Math.sin(x * Math.PI * 2 / 18.0));
                if (y >= 0 && y < 31) {
                    g.setColor(crest); g.fillRect(x, y,   1, 1);
                    g.setColor(shade); g.fillRect(x, y+1, 1, 1);
                }
            }
        }

        // Cactus (centered, upper half)
        int cx = 16, cy = 7;
        Color cd = new Color(22, 82, 28), cm = new Color(38, 115, 38), cl = new Color(58, 148, 50);
        // Trunk
        g.setColor(cm); g.fillRect(cx-1, cy, 3, 11);
        g.setColor(cl); g.drawLine(cx, cy, cx, cy+10);
        g.setColor(cd); g.drawLine(cx+1, cy, cx+1, cy+10);
        // Left arm
        g.setColor(cm); g.fillRect(cx-5, cy+3, 4, 2); g.fillRect(cx-5, cy+1, 2, 4);
        g.setColor(cl); g.drawLine(cx-5, cy+1, cx-5, cy+4);
        g.setColor(cd); g.drawLine(cx-2, cy+3, cx-2, cy+4);
        // Right arm
        g.setColor(cm); g.fillRect(cx+2, cy+2, 4, 2); g.fillRect(cx+4, cy+1, 2, 5);
        g.setColor(cl); g.drawLine(cx+5, cy+1, cx+5, cy+4);
        g.setColor(cd); g.drawLine(cx+3, cy+2, cx+3, cy+4);
        // Shadow on sand
        g.setColor(new Color(165, 132, 40, 110));
        g.fillRect(cx+1, cy+11, 5, 2);

        g.dispose(); return img;
    }

    // ── Marsh ─────────────────────────────────────────────────────────────────
    // Murky dark water with algae scum and reed tufts
    static BufferedImage marsh() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color waterD = new Color(25, 48, 28),  waterM = new Color(35, 65, 38);
        Color mudD   = new Color(62, 50, 25),  scum   = new Color(48, 82, 32);
        Color reedD  = new Color(28, 78, 22),  reedM  = new Color(45, 108, 32), reedL = new Color(65, 138, 45);
        Color seed   = new Color(132, 105, 38);

        g.setColor(waterD); g.fillRect(0, 0, 32, 32);

        // Water texture noise
        rng.setSeed(202);
        for (int i = 0; i < 90; i++) {
            g.setColor(rng.nextBoolean() ? waterM : mudD);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1+rng.nextInt(2), 1);
        }

        // Algae/scum patches
        for (int[] p : new int[][]{{3,3,9,5},{19,1,7,4},{1,13,6,5},{21,11,8,5},{7,21,7,4},{22,19,7,5},{11,27,9,4}}) {
            g.setColor(scum); g.fillOval(p[0], p[1], p[2], p[3]);
        }

        // Reed tufts
        for (int[] r : new int[][]{{5,7},{14,4},{24,9},{6,18},{20,16},{11,25},{27,23},{3,26}}) {
            int rx=r[0], ry=r[1];
            g.setColor(reedD); g.fillRect(rx-1, ry+5, 3, 3);
            // Stems
            g.setColor(reedM); g.drawLine(rx,   ry,   rx,   ry+5);
            g.setColor(reedL); g.drawLine(rx-1, ry+1, rx-1, ry+6);
            g.setColor(reedM); g.drawLine(rx+1, ry+2, rx+1, ry+6);
            // Seed heads (brown tips)
            g.setColor(seed);          g.fillRect(rx,   ry,   1, 2);
            g.setColor(new Color(105,80,28)); g.fillRect(rx-1, ry+1, 1, 2);
        }

        g.dispose(); return img;
    }

    // ── Dirt Road ─────────────────────────────────────────────────────────────
    // Packed earth, two wheel ruts, scattered pebbles
    static BufferedImage dirtRoad() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(160, 120, 62), light = new Color(182, 142, 80), dark  = new Color(125, 92, 42);
        Color worn  = new Color(175, 138, 72);
        Color rutM  = new Color(102, 72, 28),  rutD  = new Color(78,  54, 18);
        Color stone = new Color(138, 128, 115);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Earth texture
        rng.setSeed(303);
        for (int i = 0; i < 110; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Center worn strip
        for (int y = 0; y < 32; y++) {
            int j = (int)(0.6 * Math.sin(y * 0.38));
            g.setColor(worn); g.drawLine(13+j, y, 18+j, y);
        }

        // Left wheel rut
        for (int y = 0; y < 32; y++) {
            int j = (int)(0.9 * Math.sin(y * 0.32 + 0.5));
            g.setColor(rutD);  g.fillRect( 7+j, y, 1, 1);
            g.setColor(rutM);  g.fillRect( 8+j, y, 2, 1);
            g.setColor(rutD);  g.fillRect(10+j, y, 1, 1);
        }

        // Right wheel rut
        for (int y = 0; y < 32; y++) {
            int j = (int)(0.9 * Math.sin(y * 0.32 + 1.2));
            g.setColor(rutD);  g.fillRect(21+j, y, 1, 1);
            g.setColor(rutM);  g.fillRect(22+j, y, 2, 1);
            g.setColor(rutD);  g.fillRect(24+j, y, 1, 1);
        }

        // Edge stones
        rng.setSeed(304);
        for (int i = 0; i < 7; i++) {
            int sx = rng.nextBoolean() ? rng.nextInt(5) : 27+rng.nextInt(5);
            int sy = rng.nextInt(30);
            g.setColor(stone); g.fillRect(sx, sy, 2, 1);
            g.setColor(new Color(158,148,135)); g.fillRect(sx, sy-1, 2, 1);
        }

        g.dispose(); return img;
    }

    // ── Wooden Bridge ─────────────────────────────────────────────────────────
    // Water on E/W edges, vertical planks span N-S, rope railings
    static BufferedImage bridge() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color wD=new Color(18,42,120), wM=new Color(28,62,162), wL=new Color(45,88,200);
        Color pD=new Color(92,56,18),  pM=new Color(125,80,28),  pL=new Color(152,102,40);
        Color gap=new Color(52,28,6);
        Color rope=new Color(165,125,55), ropeD=new Color(115,85,30);
        Color postD=new Color(72,42,10), postM=new Color(105,65,20);

        // Water background
        g.setColor(wD); g.fillRect(0, 0, 32, 32);

        // Water chevron pattern on left (x=0-5) and right (x=26-31)
        for (int side = 0; side < 2; side++) {
            int bx = side == 0 ? 0 : 26;
            for (int row = -1; row < 9; row++) {
                int baseY = row * 4;
                for (int x = 0; x < 6; x++) {
                    int chevY = baseY + (x < 3 ? x : 6-x);
                    for (int dy = 0; dy < 2; dy++) {
                        int py = chevY + dy;
                        if (py < 0 || py >= 32) continue;
                        g.setColor(dy == 0 ? wL : wM);
                        g.fillRect(bx+x, py, 1, 1);
                    }
                }
            }
        }

        // Planks (x=6-25): 3px wide, 1px gap, run full height
        for (int x = 6; x <= 25; ) {
            for (int y = 0; y < 32; y++) {
                boolean grained = (y % 5 < 2);
                g.setColor(grained ? pL : pM); g.fillRect(x,   y, 1, 1);
                g.setColor(pM);                g.fillRect(x+1, y, 1, 1);
                g.setColor(grained ? pM : pD); g.fillRect(x+2, y, 1, 1);
            }
            x += 3;
            if (x <= 25) { g.setColor(gap); g.drawLine(x, 0, x, 31); x++; }
        }

        // Rope railings top (y=1-2) and bottom (y=29-30)
        g.setColor(rope);  g.drawLine(6,1,25,1);  g.drawLine(6,29,25,29);
        g.setColor(ropeD); g.drawLine(6,2,25,2);  g.drawLine(6,30,25,30);

        // Railing posts
        for (int px : new int[]{6, 13, 20, 25}) {
            g.setColor(postD); g.fillRect(px, 0, 2, 4);   g.fillRect(px, 28, 2, 4);
            g.setColor(postM); g.drawLine(px, 0, px, 3);  g.drawLine(px, 28, px, 31);
        }

        g.dispose(); return img;
    }

    // ── Tall Grass ────────────────────────────────────────────────────────────
    // Dense vertical blades, darker/more crowded than regular grass
    static BufferedImage tallGrass() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base = new Color(16, 68, 16);
        Color bD   = new Color(20, 88, 18), bM = new Color(35, 120, 25), bL = new Color(52, 150, 38);
        Color tip  = new Color(72, 172, 52);
        Color seed = new Color(148, 128, 38);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        rng.setSeed(404);
        // Dense blade pass — every column gets multiple blades
        for (int pass = 0; pass < 4; pass++) {
            for (int x = 0; x < 32; x++) {
                int bx  = x + rng.nextInt(3) - 1; if (bx<0||bx>31) bx=x;
                int by  = rng.nextInt(32);
                int bh  = 7 + rng.nextInt(9);
                int lean= rng.nextInt(3) - 1;
                if (by+bh > 31) bh = 31-by;
                if (bh < 3) continue;
                for (int i = 0; i < bh; i++) {
                    int dx = bx + lean*i/bh; if (dx<0||dx>31) continue;
                    float t = (float)i/bh;
                    g.setColor(t < 0.35f ? bD : t < 0.7f ? bM : bL);
                    g.fillRect(dx, by+(bh-i), 1, 1);
                }
                // Tip
                int tipX = bx+lean; if (tipX>=0&&tipX<32) { g.setColor(tip); g.fillRect(tipX, by, 1, 1); }
                // Seed head
                if (rng.nextInt(5)==0 && by>1) {
                    g.setColor(seed); g.fillRect(tipX>=0&&tipX<32?tipX:bx, by-1, 1, 2);
                }
            }
        }

        g.dispose(); return img;
    }

    // ── Snow ──────────────────────────────────────────────────────────────────
    // Soft white drifts with blue shadows, glinting sparkle points
    static BufferedImage snow() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base  = new Color(225, 234, 245);
        Color light = new Color(248, 252, 255);
        Color sh1   = new Color(195, 208, 228);
        Color sh2   = new Color(172, 188, 215);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Drift ripple lines
        for (int dy : new int[]{4, 10, 17, 24}) {
            for (int x = 0; x < 32; x++) {
                int y = dy + (int)(1.4 * Math.sin(x * Math.PI / 13.0));
                if (y >= 0 && y < 31) {
                    g.setColor(sh2);   g.fillRect(x, y,   1, 1);
                    g.setColor(light); g.fillRect(x, y+1, 1, 1);
                }
            }
        }

        // Snow texture noise
        rng.setSeed(505);
        for (int i = 0; i < 90; i++) {
            int px=rng.nextInt(32), py=rng.nextInt(32);
            g.setColor(rng.nextInt(3)==0 ? light : rng.nextBoolean() ? sh1 : sh2);
            g.fillRect(px, py, 1, 1);
        }

        // Glinting sparkles
        rng.setSeed(506);
        for (int i = 0; i < 10; i++) {
            int px=2+rng.nextInt(28), py=2+rng.nextInt(28);
            g.setColor(new Color(255,255,255));
            g.fillRect(px, py, 1, 1);
            g.setColor(sh1);
            g.fillRect(px-1,py,1,1); g.fillRect(px+1,py,1,1);
            g.fillRect(px,py-1,1,1); g.fillRect(px,py+1,1,1);
        }

        g.dispose(); return img;
    }

    // ── Hills ─────────────────────────────────────────────────────────────────
    // Three rolling hill silhouettes with sky behind, lit crests, shadowed slopes
    static BufferedImage hills() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color sky   = new Color(88, 145, 195);
        Color farM  = new Color(62, 102, 45),  farL  = new Color(80, 128, 58);
        Color gD    = new Color(38, 82, 24),    gM    = new Color(58, 112, 36);
        Color gL    = new Color(80, 142, 52),   gHL   = new Color(105, 168, 70);
        Color shad  = new Color(25, 58, 15),    gnd   = new Color(30, 68, 18);

        g.setColor(sky); g.fillRect(0, 0, 32, 32);

        // Far hill (right side, x-peak ~22)
        for (int x = 0; x < 32; x++) {
            double t = (x-22.0)/16.0; int top = (int)(8+8*t*t); top=Math.max(6,Math.min(22,top));
            for (int y=top; y<32; y++) {
                float d=(float)(y-top)/(32-top);
                g.setColor(d<0.12f ? farL : farM); g.fillRect(x,y,1,1);
            }
        }

        // Left hill (x-peak ~5)
        for (int x = 0; x < 26; x++) {
            double t = (x-5.0)/12.0; int top = (int)(3+18*t*t); top=Math.max(2,Math.min(30,top));
            for (int y=top; y<32; y++) {
                float d=(float)(y-top)/(32-top);
                Color c = d<0.08f ? gHL : d<0.25f ? gL : d<0.55f ? gM : gD;
                g.setColor(c); g.fillRect(x,y,1,1);
            }
        }

        // Right hill (x-peak ~27)
        for (int x = 8; x < 32; x++) {
            double t = (x-27.0)/11.0; int top = (int)(5+16*t*t); top=Math.max(4,Math.min(30,top));
            for (int y=top; y<32; y++) {
                float d=(float)(y-top)/(32-top);
                Color c = d<0.08f ? gHL : d<0.22f ? gL : d<0.52f ? gM : shad;
                g.setColor(c); g.fillRect(x,y,1,1);
            }
        }

        // Dark ground strip at bottom
        rng.setSeed(606);
        for (int x=0; x<32; x++) { g.setColor(gnd); g.drawLine(x, 30+rng.nextInt(2), x, 31); }

        g.dispose(); return img;
    }

    // ── Sand ──────────────────────────────────────────────────────────────────
    // Light beach sand — gentle ripples, pebbles, wet waterline streak
    static BufferedImage sand() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color base   = new Color(208, 188, 125);
        Color light  = new Color(228, 212, 148);
        Color dark   = new Color(180, 160, 100);
        Color ripHi  = new Color(218, 200, 138);
        Color ripSh  = new Color(188, 168, 105);
        Color pebble = new Color(155, 142, 115);
        Color wet    = new Color(172, 152, 92);

        g.setColor(base); g.fillRect(0, 0, 32, 32);

        // Grain noise
        rng.setSeed(707);
        for (int i = 0; i < 170; i++) {
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Gentle ripple marks
        for (int dy : new int[]{5, 12, 19, 26}) {
            for (int x = 0; x < 32; x++) {
                int y = dy + (int)(0.9 * Math.sin(x * Math.PI / 11.0));
                if (y>=0 && y<31) {
                    g.setColor(ripHi); g.fillRect(x,y,  1,1);
                    g.setColor(ripSh); g.fillRect(x,y+1,1,1);
                }
            }
        }

        // Pebbles
        rng.setSeed(708);
        for (int i = 0; i < 9; i++) {
            int px=2+rng.nextInt(28), py=2+rng.nextInt(28);
            g.setColor(pebble); g.fillRect(px,py,2,1);
            g.setColor(light);  g.fillRect(px,py-1,2,1);
            g.setColor(new Color(125,112,88)); g.fillRect(px+1,py+1,1,1);
        }

        // Wet sand streak near top (waterline)
        for (int x=0; x<32; x++) {
            int y=2+(int)(1.5*Math.sin(x*Math.PI/16.0));
            g.setColor(wet); g.fillRect(x,y,1,2);
        }

        g.dispose(); return img;
    }

    // ── Flower Field ──────────────────────────────────────────────────────────
    // Rich green base, scattered coloured 4-petal flowers
    static BufferedImage flowers() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color gD=new Color(32,108,26), gM=new Color(48,135,38), gL=new Color(65,158,52);
        Color yc=new Color(255,240,60), wh=new Color(248,248,240);
        Color center=new Color(255,228,50), cDk=new Color(195,158,22);

        // Grass base
        g.setColor(gM); g.fillRect(0,0,32,32);
        rng.setSeed(808);
        for (int i=0;i<200;i++) {
            g.setColor(rng.nextBoolean()?gL:gD);
            g.fillRect(rng.nextInt(32),rng.nextInt(32),1,1);
        }

        Color[][] palettes = {
            {new Color(232,88,148), center},   // pink + yellow center
            {yc, new Color(255,255,255)},       // yellow + white center
            {wh, yc},                           // white + yellow center
            {new Color(178,122,232), center},   // lavender + yellow
            {new Color(255,108,42), cDk},       // orange + dark center
            {new Color(215,48,48), cDk},        // red + dark center
        };

        int[][] pos = {
            {3,4},{10,2},{18,5},{27,3},{28,12},{22,8},{14,7},{5,11},
            {1,18},{8,21},{16,15},{25,19},{29,23},{20,26},{11,28},{4,26},
            {7,29},{27,28},{13,20},{22,23},{5,16},{15,24},{24,12},{9,14}
        };
        rng.setSeed(809);
        for (int[] fp : pos) {
            int fx=fp[0], fy=fp[1];
            if (fx<1||fx>30||fy<1||fy>30) continue;
            Color[] pal = palettes[rng.nextInt(palettes.length)];
            Color petal=pal[0], ctr=pal[1];
            // 4-petal cross
            g.setColor(petal);
            g.fillRect(fx,fy-1,1,1); g.fillRect(fx,fy+1,1,1);
            g.fillRect(fx-1,fy,1,1); g.fillRect(fx+1,fy,1,1);
            // Diagonal petals on larger flowers
            if (rng.nextInt(3)==0) {
                g.fillRect(fx-1,fy-1,1,1); g.fillRect(fx+1,fy-1,1,1);
                g.fillRect(fx-1,fy+1,1,1); g.fillRect(fx+1,fy+1,1,1);
            }
            // Center
            g.setColor(ctr); g.fillRect(fx,fy,1,1);
        }

        g.dispose(); return img;
    }

    // ── Ruined Pillar ─────────────────────────────────────────────────────────
    // Grassy ground, fallen column drum (top-down), cracked stone slab, rubble
    static BufferedImage ruin() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color gD=new Color(35,85,25), gM=new Color(52,112,36), gL=new Color(70,138,50);
        Color slabB=new Color(115,108,98), slabD=new Color(72,68,60), slabL=new Color(155,148,135);
        Color crack=new Color(50,46,38), moss=new Color(48,90,32);
        Color colMd=new Color(132,125,112), colLt=new Color(158,150,136), colDk=new Color(82,76,68);

        // Grassy base
        g.setColor(gM); g.fillRect(0,0,32,32);
        rng.setSeed(909);
        for (int i=0;i<120;i++) {
            g.setColor(rng.nextBoolean()?gD:gL); g.fillRect(rng.nextInt(32),rng.nextInt(32),1,1);
        }

        // Stone slab base (lower half, x=3-28, y=18-29)
        g.setColor(slabB); g.fillRect(3,18,26,12);
        g.setColor(slabL); g.drawLine(3,18,28,18); g.drawLine(3,18,3,29);
        g.setColor(slabD); g.drawLine(3,29,28,29); g.drawLine(28,18,28,29);
        // Slab grout lines
        g.setColor(crack);
        g.drawLine(3,23,28,23); g.drawLine(15,18,15,29);
        // Stone faces
        g.setColor(new Color(122,115,104));
        g.fillRect(4,19,10,4); g.fillRect(16,19,11,4);
        g.fillRect(4,24,10,4); g.fillRect(16,24,11,4);
        // Slab crack
        g.setColor(crack); g.drawLine(7,19,13,28); g.drawLine(8,19,14,28);
        // Moss in crack
        g.setColor(moss); g.fillRect(9,21,1,2); g.fillRect(11,24,1,2); g.fillRect(12,26,1,1);

        // Fallen column drum (top-down circle, cx=14, cy=12, r=9)
        int cx=14, cy=12, r=9;
        g.setColor(colMd); g.fillOval(cx-r,cy-r,r*2,r*2);
        // Fluting grooves (radiating lines)
        g.setColor(crack);
        for (int i=0;i<8;i++) {
            double a=Math.PI*2*i/8.0;
            g.drawLine(cx,cy,(int)(cx+(r-1)*Math.cos(a)),(int)(cy+(r-1)*Math.sin(a)));
        }
        // Highlight arc (upper-left)
        g.setColor(colLt); g.drawArc(cx-r,cy-r,r*2-1,r*2-1,100,130);
        // Shadow arc (lower-right)
        g.setColor(colDk); g.drawArc(cx-r+1,cy-r+1,r*2-3,r*2-3,280,130);
        // Outer ring
        g.setColor(colDk); g.drawOval(cx-r,cy-r,r*2-1,r*2-1);
        // Diagonal crack across face
        g.setColor(crack); g.drawLine(cx-6,cy-4,cx+4,cy+6);
        g.setColor(new Color(38,34,28)); g.drawLine(cx-5,cy-4,cx+3,cy+6);
        // Moss patches
        g.setColor(moss);
        g.fillRect(cx-6,cy-3,2,2); g.fillRect(cx+3,cy+2,2,2); g.fillRect(cx-2,cy+5,2,1);

        // Rubble chunks (scattered around)
        rng.setSeed(910);
        for (int[] r2 : new int[][]{{26,4},{29,10},{0,7},{2,14},{27,17},{1,22},{28,25},{5,4},{24,29}}) {
            int rx=r2[0], ry=r2[1]; if (rx>29||ry>29) continue;
            int rw=1+rng.nextInt(3), rh=1+rng.nextInt(2);
            g.setColor(colMd); g.fillRect(rx,ry,rw,rh);
            g.setColor(colLt); g.fillRect(rx,ry,1,1);
            g.setColor(colDk); g.fillRect(rx+rw-1,ry+rh-1,1,1);
        }

        g.dispose(); return img;
    }

    // ── Poison Grass ──────────────────────────────────────────────────────────
    // Sickly green-yellow grass with purple toxic spores / dark drip patches
    static BufferedImage poisongrass() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Base palette — muted sickly greens
        Color bgD  = new Color(28,  62,  12);   // dark base
        Color bgM  = new Color(48,  88,  22);   // mid grass
        Color bgL  = new Color(72, 110,  30);   // lighter blade
        Color yel  = new Color(140, 150,  18);  // yellow-green tinge (toxic)
        Color yelB = new Color(175, 175,  22);  // brighter yellow-green tip

        // Toxic drip/pool colours
        Color toxD = new Color(55,  28,  70);   // dark purple pool
        Color toxM = new Color(90,  45, 110);   // mid purple spore
        Color toxL = new Color(140, 80, 160);   // bright spore dot

        // Fill with dark base
        g.setColor(bgD); g.fillRect(0, 0, 32, 32);

        // Grass texture noise
        rng.setSeed(1111);
        for (int i = 0; i < 200; i++) {
            int c = rng.nextInt(4);
            g.setColor(c==0 ? bgM : c==1 ? bgL : c==2 ? yel : bgD);
            g.fillRect(rng.nextInt(32), rng.nextInt(32), 1, 1);
        }

        // Grass blades — 4 passes, slightly leaning left/right
        rng.setSeed(1112);
        for (int pass = 0; pass < 4; pass++) {
            int blades = 14 + rng.nextInt(6);
            for (int b = 0; b < blades; b++) {
                int bx = rng.nextInt(32);
                int by = rng.nextInt(32);
                int h  = 3 + rng.nextInt(5);
                int lean = (rng.nextBoolean() ? 1 : -1) * rng.nextInt(2);
                Color stem = rng.nextBoolean() ? bgM : yel;
                Color tip  = rng.nextBoolean() ? yelB : bgL;
                for (int row = 0; row < h; row++) {
                    int px = Math.max(0, Math.min(31, bx + lean * row / 2));
                    int py = Math.max(0, Math.min(31, by - row));
                    g.setColor(row == h-1 ? tip : stem);
                    g.fillRect(px, py, 1, 1);
                }
            }
        }

        // Toxic pools — 3 dark puddles scattered across tile
        int[][] pools = {{5, 6, 6, 4}, {18, 20, 7, 3}, {24, 9, 5, 3}};
        for (int[] p : pools) {
            int px=p[0], py=p[1], pw=p[2], ph=p[3];
            g.setColor(toxD); g.fillOval(px, py, pw, ph);
            g.setColor(toxM); g.fillOval(px+1, py, pw-2, ph-1);
            // Sheen highlight
            g.setColor(toxL); g.fillRect(px+1, py, 1, 1);
        }

        // Purple spore dots — scattered tiny specks
        rng.setSeed(1113);
        int[][] spores = {
            {3,14},{9,8},{13,25},{20,5},{25,18},{7,22},{17,12},{29,7},{11,29},{26,28}
        };
        for (int[] sp : spores) {
            if (sp[0]>30||sp[1]>30) continue;
            g.setColor(toxM); g.fillRect(sp[0], sp[1], 2, 2);
            g.setColor(toxL); g.fillRect(sp[0], sp[1], 1, 1);
        }

        g.dispose(); return img;
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote: " + path);
    }
}
