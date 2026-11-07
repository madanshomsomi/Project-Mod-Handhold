package com.example

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class FollowStatePayload(val following: Boolean) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

	companion object {
		val TYPE = CustomPacketPayload.Type<FollowStatePayload>(TemplateMod.id("follow_state"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, FollowStatePayload> = StreamCodec.of(
			{ buffer, payload -> buffer.writeBoolean(payload.following) },
			{ buffer -> FollowStatePayload(buffer.readBoolean()) },
		)
	}
}

data class FollowerInputPayload(val strafe: Float, val forward: Float) : CustomPacketPayload {
	override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

	companion object {
		val TYPE = CustomPacketPayload.Type<FollowerInputPayload>(TemplateMod.id("follower_input"))
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, FollowerInputPayload> = StreamCodec.of(
			{ buffer, payload ->
				buffer.writeFloat(payload.strafe)
				buffer.writeFloat(payload.forward)
			},
			{ buffer -> FollowerInputPayload(buffer.readFloat(), buffer.readFloat()) },
		)
	}
}
