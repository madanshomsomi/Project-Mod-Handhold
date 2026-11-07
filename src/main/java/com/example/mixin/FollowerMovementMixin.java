package com.example.mixin;

import com.example.HandholdService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class FollowerMovementMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void handhold$ignoreClientMovementWhileFollowing(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
		if (HandholdService.INSTANCE.isFollowingPlayer(player.getUUID())) {
			callback.cancel();
		}
	}
}
