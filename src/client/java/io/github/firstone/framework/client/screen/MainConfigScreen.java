package io.github.firstone.framework.client.screen;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * หน้าจอหลักสำหรับการตั้งค่า FirstOne Framework
 *
 * <p>แสดงรายการ Feature ทั้งหมดที่ถูกลงทะเบียนใน {@link FeatureRegistry}
 * ผู้เล่นสามารถเลือก Feature เพื่อเปิดหน้าจอตั้งค่าของ Feature นั้นๆ ได้</p>
 *
 * <p>วิธีเปิดหน้าจอนี้:</p>
 * <pre>{@code
 * Minecraft.getInstance().setScreen(new MainConfigScreen(currentScreen));
 * }</pre>
 */
public class MainConfigScreen extends Screen {

    private static final int TITLE_Y = 15;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;
    private static final int CLOSE_BUTTON_WIDTH = 100;

    /** หน้าจอก่อนหน้าที่จะกลับไปเมื่อปิดหน้าจอนี้ */
    private final Screen parent;

    /** รายการ Feature ทั้งหมดที่ลงทะเบียนแล้ว */
    private final List<Feature> features;

    /**
     * สร้างหน้าจอตั้งค่าหลักของ Framework
     *
     * @param parent หน้าจอที่จะกลับไปเมื่อปิดหน้าจอนี้ อาจเป็น {@code null} ได้
     */
    public MainConfigScreen(Screen parent) {
        super(Component.literal("FirstOne Framework"));
        this.parent = parent;
        this.features = FeatureRegistry.getAll();
    }

    @Override
    protected void init() {
        int startY = (this.height / 2) - (features.size() * BUTTON_SPACING / 2) - BUTTON_SPACING;

        for (int i = 0; i < features.size(); i++) {
            Feature feature = features.get(i);
            int buttonY = startY + (i * BUTTON_SPACING);

            this.addRenderableWidget(Button.builder(
                Component.literal(feature.getId()),
                btn -> onFeatureButtonClick(feature)
            ).pos((this.width - BUTTON_WIDTH) / 2, buttonY).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }

        this.addRenderableWidget(Button.builder(
            Component.literal("Done"),
            btn -> this.onClose()
        ).pos((this.width - CLOSE_BUTTON_WIDTH) / 2, this.height - 30).size(CLOSE_BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * จัดการเหตุการณ์เมื่อผู้เล่นคลิกปุ่มของ Feature
     *
     * <p>Feature สามารถ override ได้โดยผ่าน FeatureConfigScreen ของตัวเอง
     * โดยเปลี่ยนมาเรียก {@code minecraft.setScreen(new FeatureConfigScreen(this))} ที่นี่</p>
     *
     * @param feature Feature ที่ถูกเลือก
     */
    protected void onFeatureButtonClick(Feature feature) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);

        if (features.isEmpty()) {
            graphics.drawCenteredString(
                this.font,
                Component.literal("No features registered"),
                this.width / 2,
                this.height / 2,
                0xAAAAAA
            );
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
