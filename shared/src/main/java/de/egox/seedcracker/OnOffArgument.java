package de.egox.seedcracker;

import com.mojang.brigadier.*;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One case-insensitive argument, with exactly two canonical completion suggestions. */
public final class OnOffArgument implements ArgumentType<Boolean> {
    private static final SimpleCommandExceptionType INVALID = new SimpleCommandExceptionType(new LiteralMessage("ON oder OFF erwartet"));
    public Boolean parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor(); String value = reader.readUnquotedString();
        if (value.equalsIgnoreCase("ON")) return true;
        if (value.equalsIgnoreCase("OFF")) return false;
        reader.setCursor(start); throw INVALID.createWithContext(reader);
    }
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (String value : List.of("ON", "OFF")) if (value.startsWith(prefix)) builder.suggest(value);
        return builder.buildFuture();
    }
    public Collection<String> getExamples() { return List.of("ON", "OFF"); }
}
