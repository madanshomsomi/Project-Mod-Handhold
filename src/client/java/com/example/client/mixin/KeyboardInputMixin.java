package com.example.client.mixin;

import com.example.FollowerInputPayload;
import com.example.client.HandholdClientState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
	@Shadow
	@Final
	private Options options;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void handhold$controlFollowerInput(CallbackInfo callback) {
		if (!HandholdClientState.following) {
			return;
		}

		float forward = (options.keyUp.isDown() ? 1.0f : 0.0f) -
			(options.keyDown.isDown() ? 1.0f : 0.0f);
		float strafe = (options.keyRight.isDown() ? 1.0f : 0.0f) -
			(options.keyLeft.isDown() ? 1.0f : 0.0f);
		if (forward != 0.0f || strafe != 0.0f) {
			ClientPlayNetworking.send(new FollowerInputPayload(strafe, forward));
		}

		ClientInputAccessor input = (ClientInputAccessor) (Object) this;
		input.handhold$setKeyPresses(Input.EMPTY);
		input.handhold$setMoveVector(Vec2.ZERO);
		callback.cancel();
	}
}
