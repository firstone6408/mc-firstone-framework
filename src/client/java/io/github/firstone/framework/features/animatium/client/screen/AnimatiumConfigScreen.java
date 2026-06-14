package io.github.firstone.framework.features.animatium.client.screen;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * หน้าจอตั้งค่าสำหรับ Feature Animatium
 *
 * <p>แสดงปุ่มเปิด/ปิดสำหรับแต่ละ Legacy Animation และ Combat Feature:</p>
 * <ul>
 *   <li>No Re-equip Animation</li>
 *   <li>Old Sneak Animation</li>
 *   <li>Old Item Drop Animation</li>
 *   <li>No Sweep Effect (Particle + Sound)</li>
 *   <li>No Damage Indicator Particle</li>
 *   <li>No Attack Sounds</li>
 * </ul>
 *
 * <p>การเปลี่ยนแปลงจะถูกบันทึกลงไฟล์ทันทีเมื่อกดปุ่ม</p>
 */
public class AnimatiumConfigScreen extends Screen {

    private static final int TITLE_Y = 15;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;
    private static final int DONE_BUTTON_WIDTH = 100;

    /** หน้าจอก่อนหน้า สำหรับกลับไปเมื่อปิดหน้าจอนี้ */
    private final Screen parent;

    /**
     * สร้างหน้าจอตั้งค่า Animatium
     *
     * @param parent หน้าจอที่จะกลับไปเมื่อปิดหน้าจอนี้
     */
    public AnimatiumConfigScreen(Screen parent) {
        super(Component.literal("Animatium - Legacy Animation"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AnimatiumConfig config = AnimatiumFeature.getConfig();

        int centerX = (this.width - BUTTON_WIDTH) / 2;
        // จัดให้ 6 ปุ่มอยู่กึ่งกลางหน้าจอ (5 ช่องห่าง)
        int startY = (this.height / 2) - (BUTTON_SPACING * 5 / 2);

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("No Re-equip Animation", config.noReequipAnimation),
            btn -> {
                config.noReequipAnimation = !config.noReequipAnimation;
                btn.setMessage(buildToggleLabel("No Re-equip Animation", config.noReequipAnimation));
                AnimatiumFeature.saveConfig();
            }
        ).pos(centerX, startY).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("Old Sneak Animation", config.oldSneakAnimation),
            btn -> {
                config.oldSneakAnimation = !config.oldSneakAnimation;
                btn.setMessage(buildToggleLabel("Old Sneak Animation", config.oldSneakAnimation));
                AnimatiumFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("Old Item Drop Animation", config.oldItemDropAnimation),
            btn -> {
                config.oldItemDropAnimation = !config.oldItemDropAnimation;
                btn.setMessage(buildToggleLabel("Old Item Drop Animation", config.oldItemDropAnimation));
                AnimatiumFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING * 2).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("No Sweep Effect", config.noSweepEffect),
            btn -> {
                config.noSweepEffect = !config.noSweepEffect;
                btn.setMessage(buildToggleLabel("No Sweep Effect", config.noSweepEffect));
                AnimatiumFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING * 3).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("No Damage Indicator Particle", config.noDamageIndicatorParticle),
            btn -> {
                config.noDamageIndicatorParticle = !config.noDamageIndicatorParticle;
                btn.setMessage(buildToggleLabel("No Damage Indicator Particle", config.noDamageIndicatorParticle));
                AnimatiumFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING * 4).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("No Attack Sounds", config.noAttackSounds),
            btn -> {
                config.noAttackSounds = !config.noAttackSounds;
                btn.setMessage(buildToggleLabel("No Attack Sounds", config.noAttackSounds));
                AnimatiumFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING * 5).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Done"),
            btn -> this.onClose()
        ).pos((this.width - DONE_BUTTON_WIDTH) / 2, this.height - 28).size(DONE_BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * สร้าง Component ของปุ่ม Toggle พร้อมแสดงสถานะ ON/OFF ด้วยสี
     *
     * @param label ชื่อของ Option
     * @param value สถานะปัจจุบัน (true = ON, false = OFF)
     * @return Component สำหรับแสดงบนปุ่ม
     */
    private Component buildToggleLabel(String label, boolean value) {
        Component status = value
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED);
        return Component.literal(label + ": ").append(status);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
