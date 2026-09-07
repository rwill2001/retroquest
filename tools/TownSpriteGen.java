import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Sprite generator for RetroQuest town tiles.
 * Run from the project root:
 *   javac tools/TownSpriteGen.java -d tools/
 *   java -cp tools TownSpriteGen
 *
 * Writes PNGs to src/main/resources/tiles/town/
 * Add new methods following the same pattern to generate additional tiles.
 *
 * To run a specific tile only, comment out the others in main().
 */
public class TownSpriteGen {

    // WARNING: Do not re-enable — sprites may have been hand-edited in RetroForge.
    // Helper methods below are kept for reference. Use a one-shot SpriteGen for targeted regeneration.
    public static void main(String[] args) {
        System.out.println("Disabled to protect hand-edited sprites. Use a one-shot SpriteGen instead.");
    }

    // ── Bookshelf ─────────────────────────────────────────────────────────────
    // Front view: dark back, two shelf planks, three rows of colorful book spines
    static BufferedImage bookshelf() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color back       = new Color(18, 10,  4);
        Color frameD     = new Color(65, 38, 14);
        Color frameMid   = new Color(92, 56, 20);
        Color frameLight = new Color(122, 78, 30);
        Color plankMid   = new Color(105, 66, 25);

        g.setColor(back);   g.fillRect(0, 0, 32, 32);

        // Left frame (x=0-2)
        g.setColor(frameD);    g.fillRect(0, 0, 3, 32);
        g.setColor(frameLight); g.drawLine(2, 0, 2, 31);
        g.setColor(frameMid);  g.drawLine(1, 0, 1, 31);

        // Right frame (x=29-31)
        g.setColor(frameD);    g.fillRect(29, 0, 3, 32);
        g.setColor(frameMid);  g.drawLine(29, 0, 29, 31);
        g.setColor(frameD);    g.drawLine(31, 0, 31, 31);

        // Top frame (y=0-2)
        g.setColor(frameMid);   g.fillRect(0, 0, 32, 3);
        g.setColor(frameLight); g.drawLine(0, 0, 31, 0);
        g.setColor(frameD);     g.drawLine(0, 2, 31, 2);

        // Bottom frame (y=29-31)
        g.setColor(frameD); g.fillRect(0, 29, 32, 3);

        // Shelf planks at y=10 and y=20
        for (int sy : new int[]{10, 20}) {
            g.setColor(plankMid);   g.fillRect(3, sy, 26, 2);
            g.setColor(frameLight); g.drawLine(3, sy, 28, sy);
            g.setColor(frameD);     g.drawLine(3, sy+1, 28, sy+1);
        }

        drawBooks(g, 3, 3,  26, 7, 0);
        drawBooks(g, 3, 12, 26, 8, 5);
        drawBooks(g, 3, 22, 26, 7, 3);

        g.dispose();
        return img;
    }

    static void drawBooks(Graphics2D g, int x, int y, int w, int h, int seed) {
        Color[] bc = {
            new Color(168,24,24), new Color(22,52,155), new Color(22,108,38),
            new Color(148,118,18), new Color(82,18,98), new Color(138,58,18),
            new Color(14,88,108), new Color(155,38,38), new Color(28,75,28),
            new Color(108,88,18)
        };
        int[] bw = {3,2,4,3,2,3,4,2,3,2,4};
        int cx=x, bi=seed, wi=seed;
        while (cx < x+w-1) {
            int bookW=bw[wi%bw.length];
            if (cx+bookW > x+w) bookW=x+w-cx;
            if (bookW<=0) break;
            Color c=bc[bi%bc.length];
            g.setColor(c); g.fillRect(cx,y,bookW,h);
            g.setColor(new Color(Math.min(255,c.getRed()+40),Math.min(255,c.getGreen()+40),Math.min(255,c.getBlue()+40)));
            g.drawLine(cx,y,cx,y+h-1);
            g.setColor(new Color(Math.min(255,c.getRed()+25),Math.min(255,c.getGreen()+25),Math.min(255,c.getBlue()+25)));
            g.drawLine(cx,y,cx+bookW-1,y);
            g.setColor(new Color(Math.max(0,c.getRed()-40),Math.max(0,c.getGreen()-40),Math.max(0,c.getBlue()-40)));
            if (bookW>1) g.drawLine(cx+bookW-1,y,cx+bookW-1,y+h-1);
            g.setColor(new Color(8,4,1)); g.drawLine(cx+bookW,y,cx+bookW,y+h-1);
            cx+=bookW+1; bi++; wi++;
        }
    }

    // ── Bed ───────────────────────────────────────────────────────────────────
    // Top-down: headboard at top, two pillows, dark red blanket with folds, footboard
    static BufferedImage bed() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color floor=new Color(70,66,74), floorMid=new Color(82,78,86);
        Color woodD=new Color(68,40,14), woodMid=new Color(92,56,20), woodLight=new Color(118,76,30);
        Color pillow=new Color(215,208,185), pillowSh=new Color(155,148,128), pillowHi=new Color(238,232,210);
        Color blanketM=new Color(135,20,20), blanketHi=new Color(162,35,35);
        Color blanketSh=new Color(90,10,10), blanketFd=new Color(112,15,15);

        g.setColor(floor); g.fillRect(0,0,32,32);
        g.setColor(floorMid); g.drawLine(0,16,2,16); g.drawLine(29,16,31,16);

        // Side rails
        g.setColor(woodD);    g.fillRect(2,2,3,28);
        g.setColor(woodLight); g.drawLine(2,2,2,29); g.setColor(woodMid); g.drawLine(3,2,3,29);
        g.setColor(woodD);    g.fillRect(27,2,3,28);
        g.setColor(woodMid);  g.drawLine(27,2,27,29); g.setColor(woodD); g.drawLine(29,2,29,29);

        // Headboard (y=2-7)
        g.setColor(woodD); g.fillRect(2,2,28,6);
        g.setColor(woodMid); g.fillRect(4,3,11,4); g.fillRect(17,3,11,4);
        g.setColor(woodLight);
        g.drawLine(4,3,14,3); g.drawLine(4,3,4,6); g.drawLine(17,3,27,3); g.drawLine(17,3,17,6);
        g.setColor(woodD); g.drawLine(14,3,14,6); g.drawLine(27,3,27,6);
        g.setColor(woodLight); g.drawLine(2,2,29,2);

        // Pillows (y=8-12)
        g.setColor(pillow); g.fillRect(6,8,8,5); g.fillRect(18,8,8,5);
        g.setColor(pillowHi);
        g.drawLine(6,8,13,8); g.drawLine(6,8,6,12); g.drawLine(18,8,25,8); g.drawLine(18,8,18,12);
        g.setColor(pillowSh);
        g.drawLine(6,12,13,12); g.drawLine(13,8,13,12); g.drawLine(18,12,25,12); g.drawLine(25,8,25,12);
        g.setColor(blanketSh); g.fillRect(14,8,4,5);

        // Blanket (y=13-24)
        g.setColor(blanketM); g.fillRect(5,13,22,12);
        g.setColor(blanketHi); g.drawLine(5,15,26,15); g.drawLine(5,19,26,19); g.drawLine(5,23,26,23);
        g.setColor(blanketFd); g.drawLine(5,16,26,16); g.drawLine(5,20,26,20); g.drawLine(5,24,26,24);
        g.setColor(blanketHi); g.fillRect(5,13,22,2);
        g.setColor(new Color(185,50,50)); g.drawLine(5,13,26,13);
        g.setColor(blanketSh); g.drawLine(5,13,5,24); g.drawLine(26,13,26,24);

        // Footboard (y=25-29)
        g.setColor(woodD); g.fillRect(2,25,28,5);
        g.setColor(woodMid); g.fillRect(4,26,24,3);
        g.setColor(woodLight); g.drawLine(4,26,27,26); g.drawLine(4,26,4,28);
        g.setColor(woodD); g.drawLine(27,26,27,28);

        g.dispose();
        return img;
    }

    // ── Fireplace ─────────────────────────────────────────────────────────────
    // Front/top view: stone masonry U-surround, dark opening, fire inside
    static BufferedImage fireplace() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color stoneBase=new Color(112,105,95), stoneDark=new Color(72,66,58);
        Color stoneLight=new Color(148,140,125), grout=new Color(52,46,40);
        Color hearth=new Color(6,4,2), hearthBk=new Color(30,15,5);

        g.setColor(stoneBase); g.fillRect(0,0,32,32);

        // Grout lines (brick bond)
        g.setColor(grout);
        g.drawLine(0,7,31,7); g.drawLine(0,15,5,15); g.drawLine(26,15,31,15); g.drawLine(0,23,31,23);
        g.drawLine(8,0,8,6); g.drawLine(20,0,20,6);
        g.drawLine(3,8,3,14); g.drawLine(28,8,28,14);
        g.drawLine(5,16,5,22); g.drawLine(21,16,21,22);
        g.drawLine(12,24,12,31); g.drawLine(24,24,24,31);

        // Stone highlights
        g.setColor(stoneLight);
        g.drawLine(0,0,7,0); g.drawLine(9,0,19,0); g.drawLine(21,0,31,0);
        g.drawLine(0,8,2,8); g.drawLine(0,16,4,16); g.drawLine(0,24,11,24);

        // Mantel shelf (y=0-5)
        g.setColor(new Color(128,120,108)); g.fillRect(0,0,32,6);
        g.setColor(stoneLight); g.drawLine(0,0,31,0); g.drawLine(0,1,31,1);
        g.setColor(stoneDark); g.drawLine(0,5,31,5);
        g.setColor(stoneLight); g.fillRect(0,6,32,2); g.setColor(stoneDark); g.drawLine(0,7,31,7);

        // Firebox opening (x=6, y=8, 20w, 17h)
        int ox=6, oy=8, ow=20, oh=17;
        g.setColor(new Color(140,80,20,140));
        g.fillRect(0,oy,6,oh); g.fillRect(ox+ow,oy,6,oh);
        g.setColor(new Color(95,48,22)); g.fillRect(ox-1,oy-1,ow+2,oh+2);
        g.setColor(hearth); g.fillRect(ox,oy,ow,oh);
        g.setColor(hearthBk); g.fillRect(ox+1,oy+1,ow-2,oh-4);

        // Fire
        g.setColor(new Color(180,35,0)); g.fillRect(ox+2,oy+oh-4,ow-4,4);
        g.setColor(new Color(220,70,0)); g.fillRect(ox+3,oy+oh-3,ow-6,3);
        g.setColor(new Color(255,130,0));
        for (int ex=ox+4; ex<ox+ow-4; ex+=3) g.fillRect(ex,oy+oh-2,2,2);
        int[] lx={ox+3,ox+8,ox+5},  ly={oy+oh-4,oy+oh-4,oy+5};   g.setColor(new Color(235,95,0));  g.fillPolygon(lx,ly,3);
        int[] mx={ox+7,ox+13,ox+10},my={oy+oh-4,oy+oh-4,oy+2};   g.setColor(new Color(255,155,0)); g.fillPolygon(mx,my,3);
        int[] rx={ox+12,ox+17,ox+14},ry={oy+oh-4,oy+oh-4,oy+5};  g.setColor(new Color(235,95,0));  g.fillPolygon(rx,ry,3);
        int[] bix={ox+9,ox+11,ox+10},biy={oy+oh-4,oy+oh-4,oy+3}; g.setColor(new Color(255,215,40));g.fillPolygon(bix,biy,3);
        g.setColor(new Color(255,248,200)); g.fillRect(ox+10,oy+3,1,2);

        // Hearth slab (y=25-31)
        g.setColor(stoneDark); g.fillRect(0,25,32,7);
        g.setColor(new Color(88,82,72)); g.fillRect(2,26,28,5);
        g.setColor(stoneLight); g.drawLine(2,26,29,26);
        g.setColor(grout); g.drawLine(2,29,29,29); g.drawLine(16,26,16,31);
        g.setColor(new Color(120,65,12,80)); g.fillRect(6,25,20,2);

        g.dispose();
        return img;
    }

    // ── Barrel ────────────────────────────────────────────────────────────────
    // Top-down view: circular barrel with stave lines, metal hoops, bung hole
    static BufferedImage barrel() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color floor=new Color(70,66,74);
        Color staveD=new Color(95,58,20), staveMid=new Color(128,80,30);
        Color staveL=new Color(158,105,45), staveHL=new Color(185,130,60);
        Color hoopD=new Color(52,48,44), hoopM=new Color(75,70,64), hoopL=new Color(108,100,90);
        Color bungC=new Color(70,40,12);

        g.setColor(floor); g.fillRect(0,0,32,32);
        int cx=16, cy=16, r=13;

        g.setColor(new Color(18,12,6,100)); g.fillOval(cx-r+2,cy-r+2,r*2,r*2);
        g.setColor(staveMid); g.fillOval(cx-r,cy-r,r*2,r*2);

        // Stave lines
        g.setColor(staveD);
        for (int i=0; i<8; i++) {
            double a=Math.PI*2*i/8.0;
            g.drawLine(cx,cy,(int)(cx+(r-1)*Math.cos(a)),(int)(cy+(r-1)*Math.sin(a)));
        }
        g.setColor(staveL);
        for (int i=5; i<=7; i++) {
            double a=Math.PI*2*i/8.0;
            g.drawLine(cx,cy,(int)(cx+(r-1)*Math.cos(a)),(int)(cy+(r-1)*Math.sin(a)));
        }
        // Highlight patch (top-left)
        for (int py=cy-r+2; py<=cy-2; py++)
            for (int px=cx-r+2; px<=cx-2; px++) {
                if (Math.sqrt((px-cx)*(double)(px-cx)+(py-cy)*(double)(py-cy))<r-1)
                    img.setRGB(px,py,blend(img.getRGB(px,py),staveHL.getRGB(),0.35f));
            }

        // Hoops
        g.setColor(hoopD); g.drawOval(cx-r,cy-r,r*2-1,r*2-1);
        g.setColor(hoopM); g.drawOval(cx-r+1,cy-r+1,r*2-3,r*2-3);
        g.setColor(hoopL); g.drawArc(cx-r+1,cy-r+1,r*2-3,r*2-3,110,115);
        int r2=r*2/3;
        g.setColor(hoopD); g.drawOval(cx-r2,cy-r2,r2*2,r2*2);
        g.setColor(hoopM); g.drawOval(cx-r2+1,cy-r2+1,r2*2-2,r2*2-2);
        g.setColor(hoopL); g.drawArc(cx-r2+1,cy-r2+1,r2*2-2,r2*2-2,110,115);
        int r3=r/3+1;
        g.setColor(hoopD); g.drawOval(cx-r3,cy-r3,r3*2,r3*2);
        g.setColor(hoopM); g.drawOval(cx-r3+1,cy-r3+1,r3*2-2,r3*2-2);

        // Bung hole
        g.setColor(staveD); g.fillOval(cx-3,cy-3,6,6);
        g.setColor(bungC);  g.fillOval(cx-2,cy-2,4,4);
        g.setColor(hoopD);  g.drawOval(cx-2,cy-2,4,4);
        g.setColor(new Color(35,18,5)); g.fillOval(cx-1,cy-1,2,2);

        // Shadow arc
        g.setColor(new Color(35,20,6,130));
        g.drawArc(cx-r+2,cy-r+2,r*2-4,r*2-4,225,120);
        g.drawArc(cx-r+3,cy-r+3,r*2-6,r*2-6,225,120);

        // Clear outside circle
        for (int py=0; py<32; py++)
            for (int px=0; px<32; px++)
                if (Math.sqrt((px-cx)*(double)(px-cx)+(py-cy)*(double)(py-cy))>r+0.3)
                    img.setRGB(px,py,floor.getRGB());

        g.dispose();
        return img;
    }

    static int blend(int base, int over, float t) {
        int br=(base>>16)&0xFF, bg=(base>>8)&0xFF, bb=base&0xFF;
        int or=(over>>16)&0xFF, og=(over>>8)&0xFF, ob=over&0xFF;
        return 0xFF000000|((int)(br+(or-br)*t)<<16)|((int)(bg+(og-bg)*t)<<8)|(int)(bb+(ob-bb)*t);
    }

    // ── Carpet ────────────────────────────────────────────────────────────────
    // Top-down: fringe + dark red field + gold medallion + border ornaments
    static BufferedImage carpet() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color floor=new Color(52,48,56);
        Color fringe=new Color(185,158,100), fringeSh=new Color(135,112,68);
        Color border=new Color(142,16,16), borderDk=new Color(100,8,8);
        Color gold=new Color(188,152,18), goldBright=new Color(225,188,40), goldDim=new Color(148,118,12);
        Color field=new Color(108,10,10), fieldDk=new Color(78,5,5);

        g.setColor(floor); g.fillRect(0,0,32,32);

        // Fringe top/bottom
        for (int x=2; x<30; x++) {
            Color fc=(x%3==0)?fringe:fringeSh;
            g.setColor(fc); g.drawLine(x,0,x,1); g.drawLine(x,30,x,31);
        }
        g.setColor(new Color(50,40,25,120)); g.drawLine(2,2,29,2); g.drawLine(2,29,29,29);

        // Field
        g.setColor(field); g.fillRect(1,2,30,28);

        // Borders
        g.setColor(borderDk); g.drawRect(1,2,29,27);
        g.setColor(border);   g.drawRect(2,3,27,25);
        g.setColor(gold);     g.drawRect(4,5,23,21);
        g.setColor(goldDim);  g.drawRect(5,6,21,19);

        // Corner ornaments
        int[][] corners={{5,6},{27,6},{5,26},{27,26}};
        for (int[] c : corners) {
            g.setColor(goldBright);
            g.drawLine(c[0],c[1]-2,c[0]+2,c[1]); g.drawLine(c[0]+2,c[1],c[0],c[1]+2);
            g.drawLine(c[0],c[1]+2,c[0]-2,c[1]); g.drawLine(c[0]-2,c[1],c[0],c[1]-2);
            g.setColor(gold); g.fillRect(c[0]-1,c[1]-1,2,2);
        }

        // Border dots
        g.setColor(goldDim);
        for (int x=8; x<=24; x+=2) { g.fillRect(x,5,1,1); g.fillRect(x,26,1,1); }
        for (int y=9; y<=23; y+=2) { g.fillRect(4,y,1,1); g.fillRect(27,y,1,1); }

        // Cross arms
        g.setColor(goldDim);
        g.drawLine(16,8,16,10); g.drawLine(16,22,16,24);
        g.drawLine(7,16,9,16);  g.drawLine(23,16,25,16);

        // Center medallion
        int mx=16, my=16;
        int[] ox={mx,mx+5,mx,mx-5}, oy={my-5,my,my+5,my};
        g.setColor(gold); g.fillPolygon(ox,oy,4);
        int[] mx2={mx,mx+3,mx,mx-3}, my2={my-3,my,my+3,my};
        g.setColor(fieldDk); g.fillPolygon(mx2,my2,4);
        int[] mx3={mx,mx+2,mx,mx-2}, my3={my-2,my,my+2,my};
        g.setColor(goldBright); g.fillPolygon(mx3,my3,4);
        g.setColor(field); g.fillRect(mx-1,my-1,2,2);
        g.setColor(goldBright); g.fillRect(mx,my,1,1);

        g.dispose();
        return img;
    }

    // ── Table ─────────────────────────────────────────────────────────────────
    // Top-down: rectangular wood surface, grain lines, darker lip, corner legs
    static BufferedImage table() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color floor     = new Color(70,  66,  74);
        Color legD      = new Color(52,  30,   8);
        Color legMid    = new Color(72,  44,  14);
        Color lipD      = new Color(80,  48,  15);
        Color lipMid    = new Color(100, 62,  20);
        Color lipLight  = new Color(118, 76,  28);
        Color surfMid   = new Color(138, 88,  32);
        Color surfLight = new Color(158, 105, 42);
        Color grainHi   = new Color(168, 115, 48);
        Color grainSh   = new Color(118, 74,  26);

        // Floor
        g.setColor(floor); g.fillRect(0, 0, 32, 32);

        // Drop shadow (offset slightly bottom-right)
        g.setColor(new Color(20, 15, 8, 90));
        g.fillRect(7, 7, 22, 22);

        // Corner legs (3×3 each, slightly proud of table edge)
        g.setColor(legD);
        g.fillRect(5, 5, 3, 3); g.fillRect(24, 5, 3, 3);
        g.fillRect(5, 24, 3, 3); g.fillRect(24, 24, 3, 3);
        g.setColor(legMid); // top-left highlight on each leg
        g.fillRect(5, 5, 1, 1); g.fillRect(24, 5, 1, 1);
        g.fillRect(5, 24, 1, 1); g.fillRect(24, 24, 1, 1);

        // Table top surface (x=6-25, y=6-25)
        g.setColor(surfMid); g.fillRect(6, 6, 20, 20);

        // Wood grain — horizontal lines across the surface
        for (int y = 8; y <= 24; y += 3) {
            g.setColor(grainHi); g.drawLine(7, y,   24, y);
            g.setColor(grainSh); g.drawLine(7, y+1, 24, y+1);
        }

        // Subtle top-left highlight sheen
        g.setColor(surfLight);
        g.drawLine(7, 7, 24, 7);   // top edge of surface
        g.drawLine(7, 7, 7, 24);   // left edge of surface

        // Table lip (darker border around surface edge)
        g.setColor(lipMid); g.drawRect(6, 6, 19, 19);
        g.setColor(lipD);
        g.drawLine(25, 6, 25, 25); // right shadow edge
        g.drawLine(6, 25, 25, 25); // bottom shadow edge
        g.setColor(lipLight);
        g.drawLine(6, 6, 25, 6);   // top highlight edge
        g.drawLine(6, 6, 6, 25);   // left highlight edge

        g.dispose();
        return img;
    }

    // ── Chair ─────────────────────────────────────────────────────────────────
    // Top-down: prominent backrest bar at top, wood seat, legs at all four corners
    static BufferedImage chair() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        Color floor      = new Color(70,  66,  74);
        Color legD       = new Color(52,  30,   8);
        Color legMid     = new Color(72,  44,  14);
        Color backD      = new Color(68,  40,  12);
        Color backMid    = new Color(88,  54,  18);
        Color backLight  = new Color(112, 72,  26);
        Color seatD      = new Color(95,  58,  18);
        Color seatMid    = new Color(125, 80,  28);
        Color seatLight  = new Color(148, 100, 38);
        Color seatHi     = new Color(162, 112, 46);

        // Floor
        g.setColor(floor); g.fillRect(0, 0, 32, 32);

        // Drop shadow
        g.setColor(new Color(20, 15, 8, 85));
        g.fillRect(8, 6, 18, 24);

        // Back legs (top corners, behind backrest, x=7-9 and x=22-24, y=5-9)
        g.setColor(legD);
        g.fillRect(7, 5, 3, 4); g.fillRect(22, 5, 3, 4);
        g.setColor(legMid); g.fillRect(7, 5, 1, 1); g.fillRect(22, 5, 1, 1);

        // Backrest — thick horizontal bar (x=7-24, y=6-12)
        // Main face
        g.setColor(backMid); g.fillRect(7, 6, 18, 7);
        // Top face (slightly lighter — angled plane)
        g.setColor(backLight); g.fillRect(7, 6, 18, 2);
        // Top highlight line
        g.setColor(new Color(130, 88, 34)); g.drawLine(7, 6, 24, 6);
        // Bottom shadow of backrest
        g.setColor(backD); g.drawLine(7, 12, 24, 12);
        // Side shadows
        g.setColor(backD);
        g.drawLine(7,  6, 7,  12);
        g.drawLine(24, 6, 24, 12);
        // Wood grain on backrest (vertical for a back rail)
        g.setColor(new Color(100, 64, 22));
        g.drawLine(11, 7, 11, 11); g.drawLine(16, 7, 16, 11); g.drawLine(21, 7, 21, 11);
        g.setColor(new Color(102, 68, 24));
        g.drawLine(12, 7, 12, 11); g.drawLine(17, 7, 17, 11); g.drawLine(22, 7, 22, 11);

        // Back support posts (x=7-9 and x=22-24, y=12-15) — side stiles
        g.setColor(backD);
        g.fillRect(7, 13, 3, 3); g.fillRect(22, 13, 3, 3);

        // Seat surface (x=7-24, y=14-25)
        g.setColor(seatMid); g.fillRect(7, 14, 18, 12);
        // Wood grain (horizontal across seat)
        for (int y = 16; y <= 24; y += 3) {
            g.setColor(seatHi); g.drawLine(8, y,   23, y);
            g.setColor(seatD);  g.drawLine(8, y+1, 23, y+1);
        }
        // Seat top highlight
        g.setColor(seatLight); g.drawLine(7, 14, 24, 14); g.drawLine(7, 14, 7, 25);
        // Seat side/bottom shadow
        g.setColor(seatD);
        g.drawLine(24, 14, 24, 25);
        g.drawLine(7,  25, 24, 25);

        // Front legs (bottom corners, x=7-9 and x=22-24, y=24-27)
        g.setColor(legD);
        g.fillRect(7, 26, 3, 4); g.fillRect(22, 26, 3, 4);
        g.setColor(legMid); g.fillRect(7, 26, 1, 1); g.fillRect(22, 26, 1, 1);

        // Rung / stretcher between front legs (y=28, just below seat)
        g.setColor(legD);
        g.drawLine(10, 28, 21, 28);
        g.setColor(legMid);
        g.drawLine(10, 27, 21, 27);

        g.dispose();
        return img;
    }

    // ── Town Grass ────────────────────────────────────────────────────────────
    // Soft well-tended garden grass — more manicured than overworld grass
    static BufferedImage townGrass() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        java.util.Random rng = new java.util.Random(42);

        Color base  = new Color(58, 90, 38);
        Color light = new Color(75, 112, 48);
        Color dark  = new Color(42, 70, 25);
        Color tip   = new Color(95, 130, 58);
        Color pebble = new Color(110, 100, 90);

        // Base fill
        g.setColor(base);
        g.fillRect(0, 0, 32, 32);

        // Scattered noise variation pixels
        for (int i = 0; i < 80; i++) {
            int px = rng.nextInt(32), py = rng.nextInt(32);
            g.setColor(rng.nextBoolean() ? light : dark);
            g.fillRect(px, py, 1, 1);
        }

        // Short stubby blade strokes — 3 passes
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < 18; i++) {
                int bx = rng.nextInt(30), by = rng.nextInt(29);
                int h = 2 + rng.nextInt(2); // 2–3px tall
                g.setColor(light);
                g.drawLine(bx, by + h, bx, by + 1);
                g.setColor(tip);
                g.fillRect(bx, by, 1, 1);
            }
        }

        // Occasional tiny pebble
        for (int i = 0; i < 5; i++) {
            int px = rng.nextInt(30), py = rng.nextInt(30);
            g.setColor(pebble);
            g.fillRect(px, py, 2, 1);
        }

        g.dispose();
        return img;
    }

    // ── Town Fountain ─────────────────────────────────────────────────────────
    // Top-down view of a small stone fountain with water
    static BufferedImage fountain() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        java.util.Random rng = new java.util.Random(7);

        Color floor      = new Color(58, 90, 38);
        Color rimOuter   = new Color(75, 70, 80);
        Color rimMid     = new Color(115, 108, 120);
        Color rimInner   = new Color(145, 138, 150);
        Color water      = new Color(30, 80, 140);
        Color waveCyan   = new Color(100, 200, 230);
        Color sparkle    = new Color(230, 245, 255);
        Color spout      = new Color(190, 195, 200);

        // Floor background
        g.setColor(floor);
        g.fillRect(0, 0, 32, 32);

        // Outer rim dark edge
        g.setColor(rimOuter);
        g.fillOval(2, 2, 28, 28);

        // Rim body
        g.setColor(rimMid);
        g.fillOval(3, 3, 26, 26);

        // Inner rim lip (lighter)
        g.setColor(rimInner);
        g.drawOval(4, 4, 23, 23);
        g.drawOval(5, 5, 21, 21);

        // Water basin interior
        g.setColor(water);
        g.fillOval(6, 6, 20, 20);

        // Water surface highlights — scattered cyan dots
        for (int i = 0; i < 12; i++) {
            int angle = rng.nextInt(360);
            double rad = Math.toRadians(angle);
            double dist = 2 + rng.nextDouble() * 5;
            int wx = (int)(16 + dist * Math.cos(rad));
            int wy = (int)(16 + dist * Math.sin(rad));
            g.setColor(waveCyan);
            g.fillRect(wx, wy, 1 + rng.nextInt(2), 1);
        }

        // White sparkle at center area
        g.setColor(sparkle);
        g.fillRect(14, 13, 1, 1);
        g.fillRect(17, 15, 1, 1);
        g.fillRect(15, 18, 1, 1);

        // Central spout pip
        g.setColor(spout);
        g.fillOval(14, 14, 4, 4);
        g.setColor(sparkle);
        g.fillRect(15, 15, 2, 2);

        g.dispose();
        return img;
    }

    static void save(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", f);
        System.out.println("  wrote: " + path);
    }
}
