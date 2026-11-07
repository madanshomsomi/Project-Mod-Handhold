package com.example

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer

object HandholdNetworking {
	fun registerPayloadTypes() {
		PayloadTypeRegistry.playS2C().register(FollowStatePayload.TYPE, FollowStatePayload.CODEC)
		PayloadTypeRegistry.playC2S().register(FollowerInputPayload.TYPE, FollowerInputPayload.CODEC)
	}

	fun registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(FollowerInputPayload.TYPE) { payload, context ->
			HandholdService.updateFollowerInput(
				context.player().uuid,
				payload.strafe.toDouble(),
				payload.forward.toDouble(),
			)
		}
	}

	fun supportsFollowState(player: ServerPlayer): Boolean =
		ServerPlayNetworking.canSend(player, FollowStatePayload.TYPE)

	fun sendFollowState(player: ServerPlayer, following: Boolean) {
		if (supportsFollowState(player)) {
			ServerPlayNetworking.send(player, FollowStatePayload(following))
		}
	}
}
