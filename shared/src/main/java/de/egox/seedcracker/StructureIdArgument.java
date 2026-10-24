package de.egox.seedcracker;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.LiteralMessage;

/** Brigadier's word argument stops at ':', so resource identifiers require their own parser. */
public final class StructureIdArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(new LiteralMessage("Struktur-ID erwartet, z.B. minecraft:desert_pyramid"));
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        String value = reader.getString().substring(start, reader.getCursor());
        if (!value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) { reader.setCursor(start); throw INVALID.createWithContext(reader); }
        return value;
    }
}
