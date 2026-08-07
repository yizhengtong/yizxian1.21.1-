package net.minecraft.client.yiz.xian.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.xian.menu.EntityAttributeEditMenu;
import net.minecraft.client.yiz.xian.network.C2SEntityAttributeEditPayload;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * 实体属性编辑 Screen —— 原版容器风格（复用箱子 generic_54 背景，不画自定义贴图）。
 *
 * <p>布局（176×222，generic_54）：上半部分容器区绘制 12 个 yizmodqzk 自定义属性列表，
 * 点击行选中（黄色高亮），鼠标滚轮增减数值（步进按属性类型），"应用"按钮把改动经
 * C2S 包提交服务端（本模组实体受保护写入 / 其他实体普通写入）。下半部分玩家背包槽照常。</p>
 */
public class EntityAttributeEditScreen extends AbstractContainerScreen<EntityAttributeEditMenu> {

    /** 自定义编辑界面背景（用户改制的 generic_54 变体）。 */
    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath("yizxianmod", "textures/gui/editor/editor_bg.png");

    /** 可编辑属性定义。 */
    private record Entry(String id, String name, Holder<Attribute> attr, double step) {}

    private static final List<Entry> ENTRIES = List.of(
        new Entry("attack_strength",    "攻击强度",   YizAttributes.ATTACK_STRENGTH,    5),
        new Entry("spell_power",        "法术强度",   YizAttributes.SPELL_POWER,        5),
        new Entry("generic_damage",     "全伤害",     YizAttributes.GENERIC_DAMAGE,     1),
        new Entry("melee_damage",       "近战伤害",   YizAttributes.MELEE_DAMAGE,       5),
        new Entry("ranged_damage",      "远程伤害",   YizAttributes.RANGED_DAMAGE,      5),
        new Entry("damage_reduction",   "伤害减免",   YizAttributes.DAMAGE_REDUCTION,   5),
        new Entry("damage_block",       "伤害格挡",   YizAttributes.DAMAGE_BLOCK,       1),
        new Entry("invincibility_mult", "无敌帧",     YizAttributes.INVINCIBILITY_MULT, 5),
        new Entry("dodge_chance",       "闪避",       YizAttributes.DODGE_CHANCE,       5),
        new Entry("life_steal",         "全能吸血",   YizAttributes.LIFE_STEAL,         5),
        new Entry("armor",              "攻击强度防御", YizAttributes.ARMOR,           5),
        new Entry("spell_defense",      "法术防御",   YizAttributes.SPELL_DEFENSE,      5),
        new Entry("vitality_severance_rate",     "绝妄生机率",   YizAttributes.VITALITY_SEVERANCE_RATE,     5),
        new Entry("vitality_severance_time",     "绝妄生机时间", YizAttributes.VITALITY_SEVERANCE_TIME,     1),
        new Entry("first_dream",        "最初梦幻",   YizAttributes.FIRST_DREAM,        5),
        // ── 传导限伤（2026-08-07 新增，目标侧单发限伤；受击 CD = 无敌帧属性，编辑工具已有「无敌帧」条目）──
        new Entry("conduction_cap",      "最大生命值限伤%", YizAttributes.CONDUCTION_CAP,  5)
    );

    // ── 布局（GUI 内相对坐标）──
    // 16 个属性两列显示（左列 8 + 右列 8），按钮放顶部避免被属性行点击拦截
    private static final int LIST_X = 12;
    private static final int LIST_X2 = 92;      // 右列起点
    private static final int LIST_Y0 = 42;
    private static final int LIST_ROW_H = 9;
    private static final int LIST_COLS = 8;     // 每列 8 个（16 个属性 = 8+8 两列）
    private static final int APPLY_X = 64;
    private static final int APPLY_Y = 18;

    private int selected = 0;
    private final double[] edited = new double[ENTRIES.size()];
    private final boolean[] dirty = new boolean[ENTRIES.size()];
    /** 属性值是否已从实体加载（init 时才加载；resize 不重载，避免覆盖本地编辑）。 */
    private boolean valuesLoaded = false;
    /** 手动输入框（双击属性行数值打开；回车应用）。 */
    private EditBox valueInput;

    public EntityAttributeEditScreen(EntityAttributeEditMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.titleLabelX = 9999;
        this.inventoryLabelX = 9999;
        // 注意：minecraft 字段在 init() 才赋值，属性加载必须放 init()，不能在构造器里做
    }

    /** 客户端从目标实体读取当前属性值（yizmodqzk 属性 setSyncable，客户端可读）。 */
    private void loadCurrentValues() {
        LivingEntity e = targetEntity();
        for (int i = 0; i < ENTRIES.size(); i++) {
            double v = 0;
            if (e != null) {
                var inst = e.getAttribute(ENTRIES.get(i).attr());
                if (inst != null) v = inst.getValue();
            }
            edited[i] = v;
        }
    }

    private LivingEntity targetEntity() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getEntity(menu.getTargetEntityId()) instanceof LivingEntity le ? le : null;
    }

    @Override
    protected void init() {
        super.init();
        if (!valuesLoaded) {
            loadCurrentValues();
            valuesLoaded = true;
        }
        this.addRenderableWidget(Button.builder(Component.literal("应用"), b -> apply())
            .bounds(this.leftPos + APPLY_X, this.topPos + APPLY_Y, 48, 16).build());
        // 手动数值输入框（默认隐藏；双击属性行数值时显示）
        this.valueInput = new EditBox(this.font, this.leftPos + 12, this.topPos + LIST_Y0 + 26, 120, 14, Component.literal("输入数值"));
        this.valueInput.setMaxLength(16);
        this.valueInput.setVisible(false);
        this.valueInput.setCanLoseFocus(false);
        this.addRenderableWidget(this.valueInput);
    }

    // ── 交互 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 输入框激活时：优先交给输入框处理（点输入框外则应用并关闭）
        if (this.valueInput != null && this.valueInput.isVisible()) {
            if (this.valueInput.isMouseOver(mouseX, mouseY)) {
                return this.valueInput.mouseClicked(mouseX, mouseY, button);
            }
            commitValueInput(); // 点输入框外：应用当前输入
            return true;
        }
        for (int i = 0; i < ENTRIES.size(); i++) {
            int col = i / LIST_COLS;
            int row = i % LIST_COLS;
            int x = this.leftPos + (col == 0 ? LIST_X : LIST_X2);
            int y = this.topPos + LIST_Y0 + row * LIST_ROW_H;
            if (mouseX >= x && mouseX < x + 76 && mouseY >= y && mouseY < y + LIST_ROW_H) {
                if (this.selected == i && button == 0 && mouseX >= x + 40) {
                    // 双击/再点已选行的数值区（右侧）→ 打开手动输入
                    openValueInput();
                    return true;
                }
                this.selected = i;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 打开手动输入框，预填当前选中行数值。 */
    private void openValueInput() {
        if (this.valueInput == null) return;
        this.valueInput.setValue(fmt(edited[selected]));
        this.valueInput.setVisible(true);
        this.valueInput.setFocused(true);
        this.valueInput.setCanLoseFocus(false);
    }

    /** 应用输入框数值到选中行并关闭输入框。 */
    private void commitValueInput() {
        if (this.valueInput == null || !this.valueInput.isVisible()) return;
        String raw = this.valueInput.getValue().trim();
        this.valueInput.setVisible(false);
        this.valueInput.setFocused(false);
        if (raw.isEmpty()) return;
        try {
            double v = Double.parseDouble(raw);
            Entry e = ENTRIES.get(selected);
            if (e.attr().value() instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged) {
                v = Math.max(ranged.getMinValue(), Math.min(ranged.getMaxValue(), v));
            }
            edited[selected] = Math.max(0, v);
            dirty[selected] = true;
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.valueInput != null && this.valueInput.isVisible()) {
            if (keyCode == 257 || keyCode == 335) { // 回车
                commitValueInput();
                return true;
            }
            return this.valueInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.valueInput != null && this.valueInput.isVisible()) {
            // 只允许数字、小数点、负号
            if ((codePoint >= '0' && codePoint <= '9') || codePoint == '.' || codePoint == '-') {
                return this.valueInput.charTyped(codePoint, modifiers);
            }
            return false;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Entry e = ENTRIES.get(selected);
        double next = edited[selected] + (scrollY > 0 ? e.step() : -e.step());
        if (e.attr().value() instanceof net.minecraft.world.entity.ai.attributes.RangedAttribute ranged) {
            next = Math.max(ranged.getMinValue(), Math.min(ranged.getMaxValue(), next));
        }
        edited[selected] = Math.max(0, next);
        dirty[selected] = true;
        return true;
    }

    /** 应用：把所有改动过的属性提交服务端后关闭界面。 */
    private void apply() {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (dirty[i]) {
                C2SEntityAttributeEditPayload.send(menu.getTargetEntityId(), ENTRIES.get(i).id(), edited[i]);
            }
        }
        onClose();
    }

    // ── 渲染 ──

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        gui.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);

        gui.drawString(font, "实体属性编辑（点选+滚轮增减，点数值手动输入）",
            leftPos + 8, topPos + 8, 0xFF555555, false);

        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            int col = i / LIST_COLS;
            int row = i % LIST_COLS;
            int x = leftPos + (col == 0 ? LIST_X : LIST_X2);
            int y = topPos + LIST_Y0 + row * LIST_ROW_H;
            int color = (i == selected) ? 0xFFFFFF00 : 0xFF404040;
            String text = e.name() + ": " + fmt(edited[i]);
            gui.drawString(font, text, x, y, color, false);
        }

        // 手动输入框激活时：移到选中行下方显示
        if (this.valueInput != null && this.valueInput.isVisible()) {
            int col = selected / LIST_COLS;
            int row = selected % LIST_COLS;
            int x = leftPos + (col == 0 ? LIST_X : LIST_X2);
            int y = topPos + LIST_Y0 + (row + 1) * LIST_ROW_H;
            this.valueInput.setPosition(x, y);
            this.valueInput.render(gui, mouseX, mouseY, partialTick);
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
