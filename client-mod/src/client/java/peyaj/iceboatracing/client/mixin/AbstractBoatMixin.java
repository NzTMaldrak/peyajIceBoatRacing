package peyaj.iceboatracing.client.mixin;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import peyaj.iceboatracing.client.RacePhysics;

@Mixin(AbstractBoat.class)
abstract class AbstractBoatMixin {

    @Shadow
    private float landFriction;

    @Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
    private void iceboatracing$preserveDownhillMomentum(
            CallbackInfoReturnable<AbstractBoat.Status> cir) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (cir.getReturnValue() == AbstractBoat.Status.IN_AIR
                && RacePhysics.hasIceBelow(boat, 1.35)) {
            landFriction = RacePhysics.AIR_FRICTION;
            cir.setReturnValue(AbstractBoat.Status.ON_LAND);
        }
    }
}
