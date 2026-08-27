package peyaj.iceboatracing.client.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import peyaj.iceboatracing.client.RacePhysics;

@Mixin(Entity.class)
abstract class EntityMixin {

    @Inject(method = "maxUpStep", at = @At("HEAD"), cancellable = true)
    private void iceboatracing$nativeIceStep(CallbackInfoReturnable<Float> cir) {
        if (RacePhysics.canClimbIce((Entity) (Object) this)) {
            cir.setReturnValue(RacePhysics.STEP_HEIGHT);
        }
    }

    @Redirect(
            method = "collide",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;onGround()Z"))
    private boolean iceboatracing$allowConsecutiveIceSteps(Entity entity) {
        return RacePhysics.canClimbIce(entity) || entity.onGround();
    }
}
