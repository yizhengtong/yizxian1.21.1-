package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.yiz.xian.ui.InventoryPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * 向所有含玩家物品栏的 AbstractContainerScreen 注入面板。
 *
 * <p>面板渲染在容器界面底部（topPos + imageHeight），不修改 imageHeight，
 * 不影响各模组自身的 GUI 布局。通过 {@code mouseClicked} 拦截面板区域
 * 点击防止 {@code hasClickedOutside} 误关闭。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class InventoryScreenMixin {

    private static final int PANEL_EXTRA = InventoryPanel.PANEL_EXTRA;

    // 反射 Field 缓存
    private static Field leftPosF, topPosF, imageWidthF, imageHeightF;
    static {
        try {
            leftPosF     = AbstractContainerScreen.class.getDeclaredField("leftPos");
            topPosF      = AbstractContainerScreen.class.getDeclaredField("topPos");
            imageWidthF  = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            imageHeightF = AbstractContainerScreen.class.getDeclaredField("imageHeight");
            for (Field f : new Field[]{leftPosF, topPosF, imageWidthF, imageHeightF}) f.setAccessible(true);
        } catch (NoSuchFieldException e) { /* Mojang mappings required */ }
    }

    private int leftPos()    { try { return leftPosF.getInt(this);     } catch (Exception e) { return 0; } }
    private int topPos()     { try { return topPosF.getInt(this);      } catch (Exception e) { return 0; } }
    private int imageWidth() { try { return imageWidthF.getInt(this);  } catch (Exception e) { return 176; } }
    private int imageHeight(){ try { return imageHeightF.getInt(this); } catch (Exception e) { return 166; } }

    @SuppressWarnings("unchecked")
    private AbstractContainerScreen<?> screen() { return (AbstractContainerScreen<?>) (Object) this; }

    // ══════════════════════════════════════════════════════════
    //  判断：是否含玩家物品栏（快捷栏）
    // ══════════════════════════════════════════════════════════

    private boolean hasPlayerInv() {
        AbstractContainerMenu menu = screen().getMenu();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════
    //  面板几何（相对于 leftPos/topPos）
    // ══════════════════════════════════════════════════════════

    private int panelX() {
        return InventoryPanel.getInstance().panelLeft(leftPos());
    }

    private int panelY() {
        // 面板紧贴容器界面底部
        return topPos() + imageHeight();
    }

    private int panelW() { return InventoryPanel.panelWidth();  }
    private int panelH() { return InventoryPanel.panelHeight(); }

    // ══════════════════════════════════════════════════════════
    //  init() — 加载面板数据
    // ══════════════════════════════════════════════════════════

    @Inject(method = "init", at = @At("TAIL"))
    private void yizxian_init(CallbackInfo ci) {
        InventoryPanel.getInstance().loadItems();
    }

    // ══════════════════════════════════════════════════════════
    //  render() TAIL — 绘制面板全部内容（背景+槽位+物品+悬停）
    // ══════════════════════════════════════════════════════════

    @Inject(method = "render", at = @At("TAIL"))
    private void yizxian_render(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (!hasPlayerInv()) return;
        InventoryPanel panel = InventoryPanel.getInstance();
        panel.renderBackground(g, leftPos(), topPos());
        panel.renderSlots(g, leftPos(), topPos());
        panel.renderItems(g, leftPos(), topPos(), Minecraft.getInstance().font);
        panel.renderHoveredSlot(g, leftPos(), topPos(), mx, my);
    }

    // ══════════════════════════════════════════════════════════
    //  mouseClicked() — 拦截面板区点击，防关闭 + 处理槽位
    // ══════════════════════════════════════════════════════════

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yizxian_mouseClicked(double mx, double my, int btn,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!hasPlayerInv()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 判断是否在面板区域内
        int px = panelX();
        int py = panelY();
        if (mx >= px && mx < px + panelW() && my >= py && my < py + panelH()) {
            // 面板区点击：始终消费事件（防止 hasClickedOutside 关闭界面）
            InventoryPanel.getInstance().handleClick(mx, my, btn,
                screen().getMenu(), mc.player, leftPos(), topPos());
            cir.setReturnValue(true);
        }
    }
}
