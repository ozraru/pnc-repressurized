package pneumaticCraft.common.block.pneumaticPlants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import pneumaticCraft.common.Config;
import pneumaticCraft.common.item.Itemss;
import pneumaticCraft.common.network.NetworkHandler;
import pneumaticCraft.common.network.PacketSpawnParticle;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public abstract class BlockPneumaticPlantBase extends BlockFlower{
    IIcon[] texture;

    protected BlockPneumaticPlantBase(){
        super(0);
        setTickRandomly(true);
        float var3 = 0.5F;
        setBlockBounds(0.5F - var3, 0.0F, 0.5F - var3, 0.5F + var3, 0.25F, 0.5F + var3);
        setCreativeTab((CreativeTabs)null);
        setHardness(0.0F);
        setStepSound(Block.soundTypeGrass);
        disableStats();
        if(isPlantHanging()) {
            setBlockBounds(0, 0.75F, 0, 1, 1, 1);
        }
    }

    @Override
    public void registerBlockIcons(IIconRegister register){
        texture = new IIcon[7];
        for(int i = 0; i < 7; i++) {
            texture[i] = register.registerIcon(getTextureString() + i);
        }
    }

    /**
     * returns a list of blocks with the same ID, but different meta (eg: wood returns 4 blocks)
     */
    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item p_149666_1_, CreativeTabs p_149666_2_, List p_149666_3_){}

    /**
     * From the specified side and block metadata retrieves the blocks texture.
     * Args: side, metadata
     */
    @Override
    public IIcon getIcon(int side, int meta){
        if(meta == 14) return texture[6];
        return texture[meta % 7];
    }

    protected abstract String getTextureString();

    protected abstract boolean canGrowWithLightValue(int lightValue);

    protected boolean skipGrowthCheck(World world, int x, int y, int z){
        return false;
    }

    public boolean isPlantHanging(){
        return false;
    }

    public void executeFullGrownEffect(World world, int x, int y, int z, Random rand){}

    // make this method public
    public boolean canPlantGrowOnThisBlock(Block block){
        return canPlaceBlockOn(block);
    }

    /**
     * Ticks the block if it's been scheduled
     * metadata mapping: 0-6 = world generated plants, 7-13 = player dropped plants.
     */
    @Override
    public void updateTick(World world, int x, int y, int z, Random rand){
        super.updateTick(world, x, y, z, rand);
        if(!world.isRemote) {
            float var7 = getGrowthRate(world, x, y, z);

            if(canGrowWithLightValue(world.getBlockLightValue(x, y + (isPlantHanging() ? -1 : 1), z)) && rand.nextInt((int)(25.0F / var7) + 1) == 0 || skipGrowthCheck(world, x, y, z)) {
                int meta = world.getBlockMetadata(x, y, z);
                if(meta < 13) {
                    if(meta != 6) {//let world generated full-grown plants not grow.
                        ++meta;
                        world.setBlockMetadataWithNotify(x, y, z, meta, 3);
                    }

                } else if(meta == 13 && rand.nextInt(20) == 0) {
                    world.setBlockMetadataWithNotify(x, y, z, 6, 0);
                } else {
                    // if the plant is allowed to execute the full grown effect
                    // do so.
                    if(Config.configPlantFullGrownEffect[getSeedDamage()]) executeFullGrownEffect(world, x, y, z, rand);
                }
            }
        }
    }

    protected void spawnParticle(String particleName, World world, double spawnX, double spawnY, double spawnZ, double spawnMotX, double spawnMotY, double spawnMotZ){
        NetworkHandler.sendToAllAround(new PacketSpawnParticle(particleName, spawnX, spawnY, spawnZ, spawnMotX, spawnMotY, spawnMotZ), world);
    }

    /**
     * Apply bonemeal to the crops.
     */

    public boolean fertilize(World par1World, int par2, int par3, int par4, EntityPlayer player){
        int meta = par1World.getBlockMetadata(par2, par3, par4);
        if(meta == 6 || meta == 13) {
            par1World.setBlockMetadataWithNotify(par2, par3, par4, 13, 0);
            executeFullGrownEffect(par1World, par2, par3, par4, par1World.rand);
            return true;
        }
        if(meta > 13) return false;
        int l = meta + MathHelper.getRandomIntegerInRange(par1World.rand, 2, 5);
        if(meta < 6 && l > 6) {
            l = 6;
        } else if(meta > 6 && l > 13) {
            l = 13;
        }
        par1World.setBlockMetadataWithNotify(par2, par3, par4, l, 3);
        return true;
        // player.inventory.getCurrentItem().stackSize--;//use the item
        // updateTick(par1World, par2, par3, par4, new Random());//Immediately
        // spawn a seed.
        // Doesnt work, because it first goes through the get growth rate thing.
    }

    @Override
    public boolean canBlockStay(World par1World, int par2, int par3, int par4){
        Block soil = par1World.getBlock(par2, par3 - (isPlantHanging() ? -1 : 1), par4);
        return canGrowWithLightValue(par1World.getFullBlockLightValue(par2, par3, par4)) && soil != null && canPlantGrowOnThisBlock(soil);
    }

    /**
     * Gets the growth rate for the crop. Setup to encourage rows by halving
     * growth rate if there is diagonals, crops on different sides that aren't
     * opposing, and by adding growth for every crop next to this one (and for
     * crop below this one). Args: x, y, z
     */
    protected float getGrowthRate(World world, int x, int y, int z){
        float growthFactor = 1.0F;
        Block var6 = world.getBlock(x, y, z - 1);
        Block var7 = world.getBlock(x, y, z + 1);
        Block var8 = world.getBlock(x - 1, y, z);
        Block var9 = world.getBlock(x + 1, y, z);
        Block var10 = world.getBlock(x - 1, y, z - 1);
        Block var11 = world.getBlock(x + 1, y, z - 1);
        Block var12 = world.getBlock(x + 1, y, z + 1);
        Block var13 = world.getBlock(x - 1, y, z + 1);
        boolean var14 = var8 == this || var9 == this;
        boolean var15 = var6 == this || var7 == this;
        boolean var16 = var10 == this || var11 == this || var12 == this || var13 == this;

        for(int var17 = x - 1; var17 <= x + 1; ++var17) {
            for(int var18 = z - 1; var18 <= z + 1; ++var18) {
                Block var19 = world.getBlock(var17, y - 1, var18);
                float var20 = 0.0F;

                if(var19.canSustainPlant(world, var17, y - (isPlantHanging() ? -1 : 1), var18, ForgeDirection.UP, this)) {
                    var20 = 1.0F;

                    if(var19.isFertile(world, var17, y - (isPlantHanging() ? -1 : 1), var18)) {
                        var20 = 3.0F;
                    }
                }

                if(var17 != x || var18 != z) {
                    var20 /= 4.0F;
                }

                growthFactor += var20;
            }
        }

        if(var16 || var14 && var15) {
            growthFactor /= 2.0F;
        }
        return growthFactor;
    }

    /**
     * The type of render function that is called for this block
     */
    @Override
    public int getRenderType(){
        return 1;// flower rendertype
    }

    /**
     * Generate a seed ItemStack for this crop.
     */
    protected abstract int getSeedDamage();

    /**
     * Get the block's damage value (for use with pick block).
     */
    @Override
    public int getDamageValue(World par1World, int par2, int par3, int par4){
        return getSeedDamage();
    }

    @Override
    @SideOnly(Side.CLIENT)
    /**
     * only called by clickMiddleMouseButton , and passed to inventory.setCurrentItem (along with isCreative)
     */
    public Item getItem(World par1World, int par2, int par3, int par4){
        return Itemss.plasticPlant;
    }

    /**
     * Drops the block items with a specified chance of dropping the specified
     * items
     */
    /* @Override
     public void dropBlockAsItemWithChance(World par1World, int par2, int par3, int par4, int par5, float par6, int par7){
         super.dropBlockAsItemWithChance(par1World, par2, par3, par4, par5, par6, 0);
     }
    */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune){
        ArrayList<ItemStack> ret = new ArrayList<ItemStack>();// super.getBlockDropped(world,
                                                              // x, y, z,
                                                              // metadata,
                                                              // fortune);
        int seedDamage = getSeedDamage();
        ret.add(new ItemStack(Itemss.plasticPlant, 1, seedDamage));
        if(metadata == 6 || metadata == 13) {
            ret.add(new ItemStack(Itemss.plasticPlant, world.rand.nextInt(2) + 1, seedDamage));
        }

        return ret;
    }

    /*
     * @SideOnly(Side.CLIENT) public int idPicked(World par1World, int par2, int
     * par3, int par4) { return this.getSeedItem(); }
     */
}
