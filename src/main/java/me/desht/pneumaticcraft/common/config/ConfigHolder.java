package me.desht.pneumaticcraft.common.config;

import me.desht.pneumaticcraft.client.pneumatic_armor.ArmorUpgradeClientRegistry;
import me.desht.pneumaticcraft.common.item.ItemSeismicSensor;
import me.desht.pneumaticcraft.common.tileentity.TileEntityVacuumTrap;
import me.desht.pneumaticcraft.common.worldgen.ModWorldGen;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.lang3.tuple.Pair;

public class ConfigHolder {
    static ClientConfig client;
    static CommonConfig common;
    private static ForgeConfigSpec configCommonSpec;
    private static ForgeConfigSpec configClientSpec;

    public static void init() {
        final Pair<ClientConfig, ForgeConfigSpec> spec1 = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        client = spec1.getLeft();
        configClientSpec = spec1.getRight();

        final Pair<CommonConfig, ForgeConfigSpec> spec2 = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        common = spec2.getLeft();
        configCommonSpec = spec2.getRight();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigHolder.configCommonSpec);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ConfigHolder.configClientSpec);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(ConfigHolder::onConfigChanged);
    }

    private static void onConfigChanged(final ModConfig.ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() == ConfigHolder.configClientSpec) {
            refreshClient();
        } else if (config.getSpec() == ConfigHolder.configCommonSpec) {
            refreshCommon();
        }
    }

    static void refreshClient() {
        ArmorUpgradeClientRegistry.getInstance().refreshConfig();
    }

    static void refreshCommon() {
        TileEntityVacuumTrap.clearBlacklistCache();
        ModWorldGen.clearBlacklistCache();
        ItemSeismicSensor.clearCachedFluids();
    }
}
