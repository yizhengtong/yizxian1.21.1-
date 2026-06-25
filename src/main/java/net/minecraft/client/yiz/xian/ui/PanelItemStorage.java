package net.minecraft.client.yiz.xian.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * 面板物品的纯客户端本地持久化。
 * 保存到 {@code <gameDir>/yizxianmod/panel_inventory.dat}。
 */
public final class PanelItemStorage {

    private static final Logger LOG = LoggerFactory.getLogger("YizXian-PanelStorage");
    private static final String DIR_NAME  = "yizxianmod";
    private static final String FILE_NAME = "panel_inventory.dat";

    private PanelItemStorage() {}

    public static void save(SimpleContainer container) {
        try {
            CompoundTag root = new CompoundTag();
            root.putInt("Size", container.getContainerSize());
            ListTag items = new ListTag();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.putByte("Slot", (byte) i);
                    items.add(stack.save(Minecraft.getInstance().level.registryAccess(), slotTag));
                }
            }
            root.put("Items", items);
            File file = getSaveFile();
            file.getParentFile().mkdirs();
            NbtIo.writeCompressed(root, file.toPath());
        } catch (IOException e) {
            LOG.error("Failed to save panel items", e);
        }
    }

    public static void load(SimpleContainer container) {
        try {
            File file = getSaveFile();
            if (!file.exists()) return;
            CompoundTag root = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            container.clearContent();
            int size = root.getInt("Size");
            ListTag items = root.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag slotTag = items.getCompound(i);
                int slot = slotTag.getByte("Slot") & 0xFF;
                if (slot >= 0 && slot < container.getContainerSize()) {
                    ItemStack.parse(Minecraft.getInstance().level.registryAccess(), slotTag)
                        .ifPresent(stack -> container.setItem(slot, stack));
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to load panel items", e);
        }
    }

    private static File getSaveFile() {
        return new File(Minecraft.getInstance().gameDirectory, DIR_NAME + "/" + FILE_NAME);
    }
}
