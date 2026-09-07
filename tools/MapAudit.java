import com.google.gson.*;
import java.io.*;

public class MapAudit {
    public static void main(String[] args) throws Exception {
        var root = JsonParser.parseReader(new FileReader("data/overworlds/sylvandar.rfmap")).getAsJsonObject();
        var tiles = root.getAsJsonArray("tiles");
        int water=0, moss=0, dforest=0, mswamp=0, fungi=0, amber=0, thorn=0, gtree=0, aroots=0, sand=0, road=0, other=0;
        for (var row : tiles) {
            for (var cell : row.getAsJsonArray()) {
                String c = cell.getAsString();
                switch (c) {
                    case "~" -> water++;
                    case "\uE063" -> moss++;
                    case "\uE060" -> dforest++;
                    case "\uE062" -> mswamp++;
                    case "\uE064" -> fungi++;
                    case "\uE067" -> amber++;
                    case "\uE066" -> thorn++;
                    case "\uE061" -> gtree++;
                    case "\uE065" -> aroots++;
                    case "1" -> sand++;
                    case "r","4","5","6","7","8" -> road++;
                    default -> other++;
                }
            }
        }
        int total = 230*190;
        int land = total - water;
        System.out.println("Total tiles: " + total);
        System.out.println("Water:        " + water + " (" + (water*100/total) + "%)");
        System.out.println("Land:         " + land + " (" + (land*100/total) + "%)");
        System.out.println("  Sand:       " + sand);
        System.out.println("  Mangrove:   " + mswamp);
        System.out.println("  Moss:       " + moss);
        System.out.println("  Dense Forest: " + dforest);
        System.out.println("  Fungi:      " + fungi);
        System.out.println("  Amber:      " + amber);
        System.out.println("  Thorn:      " + thorn);
        System.out.println("  Giant Tree: " + gtree);
        System.out.println("  Ancient Roots: " + aroots);
        System.out.println("  Road:       " + road);
        System.out.println("  Other:      " + other);
    }
}
