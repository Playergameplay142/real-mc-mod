package com.example.cinecamera.mixin;

import com.example.cinecamera.CineCamera;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	// Vanilla havada duran el/esya cizimini iptal et; esya artik gercek kolun elinde cizilir.
	// 26.1.x: renderHandsWithItems, 26.2+: submitHandsWithItems
	@Inject(method = {"renderHandsWithItems", "submitHandsWithItems"}, at = @At("HEAD"), cancellable = true, require = 0)
	private void cinecamera$noFloatingHands(CallbackInfo ci) {
		if (CineCamera.fullBodyActive()) ci.cancel();
	}
}
