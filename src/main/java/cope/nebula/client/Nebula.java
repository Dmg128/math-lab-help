package cope.nebula.client;

import cope.nebula.client.manager.*;
import cope.nebula.client.manager.hole.HoleManager;
import cope.nebula.client.manager.macro.MacroManager;
import cope.nebula.client.manager.relation.RelationshipManager;
import cope.nebula.util.internal.fs.FileUtil;
import cope.nebula.util.internal.timing.Stopwatch;
import cope.nebula.util.internal.timing.TimeFormat;
import cope.nebula.util.renderer.FontUtil;
import cope.nebula.util.versioning.BuildConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.nio.file.Files;

import static net.minecraftforge.fml.common.Mod.EventHandler;
import static net.minecraftforge.fml.common.Mod.Instance;

@Mod(modid = Nebula.MODID, name = Nebula.NAME, version = Nebula.VERSION)
public class Nebula {
    public static final String NAME = "Nebula";
    public static final String VERSION = "1.0";
    public static final String HASH = BuildConfig.HASH;
    public static final String FULL_VERSION = VERSION + "-" + HASH;
    public static final String MODID = BuildConfig.NAME;

    @Instance
    private static Nebula INSTANCE;
    private static final Logger LOGGER = LogManager.getLogger(NAME);

    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private MacroManager macroManager;
    private RelationshipManager relationshipManager;
    private HoleManager holeManager;
    private TotemPopManager totemPopManager;

    private RotationManager rotationManager;
    private InteractionManager interactionManager;
    private HotbarManager hotbarManager;
    private ServerManager serverManager;

    private PresetManager presetManager;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Roxanne, Roxanne... All she wanna do is party all night
        LOGGER.info("Loading {} v{}...", NAME, VERSION);

        // create file
        if (!Files.exists(FileUtil.NEBULA)) {
            LOGGER.info("Creating nebula folder");
            FileUtil.mkDir(FileUtil.NEBULA);
        }

        Stopwatch stopwatch = new Stopwatch();
        stopwatch.resetTime();

        FontUtil.registerCustomFonts();
        new ForgeEventManager();

        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        macroManager = new MacroManager();
        relationshipManager = new RelationshipManager();
        holeManager = new HoleManager();
        totemPopManager = new TotemPopManager();

        rotationManager = new RotationManager();
        interactionManager = new InteractionManager();
        hotbarManager = new HotbarManager();
        serverManager = new ServerManager();

        // Goddamn, Roxanne... Never gonna love me but it's alright
        LOGGER.info("Completed setup of {} in {}ms", NAME, stopwatch.getTime(TimeFormat.MILLISECONDS));

        Display.setTitle(NAME + " v" + FULL_VERSION);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        presetManager = new PresetManager();
        LOGGER.info("Loading presets...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> presetManager.saveProfile(null)));
    }

    public static Nebula getInstance() {
        return INSTANCE;
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public MacroManager getMacroManager() {
        return macroManager;
    }

    public HoleManager getHoleManager() {
        return holeManager;
    }

    public TotemPopManager getTotemPopManager() {
        return totemPopManager;
    }

    public RelationshipManager getRelationshipManager() {
        return relationshipManager;
    }

    public RotationManager getRotationManager() {
        return rotationManager;
    }

    public InteractionManager getInteractionManager() {
        return interactionManager;
    }

    public HotbarManager getHotbarManager() {
        return hotbarManager;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }
}
