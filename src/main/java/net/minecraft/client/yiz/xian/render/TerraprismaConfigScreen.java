package net.minecraft.client.yiz.xian.render;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 泰拉棱镜可视化参数编辑器。
 * 滑块实时调整所有渲染参数，所见即所得，关闭时自动保存。
 */
public final class TerraprismaConfigScreen extends Screen {

    private static final Path CONFIG_PATH = Path.of("config", "yizxianmod", "terraprisma.json");

    private final List<Param> params = new ArrayList<>();
    private int scrollY;

    public TerraprismaConfigScreen() {
        super(Component.literal("泰拉棱镜配置"));
    }

    @Override
    protected void init() {
        params.clear();
        int y = 40;
        int sliderX = 100;
        int sliderW = 180;
        int labelX = 10;

        params.add(new Param("后方基础距", "behindBase",   0.5, 5.0, 0.05, TerraprismaRenderHandler.behindBase,   y)); y += 28;
        params.add(new Param("后方步进距", "behindStep",   0.0, 2.0, 0.05, TerraprismaRenderHandler.behindStep,   y)); y += 28;
        params.add(new Param("侧方基础散", "spreadBase",   0.0, 5.0, 0.05, TerraprismaRenderHandler.spreadBase,   y)); y += 28;
        params.add(new Param("侧方步进散", "spreadStep",   0.0, 3.0, 0.05, TerraprismaRenderHandler.spreadStep,   y)); y += 28;
        params.add(new Param("浮动幅度",   "bobAmplitude", 0.0, 1.0, 0.01, TerraprismaRenderHandler.bobAmplitude, y)); y += 28;
        params.add(new Param("浮动速度",   "bobSpeed",     0.0, 0.2, 0.005,TerraprismaRenderHandler.bobSpeed,     y)); y += 28;
        params.add(new Param("浮动相差",   "bobPhaseStep", 0.0, 3.0, 0.05, TerraprismaRenderHandler.bobPhaseStep, y)); y += 28;
        params.add(new Param("剑长",       "swordLength",  0.3, 3.0, 0.05, TerraprismaRenderHandler.swordLength,  y)); y += 28;
        params.add(new Param("缩放X",      "scaleX",       0.5, 3.0, 0.05, TerraprismaRenderHandler.scaleX,       y)); y += 28;
        params.add(new Param("缩放Y",      "scaleY",       0.5, 3.0, 0.05, TerraprismaRenderHandler.scaleY,       y)); y += 28;
        params.add(new Param("缩放Z",      "scaleZ",       0.5, 3.0, 0.05, TerraprismaRenderHandler.scaleZ,       y)); y += 28;
        params.add(new Param("剑尖抬头",   "tipYOffset",  -4.0, 4.0, 0.05, TerraprismaRenderHandler.tipYOffset,   y)); y += 28;
        params.add(new Param("索敌范围",   "targetRange",  5.0, 196.0,1.0, TerraprismaRenderHandler.targetRange,  y)); y += 28;
        params.add(new Param("参考Y比例",  "eyeYRatio",    0.0, 1.5, 0.05, TerraprismaRenderHandler.eyeYRatio,    y)); y += 28;
        params.add(new Param("锚点偏移X",  "anchorX",     -2.0, 2.0, 0.05, TerraprismaRenderHandler.anchorX,      y)); y += 28;
        params.add(new Param("锚点偏移Y",  "anchorY",     -2.0, 2.0, 0.05, TerraprismaRenderHandler.anchorY,      y)); y += 28;
        params.add(new Param("锚点偏移Z",  "anchorZ",     -2.0, 2.0, 0.05, TerraprismaRenderHandler.anchorZ,      y)); y += 28;
        params.add(new Param("倾斜角度°",  "tiltAngle", -180.0,180.0,1.0, TerraprismaRenderHandler.tiltAngle,     y));

        for (Param p : params) {
            addRenderableWidget(p.slider);
            p.slider.visible = false; // 手动绘制，不用原版布局
        }

        // 按钮行
        int btnW = 60;
        addRenderableWidget(Button.builder(Component.literal("重置"), b -> {
            resetDefaults(); saveConfig(); init(); })
            .pos(15, 8).size(btnW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> saveConfig())
            .pos(this.width - btnW - 15, 8).size(btnW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
            .pos(this.width - btnW * 2 - 25, 8).size(btnW, 20).build());
    }

    private void resetDefaults() {
        TerraprismaRenderHandler.behindBase   = 0.5;
        TerraprismaRenderHandler.behindStep   = 0.05;
        TerraprismaRenderHandler.spreadBase   = 0.35;
        TerraprismaRenderHandler.spreadStep   = 0.0;
        TerraprismaRenderHandler.bobAmplitude = 0.0;
        TerraprismaRenderHandler.bobSpeed     = 0.0;
        TerraprismaRenderHandler.bobPhaseStep = 0.05;
        TerraprismaRenderHandler.swordLength  = 1.0;
        TerraprismaRenderHandler.scaleX       = 1.15;
        TerraprismaRenderHandler.scaleY       = 1.15;
        TerraprismaRenderHandler.scaleZ       = 1.15;
        TerraprismaRenderHandler.tipYOffset   = -3.05;
        TerraprismaRenderHandler.tipExtendX   = 0.0;
        TerraprismaRenderHandler.useEyeHeight = true;
        TerraprismaRenderHandler.eyeYRatio    = 0.7;
        TerraprismaRenderHandler.bodyYOffset  = 0.3;
        TerraprismaRenderHandler.anchorX      = -0.1;
        TerraprismaRenderHandler.anchorY      = -0.1;
        TerraprismaRenderHandler.anchorZ      = 0.2;
        TerraprismaRenderHandler.tiltAngle    = -90.0;
        TerraprismaRenderHandler.targetRange  = 37.0;
        TerraprismaRenderHandler.glowLight    = 15728880;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial); // 背景

        g.drawCenteredString(font, Component.literal("泰拉棱镜实时调参 — 滑块拖动即生效"), width / 2, 10, 0xFFFFFF);

        // 裁剪滚动区
        g.enableScissor(0, 35, width, height - 5);

        int yBase = 38 - scrollY;
        for (Param p : params) {
            int rowY = p.baseY - scrollY;
            if (rowY < 30 || rowY > height) continue;

            // 标签
            g.drawString(font, p.label, 12, rowY + 5, 0xCCCCCC);
            // 数值
            g.drawString(font, String.format("%.2f", p.getValue()), 82, rowY + 5, 0xFFFF55);
            // 滑块
            drawSlider(g, p, 160, rowY + 3, width - 220, 16);
        }

        g.disableScissor();

        // 提示
        g.drawCenteredString(font, "按住拖动滑块 · 点保存写入文件 · 关闭返回游戏",
            width / 2, height - 12, 0x666666);
    }

    private void drawSlider(GuiGraphics g, Param p, int x, int y, int w, int h) {
        // 背景轨道
        g.fill(x, y + h / 2 - 2, x + w, y + h / 2 + 2, 0xFF444444);
        // 填充
        int fillW = (int) (w * p.getRatio());
        g.fill(x, y + h / 2 - 2, x + fillW, y + h / 2 + 2, 0xFF8888FF);
        // 滑块头
        int headX = x + fillW - 3;
        g.fill(headX, y, headX + 6, y + h, 0xFFFFFFFF);

        // 鼠标交互
        if (mouseDown && mx >= x - 4 && mx <= x + w + 4 && my >= y - 2 && my <= y + h + 2) {
            double ratio = (double) (mx - x) / w;
            ratio = Math.max(0, Math.min(1, ratio));
            p.setRatio(ratio);
        }
    }

    private boolean mouseDown;
    private int mx, my;

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) { mouseDown = true; this.mx = (int) mx; this.my = (int) my; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        mouseDown = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        this.mx = (int) mx; this.my = (int) my;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scrollY = Math.max(0, Math.min(scrollY - (int) dy * 20, params.size() * 28 - height + 60));
        return true;
    }

    @Override
    public void onClose() {
        saveConfig();
        super.onClose();
    }

    private void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("_说明", "由GUI自动保存");

            JsonObject pos = new JsonObject();
            pos.addProperty("behindBase", TerraprismaRenderHandler.behindBase);
            pos.addProperty("behindStep", TerraprismaRenderHandler.behindStep);
            pos.addProperty("spreadBase", TerraprismaRenderHandler.spreadBase);
            pos.addProperty("spreadStep", TerraprismaRenderHandler.spreadStep);
            pos.addProperty("alternateLeftRight", true);
            root.add("八字排列", pos);

            JsonObject bob = new JsonObject();
            bob.addProperty("amplitude", TerraprismaRenderHandler.bobAmplitude);
            bob.addProperty("speed", TerraprismaRenderHandler.bobSpeed);
            bob.addProperty("phaseStep", TerraprismaRenderHandler.bobPhaseStep);
            root.add("浮动动画", bob);

            JsonObject sz = new JsonObject();
            sz.addProperty("length", TerraprismaRenderHandler.swordLength);
            sz.addProperty("scaleX", TerraprismaRenderHandler.scaleX);
            sz.addProperty("scaleY", TerraprismaRenderHandler.scaleY);
            sz.addProperty("scaleZ", TerraprismaRenderHandler.scaleZ);
            root.add("剑身尺寸", sz);

            JsonObject tip = new JsonObject();
            tip.addProperty("yOffset", TerraprismaRenderHandler.tipYOffset);
            tip.addProperty("extendX", TerraprismaRenderHandler.tipExtendX);
            root.add("剑尖默认朝向", tip);

            JsonObject ref = new JsonObject();
            ref.addProperty("useEyeHeight", TerraprismaRenderHandler.useEyeHeight);
            ref.addProperty("eyeYRatio", TerraprismaRenderHandler.eyeYRatio);
            ref.addProperty("bodyYOffset", TerraprismaRenderHandler.bodyYOffset);
            root.add("玩家参考点", ref);

            JsonObject anc = new JsonObject();
            anc.addProperty("x", TerraprismaRenderHandler.anchorX);
            anc.addProperty("y", TerraprismaRenderHandler.anchorY);
            anc.addProperty("z", TerraprismaRenderHandler.anchorZ);
            root.add("锚点偏移", anc);

            JsonObject tilt = new JsonObject();
            tilt.addProperty("angle", TerraprismaRenderHandler.tiltAngle);
            root.add("倾斜与朝向", tilt);

            JsonObject ai = new JsonObject();
            ai.addProperty("range", TerraprismaRenderHandler.targetRange);
            root.add("索敌", ai);

            JsonObject gl = new JsonObject();
            gl.addProperty("brightness", TerraprismaRenderHandler.glowLight);
            root.add("发光", gl);

            Path path = Path.of("").toAbsolutePath().resolve(CONFIG_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));

        } catch (Exception ignored) {}
    }

    /** 一个可调参数 */
    private class Param {
        final String label, key;
        final double min, max, step;
        final Button slider;
        final int baseY;
        double value;

        Param(String label, String key, double min, double max, double step, double value, int baseY) {
            this.label = label;
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.value = value;
            this.baseY = baseY;
            this.slider = Button.builder(Component.literal(""), b -> {}).pos(0, 0).size(1, 1).build();
        }

        double getValue() { return value; }
        double getRatio() { return (value - min) / (max - min); }

        void setRatio(double r) {
            value = min + r * (max - min);
            value = Math.round(value / step) * step;
            value = Math.max(min, Math.min(max, value));
            apply();
        }

        void apply() {
            // 直接写 TerraprismaRenderHandler 的静态字段
            switch (key) {
                case "behindBase"   -> TerraprismaRenderHandler.behindBase   = value;
                case "behindStep"   -> TerraprismaRenderHandler.behindStep   = value;
                case "spreadBase"   -> TerraprismaRenderHandler.spreadBase   = value;
                case "spreadStep"   -> TerraprismaRenderHandler.spreadStep   = value;
                case "bobAmplitude" -> TerraprismaRenderHandler.bobAmplitude = value;
                case "bobSpeed"     -> TerraprismaRenderHandler.bobSpeed     = value;
                case "bobPhaseStep" -> TerraprismaRenderHandler.bobPhaseStep = value;
                case "swordLength"  -> TerraprismaRenderHandler.swordLength  = value;
                case "scaleX"       -> TerraprismaRenderHandler.scaleX       = value;
                case "scaleY"       -> TerraprismaRenderHandler.scaleY       = value;
                case "scaleZ"       -> TerraprismaRenderHandler.scaleZ       = value;
                case "tipYOffset"   -> TerraprismaRenderHandler.tipYOffset   = value;
                case "targetRange"  -> TerraprismaRenderHandler.targetRange  = value;
                case "eyeYRatio"    -> TerraprismaRenderHandler.eyeYRatio    = value;
                case "anchorX"      -> TerraprismaRenderHandler.anchorX      = value;
                case "anchorY"      -> TerraprismaRenderHandler.anchorY      = value;
                case "anchorZ"      -> TerraprismaRenderHandler.anchorZ      = value;
                case "tiltAngle"    -> TerraprismaRenderHandler.tiltAngle    = value;
            }
        }
    }
}