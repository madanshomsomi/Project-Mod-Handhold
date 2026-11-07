package com.example

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

object HandholdService {
	private const val REQUEST_LIFETIME_TICKS = 20 * 60
	private const val FOLLOW_SIDE_DISTANCE = 0.8

	private data class PendingRequest(val sender: UUID, val expiresAt: Long)

	private val pendingRequests = mutableMapOf<UUID, PendingRequest>()
	private val followers = mutableMapOf<UUID, UUID>()
	private var ticks = 0L

	fun registerTickHandler() {
		ServerTickEvents.END_SERVER_TICK.register(::tick)
		ServerLifecycleEvents.SERVER_STOPPED.register {
			pendingRequests.clear()
			followers.clear()
			ticks = 0L
		}
	}

	fun request(sender: ServerPlayer, receiver: ServerPlayer) {
		if (sender.uuid == receiver.uuid) {
			sender.sendSystemMessage(Component.literal("Kamu tidak bisa mengirim request ke diri sendiri."))
			return
		}
		if (isLinked(sender.uuid) || isLinked(receiver.uuid)) {
			sender.sendSystemMessage(Component.literal("Kamu atau pemain tersebut sudah sedang bergandengan."))
			return
		}

		val currentRequest = pendingRequests[receiver.uuid]
		if (currentRequest != null && currentRequest.expiresAt > ticks) {
			sender.sendSystemMessage(Component.literal("Pemain tersebut masih memiliki request yang menunggu."))
			return
		}

		pendingRequests[receiver.uuid] = PendingRequest(sender.uuid, ticks + REQUEST_LIFETIME_TICKS)
		sender.sendSystemMessage(Component.literal("Request bergandengan dikirim ke ${receiver.name.string}."))
		receiver.sendSystemMessage(
			Component.literal("${sender.name.string} mengajakmu bergandengan. Gunakan /handhold accept ${sender.name.string} atau /handhold deny."),
		)
	}

	fun accept(receiver: ServerPlayer, sender: ServerPlayer) {
		val request = pendingRequests[receiver.uuid]
		if (request == null || request.sender != sender.uuid || request.expiresAt <= ticks) {
			receiver.sendSystemMessage(Component.literal("Tidak ada request bergandengan yang masih berlaku dari pemain tersebut."))
			return
		}
		if (isLinked(receiver.uuid) || isLinked(sender.uuid)) {
			pendingRequests.remove(receiver.uuid)
			receiver.sendSystemMessage(Component.literal("Kamu atau pemain tersebut sudah sedang bergandengan."))
			return
		}
		if (receiver.level() !== sender.level()) {
			receiver.sendSystemMessage(Component.literal("Kalian harus berada di dimensi yang sama untuk bergandengan."))
			return
		}

		pendingRequests.remove(receiver.uuid)
		followers[receiver.uuid] = sender.uuid
		receiver.sendSystemMessage(Component.literal("Kamu menerima ajakan ${sender.name.string} dan sekarang akan mengikutinya. Gunakan /handhold release untuk melepas."))
		sender.sendSystemMessage(Component.literal("${receiver.name.string} menerima ajakanmu. Gunakan /handhold release untuk melepas."))
	}

	fun deny(server: MinecraftServer, receiver: ServerPlayer, sender: ServerPlayer?) {
		val request = pendingRequests[receiver.uuid]
		if (request == null || request.expiresAt <= ticks || (sender != null && request.sender != sender.uuid)) {
			receiver.sendSystemMessage(Component.literal("Tidak ada request bergandengan yang cocok untuk ditolak."))
			return
		}

		pendingRequests.remove(receiver.uuid)
		receiver.sendSystemMessage(Component.literal("Request bergandengan ditolak."))
		server.playerList.getPlayer(request.sender)?.sendSystemMessage(
			Component.literal("${receiver.name.string} menolak ajakan bergandenganmu."),
		)
	}

	fun release(server: MinecraftServer, player: ServerPlayer) {
		val link = findLink(player.uuid)
		if (link == null) {
			player.sendSystemMessage(Component.literal("Kamu sedang tidak bergandengan."))
			return
		}

		followers.remove(link.first)
		val partnerId = if (link.first == player.uuid) link.second else link.first
		server.playerList.getPlayer(partnerId)?.sendSystemMessage(
			Component.literal("${player.name.string} melepas pegangan tangan."),
		)
		player.sendSystemMessage(Component.literal("Pegangan tangan dilepas."))
	}

	private fun tick(server: MinecraftServer) {
		ticks++
		pendingRequests.entries.removeIf { (_, request) ->
			request.expiresAt <= ticks || server.playerList.getPlayer(request.sender) == null
		}

		val iterator = followers.entries.iterator()
		while (iterator.hasNext()) {
			val (followerId, leaderId) = iterator.next()
			val follower = server.playerList.getPlayer(followerId)
			val leader = server.playerList.getPlayer(leaderId)
			if (follower == null || leader == null) {
				iterator.remove()
				continue
			}
			if (follower.level() !== leader.level()) {
				iterator.remove()
				follower.sendSystemMessage(Component.literal("Pegangan tangan dilepas karena kalian berada di dimensi berbeda."))
				leader.sendSystemMessage(Component.literal("Pegangan tangan dilepas karena kalian berada di dimensi berbeda."))
				continue
			}

			val yaw = Math.toRadians(leader.yRot.toDouble())
			val side = if (((follower.uuid.leastSignificantBits xor leader.uuid.leastSignificantBits) and 1L) == 0L) 1.0 else -1.0
			val x = leader.x + cos(yaw) * FOLLOW_SIDE_DISTANCE * side
			val z = leader.z + sin(yaw) * FOLLOW_SIDE_DISTANCE * side
			follower.connection.teleport(x, leader.y, z, leader.yRot, leader.xRot)
			follower.setDeltaMovement(0.0, 0.0, 0.0)
		}
	}

	private fun isLinked(playerId: UUID): Boolean =
		followers.containsKey(playerId) || followers.containsValue(playerId)

	private fun findLink(playerId: UUID): Pair<UUID, UUID>? {
		if (followers.containsKey(playerId)) {
			return playerId to followers.getValue(playerId)
		}
		val entry = followers.entries.firstOrNull { it.value == playerId } ?: return null
		return entry.key to entry.value
	}
}
