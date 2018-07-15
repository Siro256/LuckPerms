/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.bukkit.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import me.lucko.luckperms.bukkit.LPBukkitPlugin;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class LuckPermsBrigadier {

    public static boolean isSupported() {
        try {
            Class.forName("com.mojang.brigadier.CommandDispatcher");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void register(LPBukkitPlugin plugin) throws Exception {
        try (InputStream is = plugin.getBootstrap().getResourceStream("me/lucko/luckperms/commands/commands.json")) {
            if (is == null) {
                throw new Exception("commands.json missing from jar!");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                JsonObject data = new JsonParser().parse(reader).getAsJsonObject();
                LiteralArgumentBuilder command = deserializeLiteral(data);
                register(command);
            }
        }
    }

    private static void register(LiteralArgumentBuilder command) throws Exception {
        Class<?> craftServer = ReflectionUtil.obcClass("CraftServer");

        Field consoleField = craftServer.getDeclaredField("console");
        consoleField.setAccessible(true);

        Object mcServerObject = consoleField.get(Bukkit.getServer());

        Class<?> minecraftServer = ReflectionUtil.nmsClass("MinecraftServer");

        Method getCommandDispatcherMethod = minecraftServer.getDeclaredMethod("getCommandDispatcher");
        getCommandDispatcherMethod.setAccessible(true);

        Object commandDispatcherObject = getCommandDispatcherMethod.invoke(mcServerObject);

        Class<?> commandDispatcher = ReflectionUtil.nmsClass("CommandDispatcher");

        Method getBrigadierDispatcherMethod = commandDispatcher.getDeclaredMethod("a");
        getBrigadierDispatcherMethod.setAccessible(true);

        Object brigadierDispatcher = getBrigadierDispatcherMethod.invoke(commandDispatcherObject);

        CommandDispatcher dispatcher = (CommandDispatcher) brigadierDispatcher;

        //noinspection unchecked
        dispatcher.register(command);
    }

    private static ArgumentBuilder deserialize(JsonObject data) {
        String type = data.get("type").getAsString();
        switch (type) {
            case "literal": {
                return deserializeLiteral(data);
            }
            case "argument": {
                return deserializeArgument(data);
            }
            default:
                throw new IllegalArgumentException("type: " + type);
        }
    }

    private static LiteralArgumentBuilder deserializeLiteral(JsonObject data) {
        String name = data.get("name").getAsString();

        LiteralArgumentBuilder arg = LiteralArgumentBuilder.literal(name);

        return deserializeChildren(data, arg);
    }

    private static RequiredArgumentBuilder deserializeArgument(JsonObject data) {
        String name = data.get("name").getAsString();
        ArgumentType argumentType = deserializeArgumentType(data);

        //noinspection unchecked
        RequiredArgumentBuilder arg = RequiredArgumentBuilder.argument(name, argumentType);

        return deserializeChildren(data, arg);
    }

    private static ArgumentType deserializeArgumentType(JsonObject data) {
        String parser = data.get("parser").getAsString();
        String properties = null;
        if (data.has("properties")) {
            properties = data.get("properties").getAsString();
        }

        switch (parser) {
            case "brigadier:string": {
                Objects.requireNonNull(properties, "string properties");
                switch (properties) {
                    case "SINGLE_WORD":
                        return StringArgumentType.word();
                    case "QUOTABLE_PHRASE":
                        return StringArgumentType.string();
                    case "GREEDY_PHRASE":
                        return StringArgumentType.greedyString();
                    default:
                        throw new IllegalArgumentException("string property: " + properties);
                }
            }
            case "brigadier:bool":
                return BoolArgumentType.bool();
            case "brigadier:integer":
                return IntegerArgumentType.integer();
            default:
                throw new IllegalArgumentException("parser: " + parser);
        }
    }

    private static <T extends ArgumentBuilder> T deserializeChildren(JsonObject data, T builder) {
        if (data.has("children")) {
            JsonArray children = data.get("children").getAsJsonArray();
            for (JsonElement child : children) {
                //noinspection unchecked
                builder.then(deserialize(child.getAsJsonObject()));
            }
        }
        return builder;
    }

}
