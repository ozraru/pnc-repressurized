package me.desht.pneumaticcraft.common.progwidgets;

import com.google.common.collect.ImmutableList;
import me.desht.pneumaticcraft.api.drone.ProgWidgetType;
import me.desht.pneumaticcraft.common.ai.DroneAIManager;
import me.desht.pneumaticcraft.common.ai.IDroneBase;
import me.desht.pneumaticcraft.common.config.PNCConfig;
import me.desht.pneumaticcraft.common.core.ModProgWidgets;
import me.desht.pneumaticcraft.common.progwidgets.area.*;
import me.desht.pneumaticcraft.common.progwidgets.area.AreaType.AreaTypeWidget;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import me.desht.pneumaticcraft.common.variables.GlobalVariableManager;
import me.desht.pneumaticcraft.lib.Log;
import me.desht.pneumaticcraft.lib.Textures;
import net.minecraft.entity.Entity;
import net.minecraft.item.DyeColor;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

/**
 * The Area widget itself
 */
public class ProgWidgetArea extends ProgWidget implements IAreaProvider, IVariableWidget {
    private DroneAIManager aiManager;
    private final BlockPos[] pos = new BlockPos[] { null, null };
    private final String[] varNames = new String[] { "", "" };
    public AreaType type = new AreaTypeBox();
    private IVariableProvider variableProvider;
    private UUID playerID;  // for player-global variable context

    // map string area types to internal numeric ID's (for more efficient sync)
    private static final Map<String, Integer> areaTypeToID = new HashMap<>();
    // collection of area type factories, indexed by internal ID
    private static final List<Supplier<? extends AreaType>> areaTypeFactories = new ArrayList<>();

    static {
        register(AreaTypeBox.ID, AreaTypeBox::new);
        register(AreaTypeSphere.ID, AreaTypeSphere::new);
        register(AreaTypeLine.ID, AreaTypeLine::new);
        register(AreaTypeWall.ID, AreaTypeWall::new);
        register(AreaTypeCylinder.ID, AreaTypeCylinder::new);
        register(AreaTypePyramid.ID, AreaTypePyramid::new);
        register(AreaTypeGrid.ID, AreaTypeGrid::new);
        register(AreaTypeRandom.ID, AreaTypeRandom::new);
    }

    public ProgWidgetArea() {
        super(ModProgWidgets.AREA.get());
    }

    private static <T extends AreaType> void register(String id, Supplier<T> factory) {
        if (areaTypeToID.containsKey(id)) {
            throw new IllegalStateException("Area type " + id + " could not be registered, duplicate id");
        }
        areaTypeFactories.add(factory);
        areaTypeToID.put(id, areaTypeFactories.size() - 1);
    }

    public static List<AreaType> getAllAreaTypes() {
        return areaTypeFactories.stream().map(Supplier::get).collect(Collectors.toList());
    }

    public static ProgWidgetArea fromPosition(BlockPos p1) {
        return fromPositions(p1, p1);
    }

    public static ProgWidgetArea fromPosition(BlockPos p1, int expand) {
        return fromPosition(p1, expand, expand, expand);
    }

    public static ProgWidgetArea fromPosition(BlockPos p1, int expandX, int expandY, int expandZ) {
        int x = expandX / 2;
        int y = expandY / 2;
        int z = expandZ / 2;
        return fromPositions(p1.add(-x, -y, -z), p1.add(x, y, z));
    }

    public static ProgWidgetArea fromPositions(BlockPos p1, BlockPos p2) {
        ProgWidgetArea area = new ProgWidgetArea();
        area.setPos(0, p1);
        area.setPos(1, p2);
        return area;
    }

    @Override
    public List<ITextComponent> getExtraStringInfo() {
        List<ITextComponent> res = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            if (!varNames[i].isEmpty()) {
                res.add(new StringTextComponent("\"" + varNames[i] + "\""));
            } else if (PneumaticCraftUtils.isValidPos(pos[i])) {
                res.add(new StringTextComponent(PneumaticCraftUtils.posToString(pos[i])));
            }
        }
        if (res.size() == 2) {
            res.add(new StringTextComponent(type.toString()));
        }
        return res;
    }

    @Override
    public void getTooltip(List<ITextComponent> curTooltip) {
        super.getTooltip(curTooltip);

        int n = curTooltip.size();
        for (int i = 0; i < 2; i++) {
            String text = varNames[i].isEmpty() ?
                    pos[i] == null ? null : PneumaticCraftUtils.posToString(pos[i]) :
                    String.format("var \"%s\"", varNames[i]);
            if (text != null) {
                curTooltip.add(new StringTextComponent("P" + (i + 1) + ": ").append(new StringTextComponent(text).mergeStyle(TextFormatting.YELLOW)));
            }
        }
        if (curTooltip.size() - n == 2) {
            addAreaTypeTooltip(curTooltip);
        }
    }

    public void addAreaTypeTooltip(List<ITextComponent> curTooltip) {
        curTooltip.add(xlate("pneumaticcraft.gui.progWidget.area.type").append(xlate(type.getTranslationKey()).mergeStyle(TextFormatting.YELLOW)));

        List<AreaTypeWidget> widgets = new ArrayList<>();
        type.addUIWidgets(widgets);
        for (AreaTypeWidget widget : widgets) {
            curTooltip.add(xlate(widget.title).appendString(" ").append(new StringTextComponent(widget.getCurValue()).mergeStyle(TextFormatting.YELLOW)));
        }
    }

    @Override
    public void addErrors(List<ITextComponent> curInfo, List<IProgWidget> widgets) {
        super.addErrors(curInfo, widgets);
        if (varNames[0].isEmpty() && varNames[1].isEmpty() && pos[0] == null && pos[1] == null) {
            curInfo.add(xlate("pneumaticcraft.gui.progWidget.area.error.noArea"));
        }
        if (!(type instanceof AreaTypeBox)) {
            IProgWidget p = this;
            while ((p = p.getParent()) != null) {
                ProgWidgetType<?> type = p.getType();
                if (type == ModProgWidgets.ENTITY_ATTACK.get() || type == ModProgWidgets.ENTITY_IMPORT.get()
                        || type == ModProgWidgets.ENTITY_RIGHT_CLICK.get() || type == ModProgWidgets.CONDITION_ENTITY.get()
                        || type == ModProgWidgets.PICKUP_ITEM.get()) {
                    curInfo.add(xlate("pneumaticcraft.gui.progWidget.area.error.onlyAreaTypeBox", xlate(p.getTranslationKey())));
                    break;
                }
            }
        }
    }

    private BlockPos[] getAreaPoints() {
        BlockPos[] points = new BlockPos[2];
        for (int i = 0; i < 2; i++) {
            if (varNames[i].isEmpty()) points[i] = pos[i];
            else points[i] = variableProvider != null ?
                    variableProvider.getCoordinate(playerID, varNames[i]).orElse(null) :
                    null;
        }
        if (points[0] == null && points[1] == null) {
            return new BlockPos[]{null, null};
        } else if (points[0] == null) {
            return new BlockPos[]{points[1], null};
        } else if (points[1] == null) {
            return new BlockPos[]{points[0], null};
        } else {
            return points;
        }
    }

    @Override
    public boolean hasStepInput() {
        return false;
    }

    @Override
    public ProgWidgetType<?> returnType() {
        return ModProgWidgets.AREA.get();
    }

    @Override
    public List<ProgWidgetType<?>> getParameters() {
        return ImmutableList.of(ModProgWidgets.AREA.get());
    }

    @Override
    public ResourceLocation getTexture() {
        return Textures.PROG_WIDGET_AREA;
    }

    @Override
    public void getArea(Set<BlockPos> area) {
        getArea(area, type);
    }

    public void getArea(Set<BlockPos> area, AreaType areaType) {
        BlockPos[] areaPoints = getAreaPoints();
        if (areaPoints[0] == null) return;

        int minX, minY, minZ;
        int maxX, maxY, maxZ;
        if (areaPoints[1] != null) {
            minX = Math.min(areaPoints[0].getX(), areaPoints[1].getX());
            minY = Math.min(areaPoints[0].getY(), areaPoints[1].getY());
            minZ = Math.min(areaPoints[0].getZ(), areaPoints[1].getZ());
            maxX = Math.max(areaPoints[0].getX(), areaPoints[1].getX());
            maxY = Math.max(areaPoints[0].getY(), areaPoints[1].getY());
            maxZ = Math.max(areaPoints[0].getZ(), areaPoints[1].getZ());
        } else {
            minX = maxX = areaPoints[0].getX();
            minY = maxY = areaPoints[0].getY();
            minZ = maxZ = areaPoints[0].getZ();
        }

        // Size validation is now done at compile-time - see ProgWidgetAreaItemBase#addErrors
        // https://github.com/TeamPneumatic/pnc-repressurized/issues/95
        // https://github.com/TeamPneumatic/pnc-repressurized/issues/104
        int size = (maxX - minX) * (maxY - minY) * (maxZ - minZ);
        final int maxSize = PNCConfig.Common.General.maxProgrammingArea;
        if (size > maxSize) { // Prevent memory problems when getting to ridiculous areas.
            if (aiManager != null) {
                // We still need to do run-time checks:
                // 1) Drones programmed before the compile-time validation was added
                // 2) Programs using variables where we don't necessarily have the values at compile-time
                IDroneBase drone = aiManager.getDrone();
                Log.warning(String.format("Drone @ %s (DIM %s) was killed due to excessively large area (%d > %d). See 'maxProgrammingArea' in config.",
                        drone.getDronePos().toString(), drone.world().getDimensionKey().getLocation().toString(), size, maxSize));
                drone.overload("areaTooLarge", maxSize);
                return;
            }
            // We're in the Programmer (no AI manager).  Continue to update the area,
            // but don't let it grow without bounds.
        }

        Consumer<BlockPos> addFunc = p -> {
            if (p.getY() >= 0 && p.getY() < 256 && area.add(p) && area.size() > maxSize) {
                throw new AreaTooBigException();
            }
        };
        BlockPos p1 = areaPoints[0];
        BlockPos p2 = areaPoints[1] != null ? areaPoints[1] : p1;

        try {
            areaType.addArea(addFunc, p1, p2, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (AreaTooBigException ignored) {
        }
    }

    private AxisAlignedBB getAABB() {
        BlockPos[] areaPoints = getAreaPoints();
        if (areaPoints[0] == null) return null;
        int minX;
        int minY;
        int minZ;
        int maxX;
        int maxY;
        int maxZ;
        if (areaPoints[1] != null) {
            minX = Math.min(areaPoints[0].getX(), areaPoints[1].getX());
            minY = Math.min(areaPoints[0].getY(), areaPoints[1].getY());
            minZ = Math.min(areaPoints[0].getZ(), areaPoints[1].getZ());
            maxX = Math.max(areaPoints[0].getX(), areaPoints[1].getX());
            maxY = Math.max(areaPoints[0].getY(), areaPoints[1].getY());
            maxZ = Math.max(areaPoints[0].getZ(), areaPoints[1].getZ());
        } else {
            minX = maxX = areaPoints[0].getX();
            minY = maxY = areaPoints[0].getY();
            minZ = maxZ = areaPoints[0].getZ();
        }
        return new AxisAlignedBB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    List<Entity> getEntitiesWithinArea(World world, Predicate<? super Entity> predicate) {
        AxisAlignedBB aabb = getAABB();
        return aabb != null ? world.getEntitiesInAABBexcluding(null, aabb, predicate) : new ArrayList<>();
    }

    @Override
    public void writeToPacket(PacketBuffer buf) {
        super.writeToPacket(buf);
        BlockPos pos1 = getPos(0).orElse(BlockPos.ZERO);
        BlockPos pos2 = getPos(1).orElse(BlockPos.ZERO);
        buf.writeBlockPos(pos1);
        // looks weird but this ensures the vast majority of offsets can be encoded into one byte
        // (keep numbers positive for best varint results)
        BlockPos offset = pos1.subtract(pos2).add(127, 127, 127);
        buf.writeBlockPos(offset);
//        buf.writeVarInt(x1 - x2 + 127);
//        buf.writeVarInt(y1 - y2 + 127);
//        buf.writeVarInt(z1 - z2 + 127);
        buf.writeVarInt(areaTypeToID.get(type.getName()));
        type.writeToPacket(buf);
        buf.writeString(varNames[0]);
        buf.writeString(varNames[1]);
    }

    @Override
    public void readFromPacket(PacketBuffer buf) {
        super.readFromPacket(buf);
        pos[0] = buf.readBlockPos();
        BlockPos offset = buf.readBlockPos().add(-127, -127, -127);
        pos[1] = pos[0].subtract(offset);
//        x2 = x1 - (buf.readVarInt() - 127);
//        y2 = y1 - (buf.readVarInt() - 127);
//        z2 = z1 - (buf.readVarInt() - 127);
        type = createType(buf.readVarInt());
        type.readFromPacket(buf);
        varNames[0] = buf.readString(GlobalVariableManager.MAX_VARIABLE_LEN);
        varNames[1] = buf.readString(GlobalVariableManager.MAX_VARIABLE_LEN);
    }

    @Override
    public void writeToNBT(CompoundNBT tag) {
        super.writeToNBT(tag);
        getPos(0).ifPresent(pos -> tag.put("pos1", NBTUtil.writeBlockPos(pos)));
        getPos(1).ifPresent(pos -> tag.put("pos2", NBTUtil.writeBlockPos(pos)));
        tag.putString("type", type.getName());
        type.writeToNBT(tag);
        if (!varNames[0].isEmpty()) {
            tag.putString("var1", varNames[0]);
        } else {
            tag.remove("var1");
        }
        if (!varNames[1].isEmpty()) {
            tag.putString("var2", varNames[1]);
        } else {
            tag.remove("var2");
        }
    }

    @Override
    public void readFromNBT(CompoundNBT tag) {
        super.readFromNBT(tag);
        if (tag.contains("x1")) {
            // TODO remove in 1.17 - legacy import code
            pos[0] = new BlockPos(tag.getInt("x1"), tag.getInt("y1"), tag.getInt("z1"));
            pos[1] = new BlockPos(tag.getInt("x2"), tag.getInt("y2"), tag.getInt("z2"));
            if (pos[1].equals(BlockPos.ZERO)) {
                pos[1] = pos[0];
            }
            varNames[0] = tag.getString("coord1Variable");
            varNames[1] = tag.getString("coord2Variable");
            tag.remove("x1");
            tag.remove("x2");
            tag.remove("y1");
            tag.remove("y2");
            tag.remove("z1");
            tag.remove("z1");
            tag.remove("coord1Variable");
            tag.remove("coord2Variable");
        } else {
            pos[0] = NBTUtil.readBlockPos(tag.getCompound("pos1"));
            pos[1] = NBTUtil.readBlockPos(tag.getCompound("pos2"));
            if (pos[1].equals(BlockPos.ZERO)) {
                // TODO remove in 1.17 - (0,0,0) will no longer mean "invalid"
                pos[1] = pos[0];
            }
            varNames[0] = tag.getString("var1");
            varNames[1] = tag.getString("var2");
        }
        type = createType(tag.getString("type"));
        type.readFromNBT(tag);
    }

    public static AreaType createType(String id) {
        if (!areaTypeToID.containsKey(id)) {
            Log.error("No Area type found for id '" + id + "'! Substituting Box!");
            return new AreaTypeBox();
        }
        return createType(areaTypeToID.get(id));
    }

    public static AreaType createType(int id) {
        return areaTypeFactories.get(id).get();
    }

    @Override
    public WidgetDifficulty getDifficulty() {
        return WidgetDifficulty.EASY;
    }

    @Override
    public DyeColor getColor() {
        return DyeColor.GREEN;
    }

    public void setPos(int index, BlockPos newPos) {
        pos[index] = newPos;
    }

    public Optional<BlockPos> getPos(int index) {
        return Optional.ofNullable(pos[index]);
    }

    public String getVarName(int index) {
        return varNames[index];
    }

    public void setVarName(int index, String varName) {
        varNames[index] = varName;
    }

    @Override
    public void setAIManager(DroneAIManager aiManager) {
        this.aiManager = aiManager;
        this.variableProvider = aiManager;
    }

    @Override
    public void addVariables(Set<String> variables) {
        variables.add(varNames[0]);
        variables.add(varNames[1]);
    }

    public void setVariableProvider(IVariableProvider provider, UUID playerID) {
        this.variableProvider = provider;
        this.playerID = playerID;
    }
}
