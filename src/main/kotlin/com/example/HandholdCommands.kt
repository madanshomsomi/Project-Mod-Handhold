package com.example

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object HandholdCommands {
	fun register() {
		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			dispatcher.register(
				Commands.literal("handhold")
					.then(
						Commands.literal("request")
							.then(
								Commands.argument("player", EntityArgument.player())
									.executes { context ->
										HandholdService.request(
											context.source.playerOrException,
											EntityArgument.getPlayer(context, "player"),
										)
										1
									},
							),
					)
					.then(
						Commands.literal("accept")
							.then(
								Commands.argument("player", EntityArgument.player())
									.executes { context ->
										HandholdService.accept(
											context.source.playerOrException,
											EntityArgument.getPlayer(context, "player"),
										)
										1
									},
							),
					)
					.then(
						Commands.literal("deny")
							.executes { context ->
								HandholdService.deny(context.source.server, context.source.playerOrException, null)
								1
							}
							.then(
								Commands.argument("player", EntityArgument.player())
									.executes { context ->
										HandholdService.deny(
											context.source.server,
											context.source.playerOrException,
											EntityArgument.getPlayer(context, "player"),
										)
										1
									},
							),
					)
					.then(
						Commands.literal("release")
							.executes { context ->
								HandholdService.release(context.source.server, context.source.playerOrException)
								1
							},
					),
			)
		}
	}
}
