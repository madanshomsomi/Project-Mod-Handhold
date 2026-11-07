package com.example

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object HandholdService {
	private const val REQUEST_LIFETIME_TICKS = 20 * 60
	private const val FOLLOW_SIDE_DISTANCE = 1.3
	private const val FOLLOW_BEHIND_DISTANCE = 0.25
	private const val FOLLOW_CORRECTION = 0.4
	private const val FOLLOW_RESPONSE = 0.35
	private const val MAX_FOLLOW_SPEED = 0.32
	private const val FOLLOW_STOP_DISTANCE = 0.08
	private const val MAX_SWIM_SPEED = 0.28
	private const val ESCAPE_INPUT_THRESHOLD = 0.6
	private const val ESCAPE_DETECTION_DISTANCE = 3.0
	private const val ESCAPE_INPUT_TICKS = 4
	private const val INPUT_LIFETIME_TICKS = 2
	private const val MAX_BODY_TURN_PER_TICK = 8.0f
	private const val MAX_HEAD_TURN_PER_TICK = 12.0f
	private const val MAX_PITCH_TURN_PER_TICK = 8.0f

	private data class PendingRequest(val sender: UUID, val expiresAt: Long)
	private data class FollowLink(
		val leader: UUID,
		val side: Double,
		var lastLeaderOnGround: Boolean,
		var followVelocityX: Double = 0.0,
		var followVelocityY: Double = 0.0,
		var followVelocityZ: Double = 0.0,
		var inputStrafe: Double = 0.0,
		var inputForward: Double = 0.0,
		var inputTick: Long = -INPUT_LIFETIME_TICKS - 1L,
		var escapeInputTicks: Int = 0,
	)

	private val pendingRequests = mutableMapOf<UUID, PendingRequest>()
	private val followers = mutableMapOf<UUID, FollowLink>()
	private var ticks = 0L

	fun registerTickHandler() {
		ServerTickEvents.END_SERVER_TICK.register(::tick)
	}

	fun request(sender: ServerPlayer, receiver: ServerPlayer) {
		if (sender.uuid == receiver.uuid) {
			sender.sendSystemMessage(Component.literal("Kamu tidak bisa mengirim request ke diri sendiri."))
			return
		}
		if (isFollowing(receiver.uuid)) {
			sender.sendSystemMessage(Component.literal("Pemain tersebut sudah mengikuti pemain lain."))
			return
		}
		if (wouldCreateCycle(sender.uuid, receiver.uuid)) {
			sender.sendSystemMessage(Component.literal("Request ini akan membuat rantai pegangan tangan berputar."))
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

	fun updateFollowerInput(playerId: UUID, strafe: Double, forward: Double) {
		val link = followers[playerId] ?: return
		link.inputStrafe = strafe.coerceIn(-1.0, 1.0)
		link.inputForward = forward.coerceIn(-1.0, 1.0)
		link.inputTick = ticks
	}

	fun accept(receiver: ServerPlayer, sender: ServerPlayer) {
		val request = pendingRequests[receiver.uuid]
		if (request == null || request.sender != sender.uuid || request.expiresAt <= ticks) {
			receiver.sendSystemMessage(Component.literal("Tidak ada request bergandengan yang masih berlaku dari pemain tersebut."))
			return
		}
		if (isFollowing(receiver.uuid) || wouldCreateCycle(sender.uuid, receiver.uuid)) {
			pendingRequests.remove(receiver.uuid)
			receiver.sendSystemMessage(Component.literal("Request tidak dapat diterima karena akan membuat rantai pegangan tangan berputar atau pemain sudah memiliki leader."))
			return
		}
		if (receiver.level() !== sender.level()) {
			receiver.sendSystemMessage(Component.literal("Kalian harus berada di dimensi yang sama untuk bergandengan."))
			return
		}

		val side = if (((receiver.uuid.leastSignificantBits xor sender.uuid.leastSignificantBits) and 1L) == 0L) 1.0 else -1.0
		pendingRequests.remove(receiver.uuid)
		followers[receiver.uuid] = FollowLink(
			leader = sender.uuid,
			side = side,
			lastLeaderOnGround = sender.onGround(),
		)
		receiver.setDeltaMovement(0.0, receiver.deltaMovement.y, 0.0)
		HandholdNetworking.sendFollowState(receiver, true)
		if (!HandholdNetworking.supportsFollowState(receiver)) {
			receiver.sendSystemMessage(
				Component.literal("Untuk mengikuti dengan stabil, pasang Handhold versi yang sama di client Minecraft lalu mulai ulang game."),
			)
		}
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

		followers.remove(link.first) ?: return
		server.playerList.getPlayer(link.first)?.let { HandholdNetworking.sendFollowState(it, false) }
		val partnerId = if (link.first == player.uuid) link.second else link.first
		server.playerList.getPlayer(partnerId)?.sendSystemMessage(
			Component.literal("${player.name.string} melepas pegangan tangan."),
		)
		player.sendSystemMessage(Component.literal("Pegangan tangan dilepas."))
	}

	private fun tick(server: MinecraftServer) {
		ticks++
		pendingRequests.entries.removeIf { (receiverId, request) ->
			request.expiresAt <= ticks ||
				server.playerList.getPlayer(request.sender) == null ||
				server.playerList.getPlayer(receiverId) == null
		}

		val iterator = followers.entries.iterator()
		while (iterator.hasNext()) {
			val (followerId, link) = iterator.next()
			val leaderId = link.leader
			val follower = server.playerList.getPlayer(followerId)
			val leader = server.playerList.getPlayer(leaderId)
			if (follower == null || leader == null) {
				iterator.remove()
				continue
			}
			if (follower.level() !== leader.level()) {
				iterator.remove()
				HandholdNetworking.sendFollowState(follower, false)
				follower.sendSystemMessage(Component.literal("Pegangan tangan dilepas karena kalian berada di dimensi berbeda."))
				leader.sendSystemMessage(Component.literal("Pegangan tangan dilepas karena kalian berada di dimensi berbeda."))
				continue
			}

			val separationX = follower.x - leader.x
			val separationY = follower.y - leader.y
			val separationZ = follower.z - leader.z
			val separation = sqrt(separationX * separationX + separationY * separationY + separationZ * separationZ)
			if (ticks - link.inputTick <= INPUT_LIFETIME_TICKS && separation <= ESCAPE_DETECTION_DISTANCE) {
				val yaw = Math.toRadians(follower.yRot.toDouble())
				val inputX = link.inputStrafe * cos(yaw) - link.inputForward * sin(yaw)
				val inputZ = link.inputForward * cos(yaw) + link.inputStrafe * sin(yaw)
				val outwardInput = if (separation > 0.0) {
					(inputX * separationX + inputZ * separationZ) / separation
				} else {
					0.0
				}
				link.escapeInputTicks = if (outwardInput > ESCAPE_INPUT_THRESHOLD) {
					link.escapeInputTicks + 1
				} else {
					0
				}
				if (link.escapeInputTicks >= ESCAPE_INPUT_TICKS) {
					iterator.remove()
					HandholdNetworking.sendFollowState(follower, false)
					follower.sendSystemMessage(Component.literal("Pegangan tangan dilepas karena kamu mencoba bergerak menjauh."))
					leader.sendSystemMessage(Component.literal("${follower.name.string} melepas pegangan tangan dengan bergerak menjauh."))
					continue
				}
			} else if (ticks - link.inputTick > INPUT_LIFETIME_TICKS) {
				link.escapeInputTicks = 0
			}

			if (link.lastLeaderOnGround && !leader.onGround() && leader.deltaMovement.y > 0.1 &&
				follower.onGround() && !follower.isInWater
			) {
				follower.jumpFromGround()
				follower.connection.send(ClientboundSetEntityMotionPacket(follower))
			}
			link.lastLeaderOnGround = leader.onGround()

			val yaw = Math.toRadians(leader.yRot.toDouble())
			val targetX = leader.x + cos(yaw) * FOLLOW_SIDE_DISTANCE * link.side +
				sin(yaw) * FOLLOW_BEHIND_DISTANCE
			val targetZ = leader.z + sin(yaw) * FOLLOW_SIDE_DISTANCE * link.side -
				cos(yaw) * FOLLOW_BEHIND_DISTANCE
			val targetDeltaX = targetX - follower.x
			val targetDeltaZ = targetZ - follower.z
			val horizontalDistance = sqrt(targetDeltaX * targetDeltaX + targetDeltaZ * targetDeltaZ)
			val maxSpeed = if (follower.isInWater && leader.isInWater) MAX_SWIM_SPEED else MAX_FOLLOW_SPEED
			val desiredHorizontalSpeed = if (horizontalDistance <= FOLLOW_STOP_DISTANCE) {
				0.0
			} else {
				min(maxSpeed, horizontalDistance * FOLLOW_CORRECTION)
			}
			val desiredVelocityX = if (horizontalDistance == 0.0) {
				0.0
			} else {
				targetDeltaX / horizontalDistance * desiredHorizontalSpeed
			}
			val desiredVelocityZ = if (horizontalDistance == 0.0) {
				0.0
			} else {
				targetDeltaZ / horizontalDistance * desiredHorizontalSpeed
			}

			link.followVelocityX += (desiredVelocityX - link.followVelocityX) * FOLLOW_RESPONSE
			link.followVelocityZ += (desiredVelocityZ - link.followVelocityZ) * FOLLOW_RESPONSE

			val canFollowVertically = (follower.isInWater && leader.isInWater) ||
				(!follower.onGround() && !leader.onGround())
			if (canFollowVertically) {
				val verticalError = leader.y - follower.y
				val desiredVerticalSpeed = if (abs(verticalError) <= FOLLOW_STOP_DISTANCE) {
					0.0
				} else {
					(verticalError * FOLLOW_CORRECTION).coerceIn(-MAX_SWIM_SPEED, MAX_SWIM_SPEED)
				}
				link.followVelocityY += (desiredVerticalSpeed - link.followVelocityY) * FOLLOW_RESPONSE
			} else {
				link.followVelocityY = 0.0
			}

			val previousX = follower.x
			val previousY = follower.y
			val previousZ = follower.z
			val previousYaw = follower.yRot
			val previousPitch = follower.xRot
			val moveY = if (canFollowVertically) link.followVelocityY else 0.0
			follower.move(
				MoverType.SELF,
				Vec3(link.followVelocityX, moveY, link.followVelocityZ),
			)

			if (abs((follower.x - previousX) - link.followVelocityX) > 0.001) {
				link.followVelocityX = 0.0
			}
			if (abs((follower.z - previousZ) - link.followVelocityZ) > 0.001) {
				link.followVelocityZ = 0.0
			}
			if (canFollowVertically && abs((follower.y - previousY) - moveY) > 0.001) {
				link.followVelocityY = 0.0
			}

			follower.updateSwimming()
			follower.setYRot(approachAngle(follower.yRot, leader.yRot, MAX_BODY_TURN_PER_TICK))
			follower.setYBodyRot(approachAngle(follower.yBodyRot, leader.yBodyRot, MAX_BODY_TURN_PER_TICK))
			follower.setYHeadRot(approachAngle(follower.yHeadRot, leader.yHeadRot, MAX_HEAD_TURN_PER_TICK))
			follower.setXRot(approachAngle(follower.xRot, leader.xRot, MAX_PITCH_TURN_PER_TICK))

			val movedForFollow = abs(follower.x - previousX) > 0.001 ||
				abs(follower.y - previousY) > 0.001 ||
				abs(follower.z - previousZ) > 0.001 ||
				abs(angleDifference(follower.yRot, previousYaw)) > 0.001f ||
				abs(follower.xRot - previousPitch) > 0.001f
			if (movedForFollow) {
				follower.connection.teleport(PositionMoveRotation.of(follower), emptySet())
			}

		}
	}

	private fun approachAngle(current: Float, target: Float, maxStep: Float): Float {
		val difference = angleDifference(current, target)
		return current + difference.coerceIn(-maxStep, maxStep)
	}

	private fun angleDifference(from: Float, to: Float): Float = (to - from + 540.0f) % 360.0f - 180.0f

	private fun isFollowing(playerId: UUID): Boolean = followers.containsKey(playerId)

	fun isFollowingPlayer(playerId: UUID): Boolean = isFollowing(playerId)

	private fun wouldCreateCycle(leaderId: UUID, followerId: UUID): Boolean {
		var currentId = leaderId
		while (true) {
			if (currentId == followerId) {
				return true
			}
			currentId = followers[currentId]?.leader ?: return false
		}
	}

	private fun findLink(playerId: UUID): Pair<UUID, UUID>? {
		if (followers.containsKey(playerId)) {
			return playerId to followers.getValue(playerId).leader
		}
		val entry = followers.entries.firstOrNull { it.value.leader == playerId } ?: return null
		return entry.key to entry.value.leader
	}
}
