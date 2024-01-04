package io.github.niterux.bevoauthoss.mixin;

import net.minecraft.client.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.legacyminecraft.authentication.AuthenticationThread;

@Mixin(Session.class)
public class MinecraftMixin {

	@Shadow
	public String sessionId;

	@Shadow
	public String username;

	@Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;)V", at = @At("TAIL"))
	private void bevoauthinject(CallbackInfo ci) {
		AuthenticationThread authenticationThread = new AuthenticationThread(this.username, this.sessionId);
		authenticationThread.start();
	}
}
