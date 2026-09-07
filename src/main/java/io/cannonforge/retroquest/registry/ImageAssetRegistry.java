package io.cannonforge.retroquest.registry;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;

/**
 * Singleton in-memory cache for all game sprites, loaded from {@code src/main/resources/tiles/}.
 *
 * <h2>Key format</h2>
 * <p>Every sprite is identified by a slash-separated path with the {@code .png} extension removed:
 * <pre>
 *   overworld/grass          town/stone_wall          dungeon/altar
 *   monsters/dragon          npcs/innkeeper           letters/A
 * </pre>
 * The key is derived directly from the file's path relative to the {@code tiles/} root, so
 * adding a new PNG to the right subdirectory makes it available under the matching key with
 * no code changes.
 *
 * <h2>Sprite categories</h2>
 * <pre>
 *   overworld/   19 tiles — terrain, roads, structures, dungeon entrance
 *   town/        20 tiles — walls, floors, furniture, locked doors
 *   dungeon/     15 tiles — specials (altar, fountain, cube, throne), hazards, locked gates
 *   monsters/   112 sprites — procedurally assigned to levels 1–50 by MonsterRegistry
 *   npcs/        20 sprites — character portraits used in dialogue and town NPCs
 *   letters/     26 sprites — 5×7 bitmap A–Z on dark stone, used by letter tiles (Unicode PUA)
 * </pre>
 *
 * <h2>Loading strategy</h2>
 * <p>{@link #reloadAll()} (called on class load and after {@code ImageEditor} saves) populates
 * the cache from two sources in order:
 * <ol>
 *   <li><b>Classpath scan</b> — works in both IDE ({@code file://}) and packaged JAR
 *       ({@code jar://}) builds.</li>
 *   <li><b>Direct filesystem scan</b> of {@code src/main/resources/tiles} — allows sprites
 *       saved by the in-game image editor to appear immediately without a Maven rebuild.
 *       Only fills keys not already loaded from the classpath.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>
 *   Image img = ImageAssetRegistry.get("monsters/dragon");    // returns null-safe Image
 *   ImageAssetRegistry.reloadAll();                           // after adding new sprites
 *   List&lt;String&gt; keys = ImageAssetRegistry.getAllSpriteKeys(); // for editors/pickers
 * </pre>
 * <p>{@link #get(String)} looks for a {@code "default"} placeholder sprite when the requested
 * key is missing. No {@code default.png} currently ships in {@code tiles/}, so in practice a
 * missing key yields {@code null} — <b>callers must null-check the returned image.</b>
 */
public class ImageAssetRegistry {

    private static final Map<String, Image> cache = new HashMap<>();
    private static final String TILES_ROOT = "/tiles/";

    static {
        reloadAll();
    }

    /**
     * Call this after using ImageEditor to add new sprites
     */
    public static void reloadAll() {
        cache.clear();

        loadAllFromClasspath();           // existing code

        // Also scan the source folder directly so sprites saved by ImageEditor
        // are immediately visible without requiring a Maven build to copy them
        // to target/classes.
        File srcTiles = new File("src/main/resources/tiles");
        if (srcTiles.exists()) {
            loadFolderDirect(srcTiles, "");
        }

        if (cache.isEmpty()) {
            System.err.println("[ImageAssetRegistry] NO SPRITES WERE LOADED — every tile, monster "
                    + "and NPC will render blank. The tiles/ resources could not be read from the "
                    + "classpath, and the development folder src/main/resources/tiles is absent "
                    + "(normal in an installed build). See the error above for the cause.");
        }
    }

    private static void loadAllFromClasspath() {
        try {
            ClassLoader cl = ImageAssetRegistry.class.getClassLoader();
            Enumeration<URL> resources = cl.getResources("tiles");

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();

                if (protocol.equals("file")) {
                    // IDE / development mode
                    File dir = new File(url.toURI());
                    loadFolder(dir, "");
                }
                else if (protocol.equals("jar")) {
                    // Packaged JAR mode
                    loadFromJar(url);
                }
            }
        } catch (Exception e) {
            System.err.println("[ImageAssetRegistry] Failed to scan tiles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadFolder(File dir, String prefix) {
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                loadFolder(f, prefix + f.getName() + "/");
            } else if (f.getName().toLowerCase().endsWith(".png")) {
                String key = (prefix + f.getName()).replace(".png", "");
                Image img = AssetLoader.loadImage(TILES_ROOT + prefix + f.getName());
                if (img != null) {
                    cache.put(key, img);
                }
            }
        }
    }

    /**
     * Loads images directly from a filesystem directory, bypassing the classpath.
     * Used for the {@code src/main/resources/tiles} folder so that sprites saved
     * by the ImageEditor are available immediately without a Maven build.
     * Files already cached by the classpath scan are not overwritten.
     */
    private static void loadFolderDirect(File dir, String prefix) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                loadFolderDirect(f, prefix + f.getName() + "/");
            } else if (f.getName().toLowerCase().endsWith(".png")) {
                String key = (prefix + f.getName()).replace(".png", "");
                if (!cache.containsKey(key)) {
                    try {
                        Image img = ImageIO.read(f);
                        if (img != null) cache.put(key, img);
                    } catch (IOException e) {
                        System.err.println("[ImageAssetRegistry] Failed to load: " + f.getPath());
                    }
                }
            }
        }
    }

    /**
     * Enumerates the sprites inside a packaged JAR.
     *
     * <p>The jar is opened through the URL's own {@link JarURLConnection} rather than by
     * slicing {@code url.getPath()} into a filename: that path is percent-encoded, so an
     * install directory containing a space (or any other escaped character) produced a
     * {@code JarFile} that could not be opened and left the cache completely empty.
     */
    private static void loadFromJar(URL jarUrl) {
        try {
            JarURLConnection conn = (JarURLConnection) jarUrl.openConnection();
            conn.setUseCaches(false); // we own (and close) the JarFile handle
            try (JarFile jar = conn.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("tiles/") && name.endsWith(".png")) {
                        String key = name.substring(6, name.length() - 4); // remove "tiles/" and ".png"
                        Image img = AssetLoader.loadImage("/" + name);
                        if (img != null) {
                            cache.put(key, img);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ImageAssetRegistry] Failed to load sprites from JAR " + jarUrl
                    + " — " + e);
        }
    }

    public static Image get(String spriteKey) {
        if (spriteKey == null || spriteKey.isEmpty()) {
            return cache.getOrDefault("default", null);
        }
        return cache.getOrDefault(spriteKey, cache.getOrDefault("default", null));
    }

    public static List<String> getAllSpriteKeys() {
        return new ArrayList<>(cache.keySet());
    }

}
