package io.cannonforge.retroquest.registry;

import java.awt.Color;
import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Gson type adapter that serializes {@link java.awt.Color} as a packed ARGB integer
 * and deserializes it back, preserving the alpha channel.
 */
public class ColorAdapter implements JsonSerializer<Color>, JsonDeserializer<Color> {

    /**
     * Serializes a {@link Color} to its packed ARGB integer value.
     *
     * @param color   the color to serialize
     * @param type    the declared type of the field
     * @param context the serialization context
     * @return a {@link JsonPrimitive} containing the packed ARGB integer,
     *         or {@link JsonNull} if {@code color} is {@code null}
     */
    @Override
    public JsonElement serialize(Color color, Type type, JsonSerializationContext context) {
        if (color == null) return JsonNull.INSTANCE;
        return new JsonPrimitive(color.getRGB());
    }

    /**
     * Deserializes a packed ARGB integer back into a {@link Color}, with alpha support.
     *
     * @param json    the JSON element containing the packed ARGB integer
     * @param type    the declared type of the field
     * @param context the deserialization context
     * @return the reconstructed {@link Color}, or {@code null} if the element is absent/null
     */
    @Override
    public Color deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
        if (json == null || json.isJsonNull()) return null;
        return new Color(json.getAsInt(), true); // true = has alpha
    }
}
