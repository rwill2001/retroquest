package io.cannonforge.retroquest.registry;

import java.awt.Image;
import java.io.IOException;

import javax.imageio.ImageIO;

public final class AssetLoader {

    private AssetLoader() {} // utility class

    /**
     * Loads image from classpath (works in IDE and JAR)
     * Path should start with /tiles/...
     */
    public static Image loadImage(String path) {
        if (!path.startsWith("/")) path = "/" + path;
        // getResource() returns null for a missing asset, and ImageIO.read((URL) null)
        // throws IllegalArgumentException rather than IOException — check it first.
        java.net.URL url = AssetLoader.class.getResource(path);
        if (url == null) {
            System.err.println("❌ Asset not found: " + path);
            return null;
        }
        try {
            Image img = ImageIO.read(url);
            if (img == null) {
                System.err.println("❌ Asset not readable (unsupported or corrupt): " + path);
            }
            return img;
        } catch (IOException e) {
            System.err.println("❌ Failed to load: " + path + " → " + e.getMessage());
            return null;
        }
    }

}
