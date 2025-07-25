/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client.clickgui.elements

import net.ccbluex.liquidbounce.LiquidBounce.clickGui
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.ClickGUI.spacedModules
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
class ModuleElement(val module: Module) : ButtonElement(module.name, buttonAction = {
    // This module element handles the click action itself.
}) {
    override val displayName
        get() = module.getName(spacedModules)

    override var hoverText = ""
        get() = module.description

    var showSettings = false
    var disableFiltering = false

    var supposedWidth = 0

    var settingsWidth = 0
        set(value) {
            if (value > settingsWidth || disableFiltering) {
                disableFiltering = false
                field = value
            }

            if (value > supposedWidth) {
                supposedWidth = value
            }
        }

    var settingsHeight = 0

    var slowlyFade = 0
        set(value) {
            field = value.coerceIn(0, 255)
        }

    override fun drawScreenAndClick(mouseX: Int, mouseY: Int, mouseButton: Int?): Boolean {
        this.supposedWidth = 0

        return clickGui.style.drawModuleElementAndClick(mouseX, mouseY, this, mouseButton)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int): Boolean {
        if (!isHovered(mouseX, mouseY)) {
            return false
        }

        when (mouseButton) {
            0 -> {
                module.toggle()
                clickGui.style.clickSound()
            }
            1 -> {
                if (module.values.isNotEmpty()) {
                    showSettings = !showSettings
                    clickGui.style.showSettingsSound()
                }
            }
        }

        return true
    }
    
    fun adjustWidth() {
        if (settingsWidth - supposedWidth > 16) {
            disableFiltering = true
            settingsWidth = supposedWidth
        }
    }

}