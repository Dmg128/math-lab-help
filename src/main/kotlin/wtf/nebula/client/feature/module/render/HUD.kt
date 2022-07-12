package wtf.nebula.client.feature.module.render

import com.mojang.realmsclient.gui.ChatFormatting
import me.bush.eventbuskotlin.EventListener
import me.bush.eventbuskotlin.listener
import net.minecraft.client.gui.GuiChat
import wtf.nebula.client.Nebula
import wtf.nebula.client.event.render.RenderHUDEvent
import wtf.nebula.client.feature.module.Module
import wtf.nebula.client.feature.module.ModuleCategory
import wtf.nebula.client.registry.impl.ModuleRegistry

class HUD : Module(ModuleCategory.RENDER, "Renders an overlay of the vanilla HUD") {
    val watermark by bool("Watermark", true)
    val arraylist by bool("Arraylist", true)
    val coordinates by bool("Coordinates", true)

    @EventListener
    private val renderHUDEvent = listener<RenderHUDEvent> {
        if (watermark) {
            mc.fontRenderer.drawStringWithShadow(WATERMARK, 2.0f, 2.0f, Colors.color())
        }

        if (arraylist) {
            var y = 2.0
            ModuleRegistry.INSTANCE!!.registers
                .filter { mod -> mod.isActive() && mod.drawn }
                .sortedBy { mod -> -mc.fontRenderer.getStringWidth(mod.getInfo()) }
                .forEach { mod ->
                    val width = mc.fontRenderer.getStringWidth(mod.getInfo())
                    mod.animation.max = width.toFloat()
                    mod.animation.update()

                    val x = (it.res.scaledWidth_double - mod.animation.value - 4.0)
                    val height = mc.fontRenderer.FONT_HEIGHT + 2.0

                    mc.fontRenderer.drawStringWithShadow(
                        mod.getInfo(),
                        (x + 2.3).toFloat(),
                        (y + (height / 2.0) - (mc.fontRenderer.FONT_HEIGHT / 2.0)).toFloat(),
                        Colors.color())

                    y += height
                }
        }

        if (coordinates) {
            var y = it.res.scaledHeight_double - mc.fontRenderer.FONT_HEIGHT - 2
            if (mc.currentScreen is GuiChat) {
                y -= 13
            }

            val text = StringBuilder()
                .append(ChatFormatting.GRAY.toString())
                .append("XYZ: ")
                .append(ChatFormatting.RESET.toString())

            text.append(String.format("%.1f", mc.player.posX))
            text.append(", ")

            text.append(String.format("%.1f", mc.player.posY))
            text.append(", ")

            text.append(String.format("%.1f", mc.player.posZ))

            mc.fontRenderer.drawStringWithShadow(text.toString(), 2.0f, y.toFloat(), -1)
        }
    }

    companion object {
        var WATERMARK = Nebula.FULL
    }
}