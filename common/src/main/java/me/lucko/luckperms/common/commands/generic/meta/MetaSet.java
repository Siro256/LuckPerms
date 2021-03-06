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

package me.lucko.luckperms.common.commands.generic.meta;

import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.StandardNodeEquality;
import me.lucko.luckperms.api.context.MutableContextSet;
import me.lucko.luckperms.common.actionlog.ExtendedLogEntry;
import me.lucko.luckperms.common.command.CommandResult;
import me.lucko.luckperms.common.command.abstraction.CommandException;
import me.lucko.luckperms.common.command.abstraction.SharedSubCommand;
import me.lucko.luckperms.common.command.access.ArgumentPermissions;
import me.lucko.luckperms.common.command.access.CommandPermission;
import me.lucko.luckperms.common.command.utils.ArgumentParser;
import me.lucko.luckperms.common.command.utils.MessageUtils;
import me.lucko.luckperms.common.command.utils.StorageAssistant;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.command.CommandSpec;
import me.lucko.luckperms.common.locale.message.Message;
import me.lucko.luckperms.common.model.NodeMapType;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.node.factory.NodeFactory;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.utils.Predicates;
import me.lucko.luckperms.common.utils.TextUtils;

import net.kyori.text.TextComponent;
import net.kyori.text.event.HoverEvent;

import java.util.List;

public class MetaSet extends SharedSubCommand {
    public MetaSet(LocaleManager locale) {
        super(CommandSpec.META_SET.localize(locale), "set", CommandPermission.USER_META_SET, CommandPermission.GROUP_META_SET, Predicates.inRange(0, 1));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, PermissionHolder holder, List<String> args, String label, CommandPermission permission) throws CommandException {
        if (ArgumentPermissions.checkModifyPerms(plugin, sender, permission, holder)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        String key = args.get(0);
        String value = args.get(1);
        MutableContextSet context = ArgumentParser.parseContext(2, args, plugin);

        if (ArgumentPermissions.checkContext(plugin, sender, permission, context)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        if (ArgumentPermissions.checkArguments(plugin, sender, permission, key)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        Node n = NodeFactory.buildMetaNode(key, value).withExtraContext(context).build();

        if (holder.hasPermission(NodeMapType.ENDURING, n, StandardNodeEquality.IGNORE_EXPIRY_TIME_AND_VALUE).asBoolean()) {
            Message.ALREADY_HAS_META.send(sender, holder.getFriendlyName(), key, value, MessageUtils.contextSetToString(plugin.getLocaleManager(), context));
            return CommandResult.STATE_ERROR;
        }

        holder.clearMetaKeys(key, context, false);
        holder.setPermission(n);

        TextComponent.Builder builder = Message.SET_META_SUCCESS.asComponent(plugin.getLocaleManager(), key, value, holder.getFriendlyName(), MessageUtils.contextSetToString(plugin.getLocaleManager(), context)).toBuilder();
        HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextUtils.fromLegacy(
                TextUtils.joinNewline("¥3Raw key: ¥r" + key, "¥3Raw value: ¥r" + value),
                '¥'
        ));
        builder.applyDeep(c -> c.hoverEvent(event));
        sender.sendMessage(builder.build());

        ExtendedLogEntry.build().actor(sender).acted(holder)
                .action("meta", "set", key, value, context)
                .build().submit(plugin, sender);

        StorageAssistant.save(holder, sender, plugin);
        return CommandResult.SUCCESS;
    }
}
