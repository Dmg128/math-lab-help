package cope.nebula.client.feature.module.movement;

import cope.nebula.asm.mixins.client.IMinecraft;
import cope.nebula.asm.mixins.client.ITimer;
import cope.nebula.client.feature.module.Module;
import cope.nebula.client.feature.module.ModuleCategory;
import cope.nebula.client.value.Value;
import cope.nebula.util.world.entity.player.MotionUtil;

public class TickShift extends Module {
    public TickShift() {
        super("TickShift", ModuleCategory.MOVEMENT, "does the funny");
    }

    public static final Value<Integer> ticks = new Value<>("Ticks", 20, 1, 40);
    public static final Value<Integer> boost = new Value<>("Boost", 2, 1, 5);
    public static final Value<Boolean> disable = new Value<>("Disable", true);

    private int elapsedTicks = 0;

    @Override
    protected void onActivated() {
        if (nullCheck()) {
            disable();
        }
    }

    @Override
    protected void onDeactivated() {
        elapsedTicks = 0;
        if (!nullCheck()) {
            setTimerSpeed(1.0f);
        }
    }

    @Override
    public void onTick() {
        if (!MotionUtil.isMoving()) {
            ++elapsedTicks;
            if (elapsedTicks > ticks.getValue()) {
                elapsedTicks = ticks.getValue();
            }
        } else {
            if (elapsedTicks <= 0) {
                setTimerSpeed(1.0f);

                if (disable.getValue()) {
                    disable();
                    return;
                }

                return;
            }

            --elapsedTicks;
            setTimerSpeed(boost.getValue());
        }
    }

    private void setTimerSpeed(float speed) {
        ((ITimer) ((IMinecraft) mc).getTimer()).setTickLength(50.0f / speed);
    }
}
