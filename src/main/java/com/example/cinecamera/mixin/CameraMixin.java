package com.example.cinecamera.mixin;

import com.example.cinecamera.CineCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	// Surum farkina karsi birden fazla aday isim; require=0 -> olmayan isim hata vermez.
	// CineCamera.apply() idempotent oldugu icin birden fazla kez cagrilmasi sorun degil.
	@Inject(method = {"setup", "update", "alignWithEntity"}, at = @At("TAIL"), require = 0)
	private void cinecamera$apply(CallbackInfo ci) {
		CineCamera.apply((Camera) (Object) this);
	}
}
