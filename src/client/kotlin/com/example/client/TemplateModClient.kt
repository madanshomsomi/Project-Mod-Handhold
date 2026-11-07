package com.example.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import com.example.FollowStatePayload

object TemplateModClient : ClientModInitializer {
	override fun onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(FollowStatePayload.TYPE) { payload, _ ->
			HandholdClientState.following = payload.following
		}
		ClientTickEvents.END_CLIENT_TICK.register { client ->
			if (client.player == null) {
				HandholdClientState.following = false
			}
		}
	}
}