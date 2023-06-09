package tconstruct;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;

import net.minecraft.crash.CallableMinecraftVersion;
import net.minecraft.util.StringUtils;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraftforge.common.MinecraftForge;
import tconstruct.achievements.TAchievements;
import tconstruct.client.event.EventCloakRender;
import tconstruct.common.TContent;
import tconstruct.common.TProxyCommon;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.Detailing;
import tconstruct.library.crafting.LiquidCasting;
import tconstruct.library.util.TabTools;
import tconstruct.plugins.PluginController;
import tconstruct.util.EnvironmentChecks;
import tconstruct.util.TCraftingHandler;
import tconstruct.util.TEventHandler;
import tconstruct.util.TEventHandlerAchievement;
import tconstruct.util.config.BOPConfig;
import tconstruct.util.config.DimensionBlacklist;
import tconstruct.util.config.PHConstruct;
import tconstruct.util.config.TwilightForestConfig;
import tconstruct.util.landmine.behavior.Behavior;
import tconstruct.util.landmine.behavior.stackCombo.SpecialStackHandler;
import tconstruct.util.player.TPlayerHandler;
import tconstruct.worldgen.SlimeIslandGen;
import tconstruct.worldgen.TBaseWorldGenerator;
import tconstruct.worldgen.TerrainGenEventHandler;
import tconstruct.worldgen.village.ComponentSmeltery;
import tconstruct.worldgen.village.ComponentToolWorkshop;
import tconstruct.worldgen.village.TVillageTrades;
import tconstruct.worldgen.village.VillageSmelteryHandler;
import tconstruct.worldgen.village.VillageToolStationHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLInterModComms.IMCEvent;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.VillagerRegistry;
import cpw.mods.fml.relauncher.Side;

/** TConstruct, the tool mod.
 * Craft your tools with style, then modify until the original is gone!
 * @author mDiyo
 */

@Mod(modid = "TConstruct", name = "TConstruct", version = "1.6.X_1.5.5.8",
        dependencies = "required-after:Forge@[8.9,);after:ForgeMultipart;after:MineFactoryReloaded;after:NotEnoughItems;after:Waila;after:ThermalExpansion")
@NetworkMod(serverSideRequired = false, clientSideRequired = true, channels = { "TConstruct" }, packetHandler = tconstruct.util.network.TPacketHandler.class)
public class TConstruct
{
    /** The value of one ingot in millibuckets */
    public static final int ingotLiquidValue = 144;
    public static final int oreLiquidValue = ingotLiquidValue * 2;
    public static final int blockLiquidValue = ingotLiquidValue * 9;
    public static final int chunkLiquidValue = ingotLiquidValue / 2;
    public static final int nuggetLiquidValue = ingotLiquidValue / 9;

    public static final int liquidUpdateAmount = 6;

    // Shared mod logger
    public static final Logger logger = Logger.getLogger("TConstruct");

    /* Instance of this mod, used for grabbing prototype fields */
    @Instance("TConstruct")
    public static TConstruct instance;
    /* Proxies for sides, used for graphics processing */
    @SidedProxy(clientSide = "tconstruct.client.TProxyClient", serverSide = "tconstruct.common.TProxyCommon")
    public static TProxyCommon proxy;

    public TConstruct()
    {
        logger.setParent(FMLCommonHandler.instance().getFMLLogger());
        if (Loader.isModLoaded("Natura"))
        {
            TConstruct.logger.info("[TConstruct] Natura, what are we going to do tomorrow night?");
            TConstruct.logger.info("[Natura] TConstruct, we're going to take over the world!");
        }
        else
        {

            TConstruct.logger.info("[TConstruct] Preparing to take over the world");
        }

        EnvironmentChecks.verifyEnvironmentSanity();

        PluginController.getController().registerBuiltins();
    }

    @EventHandler
    public void preInit (FMLPreInitializationEvent event)
    {

        PHConstruct.initProps(event.getModConfigurationDirectory());
        TConstructRegistry.materialTab = new TabTools("TConstructMaterials");
        TConstructRegistry.toolTab = new TabTools("TConstructTools");
        TConstructRegistry.partTab = new TabTools("TConstructParts");
        TConstructRegistry.blockTab = new TabTools("TConstructBlocks");

        tableCasting = new LiquidCasting();
        basinCasting = new LiquidCasting();
        chiselDetailing = new Detailing();

        content = new TContent();

        events = new TEventHandler();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(new TEventHandlerAchievement());
        content.oreRegistry();

        proxy.registerRenderer();
        proxy.addNames();
        proxy.readManuals();
        proxy.registerKeys();
        proxy.registerTickHandler();

        GameRegistry.registerWorldGenerator(new TBaseWorldGenerator());
        MinecraftForge.TERRAIN_GEN_BUS.register(new TerrainGenEventHandler());
        GameRegistry.registerFuelHandler(content);
        GameRegistry.registerCraftingHandler(new TCraftingHandler());
        NetworkRegistry.instance().registerGuiHandler(instance, proxy);

        if (PHConstruct.addToVillages)
        {
            // adds to the villager spawner egg
            VillagerRegistry.instance().registerVillagerId(78943);
            // moved down, not needed if 'addToVillages' is false
            VillagerRegistry.instance().registerVillageTradeHandler(78943, new TVillageTrades());
            VillagerRegistry.instance().registerVillageCreationHandler(new VillageToolStationHandler());
            VillagerRegistry.instance().registerVillageCreationHandler(new VillageSmelteryHandler());
            try
            {
                if (new CallableMinecraftVersion(null).minecraftVersion().equals("1.6.4"))
                {
                    MapGenStructureIO.func_143031_a(ComponentToolWorkshop.class, "TConstruct:ToolWorkshopStructure");
                    MapGenStructureIO.func_143031_a(ComponentSmeltery.class, "TConstruct:SmelteryStructure");
                }
            }
            catch (Throwable e)
            {

            }
        }

        playerTracker = new TPlayerHandler();
        GameRegistry.registerPlayerTracker(playerTracker);
        MinecraftForge.EVENT_BUS.register(playerTracker);

        PluginController.getController().preInit();
    }

    @EventHandler
    public void init (FMLInitializationEvent event)
    {
        if (event.getSide() == Side.CLIENT)
        {
            MinecraftForge.EVENT_BUS.register(new EventCloakRender());
        }

        content.intermodCommunication();
        TwilightForestConfig.initProps(PHConstruct.cfglocation);
        BOPConfig.initProps(PHConstruct.cfglocation);
        DimensionBlacklist.getbaddimensions();
        GameRegistry.registerWorldGenerator(new SlimeIslandGen(TContent.slimePool.blockID, 0));

        PluginController.getController().init();

        if (PHConstruct.achievementsEnabled)
        {
            TAchievements.init();
        }
    }

    @EventHandler
    public void intermodCommunication (IMCEvent event)
    {
        ImmutableList<IMCMessage> messages = event.getMessages();
        for (IMCMessage message : messages)
        {
            try
            {
                if (message.key.equals("Bind_Knapsack_Dimension"))
                {
                    String string = message.getStringValue();
                    Integer i = Integer.valueOf(string);
                    TPlayerHandler.knapsackDimensions.add(i);
                }
            }
            catch (Exception e)
            {
                logger.log(Level.SEVERE, "Error parsing IMC message from " + message.getSender() == null ? "unknown" : message.getSender(), e);
            }
        }
    }

    @EventHandler
    public void postInit (FMLPostInitializationEvent evt)
    {
        proxy.postInit();
        Behavior.registerBuiltInBehaviors();
        SpecialStackHandler.registerBuiltInStackHandlers();
        content.modIntegration();
        content.addOreDictionarySmelteryRecipes();
        content.createEntities();
        content.modRecipes();

        PluginController.getController().postInit();
    }

    public static LiquidCasting getTableCasting ()
    {
        return tableCasting;
    }

    public static LiquidCasting getBasinCasting ()
    {
        return basinCasting;
    }

    public static Detailing getChiselDetailing ()
    {
        return chiselDetailing;
    }

    public static TContent content;
    public static TEventHandler events;
    public static TPlayerHandler playerTracker;
    public static LiquidCasting tableCasting;
    public static LiquidCasting basinCasting;
    public static Detailing chiselDetailing;
}
