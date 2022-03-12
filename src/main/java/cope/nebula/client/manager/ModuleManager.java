package cope.nebula.client.manager;

import com.google.common.collect.Lists;
import cope.nebula.client.Nebula;
import cope.nebula.client.feature.module.Module;
import cope.nebula.client.feature.module.combat.Aura;
import cope.nebula.client.feature.module.combat.AutoTotem;
import cope.nebula.client.feature.module.combat.Criticals;
import cope.nebula.client.feature.module.combat.SelfFill;
import cope.nebula.client.feature.module.movement.*;
import cope.nebula.client.feature.module.world.FastPlace;
import cope.nebula.client.feature.module.render.Brightness;
import cope.nebula.client.feature.module.world.FastBreak;
import cope.nebula.util.Globals;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;

/**
 * Holds all modules of the client
 *
 * Also handles any key inputs needed to toggle modules
 *
 * @author aesthetical
 * @since 3/7/22
 */
public class ModuleManager implements Globals {
    private final ArrayList<Module> modules = Lists.newArrayList(
            // Combat
            new Aura(),
            new AutoTotem(),
            new Criticals(),
            new SelfFill(),

            // Movement
            new NoSlowDown(),
            new PacketFly(),
            new Sprint(),
            new TickShift(),
            new Velocity(),

            // Render
            new Brightness(),

            // World
            new FastBreak(),
            new FastPlace()
    );

    public ModuleManager() {
        EVENT_BUS.register(this);
        Nebula.getLogger().info("Loaded {} modules", modules.size());
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        int keyCode = Keyboard.getEventKey();
        if (keyCode != Keyboard.KEY_NONE && !Keyboard.getEventKeyState() && mc.currentScreen == null) {
            modules.forEach((module) -> {
                if (module.getKeyBind() == keyCode) {
                    module.toggle();
                }
            });
        }
    }

    public ArrayList<Module> getModules() {
        return modules;
    }
}
