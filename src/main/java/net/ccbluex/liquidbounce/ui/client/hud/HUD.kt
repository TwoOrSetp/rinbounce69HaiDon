/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.hud

import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.*
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Target
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.opengl.GL11.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min

object HUD : MinecraftInstance {

    val elements = mutableListOf<Element>()
    val notifications = CopyOnWriteArrayList<Notification>()

    private val ALL_ELEMENT_CLASSES = arrayOf(
        Armor::class.java,
        Arraylist::class.java,
        Effects::class.java,
        Image::class.java,
        Inventory::class.java,
        Model::class.java,
        Notifications::class.java,
        TabGUI::class.java,
        Text::class.java,
        ScoreboardElement::class.java,
        Target::class.java,
        Radar::class.java,
        SpeedGraph::class.java,
        Cooldown::class.java,
        Taco::class.java,
        Keystrokes::class.java,
        DamageCheck::class.java
    )

    val ELEMENTS = ALL_ELEMENT_CLASSES.associateWithTo(IdentityHashMap(ALL_ELEMENT_CLASSES.size)) {
        it.getAnnotation(ElementInfo::class.java)
    }

    /** Create default HUD */
    fun setDefault() {
        elements.clear()
        addElement(Text.defaultBlockCount())
        addElement(Arraylist())
        addElement(ScoreboardElement())
        addElement(Armor())
        addElement(Effects())
        addElement(Notifications())
        addElement(Target())
    }

    /** Render all elements */
    fun render(designer: Boolean) {
        elements.forEach {
            glPushMatrix()

            if (!it.info.disableScale) glScalef(it.scale, it.scale, it.scale)

            glTranslated(it.renderX, it.renderY, 0.0)

            try {
                it.border = it.drawElement()

                if (designer) it.border?.draw()
            } catch (ex: Exception) {
                LOGGER.error("Something went wrong while drawing ${it.name} element in HUD.", ex)
            }

            glPopMatrix()
        }
    }

    /** Update all elements */
    fun update() {
        for (element in elements) element.updateElement()
    }

    /** Handle mouse click */
    fun handleMouseClick(mouseX: Int, mouseY: Int, button: Int) {
        for (element in elements) element.handleMouseClick(
            (mouseX / element.scale) - element.renderX,
            (mouseY / element.scale) - element.renderY,
            button
        )

        if (button == 0) {
            for (element in elements.reversed()) {
                if (!element.isInBorder(
                        (mouseX / element.scale) - element.renderX, (mouseY / element.scale) - element.renderY
                    )
                )
                    continue

                element.drag = true
                elements -= element
                elements += element
                break
            }
        }
    }

    /** Handle released mouse key */
    fun handleMouseReleased() {
        for (element in elements) element.drag = false
    }

    /** Handle mouse move */
    fun handleMouseMove(mouseX: Int, mouseY: Int) {
        if (mc.currentScreen !is GuiHudDesigner) return

        val (scaledWidth, scaledHeight) = ScaledResolution(mc)

        for (element in elements) {
            val scaledX = mouseX / element.scale
            val scaledY = mouseY / element.scale
            val prevMouseX = element.prevMouseX
            val prevMouseY = element.prevMouseY

            element.prevMouseX = scaledX
            element.prevMouseY = scaledY

            if (element.drag) {
                val moveX = scaledX - prevMouseX
                val moveY = scaledY - prevMouseY

                if (moveX == 0F && moveY == 0F) continue

                val border = element.border ?: continue

                val minX = min(border.x, border.x2) + 1
                val minY = min(border.y, border.y2) + 1

                val maxX = max(border.x, border.x2) - 1
                val maxY = max(border.y, border.y2) - 1

                val width = scaledWidth / element.scale
                val height = scaledHeight / element.scale

                if ((element.renderX + minX + moveX >= 0.0 || moveX > 0) &&
                    (element.renderX + maxX + moveX <= width || moveX < 0)
                )
                    element.renderX = moveX.toDouble()
                if ((element.renderY + minY + moveY >= 0.0 || moveY > 0) &&
                    (element.renderY + maxY + moveY <= height || moveY < 0)
                )
                    element.renderY = moveY.toDouble()
            }
        }
    }

    /** Handle incoming key */
    fun handleKey(c: Char, keyCode: Int) {
        for (element in elements) element.handleKey(c, keyCode)
    }

    /** Add [element] to HUD */
    fun addElement(element: Element): HUD {
        elements += element

        elements.sortBy { -it.info.priority }

        element.updateElement()
        return this
    }

    /** Remove [element] from HUD */
    fun removeElement(hudDesigner: GuiHudDesigner, element: Element): HUD {
        if (hudDesigner.elementEditableText?.element == element) {
            hudDesigner.elementEditableText = null
        }

        element.destroyElement()
        elements.remove(element)
        return this
    }

    /** Clear all elements */
    fun clearElements() {
        for (element in elements) element.destroyElement()

        elements.clear()
    }

    /** Add [notification] */
    fun addNotification(notification: Notification) =
        elements.any { it is Notifications }.also { mc.addScheduledTask { notifications.add(notification) } }

    /** Remove [notification] */
    fun removeNotification(notification: Notification) = notifications.remove(notification)
}