package com.omes.dgw.device.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * BCD 转码反序列化，统一为 true/false。
 */
public class BcdConvertDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null || token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_TRUE) {
            return true;
        }
        if (token == JsonToken.VALUE_FALSE) {
            return false;
        }
        if (token.isNumeric()) {
            Boolean parsed = parseNumeric(parser.getIntValue());
            if (parsed == null) {
                throw context.weirdNumberException(
                        parser.getNumberValue(), Boolean.class, "BCD转码须为 是/否");
            }
            return parsed;
        }
        String text = parser.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        Boolean parsed = parseText(text);
        if (parsed == null) {
            throw context.weirdStringException(text, Boolean.class, "BCD转码须为 是/否");
        }
        return parsed;
    }

    public static Boolean parseText(String text) {
        String compact = text.trim().replace(" ", "").toLowerCase();
        return switch (compact) {
            case "true", "1", "是", "yes", "y" -> true;
            case "false", "0", "否", "no", "n" -> false;
            default -> null;
        };
    }

    private static Boolean parseNumeric(int value) {
        if (value == 1) {
            return true;
        }
        if (value == 0) {
            return false;
        }
        return null;
    }
}
