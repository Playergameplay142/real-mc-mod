package com.example.cinecamera.mixin;

import com.example.cinecamera.FirstPersonBody;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	// Acik descriptor: bridge (synthetic) metoda degil gercek metoda baglanir.
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"), require = 0)
	private void cinecamera$pose(HumanoidRenderState state, CallbackInfo ci) {
		try {
			FirstPersonBody.apply((HumanoidModel<?>) (Object) this, state);
		} catch (Throwable t) {
			FirstPersonBody.fail(t);
		}
	}
}
