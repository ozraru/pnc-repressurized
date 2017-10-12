package me.desht.pneumaticcraft.client.render.tileentity;

import me.desht.pneumaticcraft.common.block.BlockPressureTube;
import me.desht.pneumaticcraft.common.block.Blockss;
import me.desht.pneumaticcraft.common.block.tubes.TubeModule;
import me.desht.pneumaticcraft.common.tileentity.TileEntityPressureTube;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.lwjgl.opengl.GL11;

public class RenderPressureTubeModule extends TileEntitySpecialRenderer<TileEntityPressureTube> {

    @Override
    public void render(TileEntityPressureTube tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        GlStateManager.pushMatrix();

        FMLClientHandler.instance().getClient().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.color(1, 1, 1);

        GlStateManager.translate((float) x + 0.5F, (float) y + 1.5F, (float) z + 0.5F);
        GlStateManager.scale(1.0F, -1F, -1F);

        // "fake" module is for showing a preview of where the module would be placed
        attachFakeModule(tile);

        for (int i = 0; i < tile.modules.length; i++) {
            TubeModule module = tile.modules[i];
            if (module != null) {
                if (module.isFake()) {
                          GL11.glEnable(GL11.GL_BLEND);
                          GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                          GL11.glColor4d(1, 1, 1, 0.5);
                }

                module.getModel().renderModel(0.0625f, module.getDirection(), partialTicks);

                if (module.isFake()) {
                    tile.modules[i] = null;
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glColor4d(1, 1, 1, 1);
                }
            }
        }
        GlStateManager.color(1, 1, 1, 1);

        GlStateManager.enableLighting();
        GlStateManager.enableAlpha();

        GlStateManager.popMatrix();
    }

    private void attachFakeModule(TileEntityPressureTube tile) {
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult pos = mc.objectMouseOver;
        if (pos != null && pos.typeOfHit == RayTraceResult.Type.BLOCK && pos.getBlockPos().equals(tile.getPos()) && mc.world.getTileEntity(pos.getBlockPos()) == tile) {
            ((BlockPressureTube) Blockss.PRESSURE_TUBE).tryPlaceModule(mc.player, mc.world, tile.getPos(), pos.sideHit, true);
        }
    }
}
