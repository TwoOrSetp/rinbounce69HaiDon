/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import java.awt.Color
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.sqrt

/**
 * DamageCheck HUD Element
 */
@ElementInfo(name = "DamageCheck")
class DamageCheck : Element("DamageCheck") {

    private val sCT by boolean("ShowCurrentTarget", true)
    private val sTH by boolean("ShowTargetHealth", true)
    private val sPH by boolean("ShowPlayerHealth", true)
    private val sdamageInfo by boolean("ShowDamageInfo", true)
    private val shitcount by boolean("ShowHitCount", false)
    

    private val DM by choices("DisplayMode", arrayOf("Compact", "Detailed", "Minimal"), "Compact")
    private val CM by choices("ColorMode", arrayOf("Static", "Dynamic", "Rainbow"), "Dynamic")
    private val BGC by color("BackgroundColor", Color(0, 0, 0, 100))
    private val TC by color("TextColor", Color.WHITE)
    
    private val sBG by boolean("ShowBackground", true)

    private var LastPH = 20.0f
    private var hitCount = 0
    private var SST = System.currentTimeMillis()

    private val DF = DecimalFormat("#.#")

    override fun drawElement(): Border {
        val player = mc.thePlayer ?: return Border(0F, 0F, 0F, 0F)

        val target = KillAura.target

        if (!sCT && !sPH && !sdamageInfo) {
            return Border(0F, 0F, 0F, 0F)
        }
        
        return when (DM) {
            "Compact" -> drawCompactMode(player, target)
            "Detailed" -> drawDetailedMode(player, target)
            "Minimal" -> drawMinimalMode(player, target)
            else -> drawCompactMode(player, target)
        }
    }

    private fun drawCompactMode(player: EntityPlayer, target: EntityLivingBase?): Border {
        val lines = mutableListOf<String>()
        
        if (sPH) {
            val healthText = "HP: ${DF.format(player.health)}/${DF.format(player.maxHealth)}"
            lines.add("§c$healthText")
        }
        
        if (sCT && target != null) {
            val targetName = target.name ?: "Unknown"
            val targetHealthText = if (sTH) {
                " (${DF.format(target.health)}HP)"
            } else ""
            lines.add("§aTarget: $targetName$targetHealthText")
        }
        
        if (sdamageInfo && target != null) {
            val estimatedDamage = calculateEstimatedDamage(player, target)
            lines.add("§bDamage: ${DF.format(estimatedDamage)}")
        }
        
        if (shitcount && hitCount > 0) {
            lines.add("§eHits: $hitCount")
        }
        
        if (lines.isEmpty()) {
            lines.add("§7No damage data")
        }
        
        return drawLines(lines)
    }

    private fun drawDetailedMode(player: EntityPlayer, target: EntityLivingBase?): Border {
        val lines = mutableListOf<String>()
        
        lines.add("§l§9Damage Check")
        
        if (sPH) {
            val healthPercentage = (player.health / player.maxHealth * 100).toInt()
            lines.add("§cPlayer: ${DF.format(player.health)}HP (${healthPercentage}%)")
        }
        
        if (sCT && target != null) {
            val targetName = target.name ?: "Unknown"
            lines.add("§aTarget: $targetName")
            
            if (sTH) {
                val targetHealthPercentage = (target.health / target.maxHealth * 100).toInt()
                lines.add("  §7Health: ${DF.format(target.health)}HP (${targetHealthPercentage}%)")
            }
        }
        
        if (sdamageInfo && target != null) {
            val estimatedDamage = calculateEstimatedDamage(player, target)
            val hitsToKill = if (estimatedDamage > 0) (target.health / estimatedDamage).toInt() + 1 else 0
            lines.add("§bEstimated Damage: ${DF.format(estimatedDamage)}")
            lines.add("§6Hits to Kill: $hitsToKill")
        }
        
        if (shitcount) {
            val sessionTime = (System.currentTimeMillis() - SST) / 1000
            lines.add("§eHits: $hitCount (${sessionTime}s)")
        }
        
        return drawLines(lines)
    }

    private fun drawMinimalMode(player: EntityPlayer, target: EntityLivingBase?): Border {
        val text = when {
            sCT && target != null -> {
                val targetName = target.name ?: "Unknown"
                val healthText = if (sTH) " (${target.health.toInt()}HP)" else ""
                "$targetName$healthText"
            }
            sPH -> "HP: ${player.health.toInt()}"
            else -> ""
        }
        
        if (text.isEmpty()) return Border(0F, 0F, 0F, 0F)
        
        val width = Fonts.fontSemibold40.getStringWidth(text).toFloat()
        val height = Fonts.fontSemibold40.FONT_HEIGHT.toFloat()
        
        if (sBG) {
            RenderUtils.drawRect(0F, 0F, width + 4F, height + 4F, BGC.rgb)
        }
        
        Fonts.fontSemibold40.drawString(text, 2F, 2F, getCurrentTC().rgb, true)
        
        return Border(0F, 0F, width + 4F, height + 4F)
    }

    private fun drawLines(lines: List<String>): Border {
        if (lines.isEmpty()) return Border(0F, 0F, 0F, 0F)
        
        val maxWidth = lines.maxOfOrNull { Fonts.fontSemibold40.getStringWidth(it) }?.toFloat() ?: 0F
        val totalHeight = lines.size * Fonts.fontSemibold40.FONT_HEIGHT.toFloat()
        
        if (sBG) {
            RenderUtils.drawRect(0F, 0F, maxWidth + 4F, totalHeight + 4F, BGC.rgb)
        }
        
        lines.forEachIndexed { index, line ->
            val y = 2F + index * Fonts.fontSemibold40.FONT_HEIGHT
            Fonts.fontSemibold40.drawString(line, 2F, y, getCurrentTC().rgb, true)
        }
        
        return Border(0F, 0F, maxWidth + 4F, totalHeight + 4F)
    }

    private fun calculateEstimatedDamage(player: EntityPlayer, target: EntityLivingBase): Double {

        val heldItem = player.heldItem
        var damage = 1.0 
        
        if (heldItem != null) {

            val attributes = heldItem.attributeModifiers
            if (attributes.containsKey("generic.attackDamage")) {
                val attackDamage = attributes.get("generic.attackDamage").firstOrNull()
                if (attackDamage != null) {
                    damage = attackDamage.amount + 1.0
                }
            }
        }
        

        if (!player.onGround && player.fallDistance > 0.0f && !player.isOnLadder && !player.isInWater) {
            damage *= 1.5
        }
        

        try {
            if (player.isPotionActive(net.minecraft.potion.Potion.damageBoost)) {
                val strengthLevel = player.getActivePotionEffect(net.minecraft.potion.Potion.damageBoost)?.amplifier ?: 0
                damage += (strengthLevel + 1) * 3.0
            }
        } catch (e: Exception) {

        }
        
        return damage
    }

    private fun getCurrentTC(): Color {
        return when (CM) {
            "Dynamic" -> {
                val player = mc.thePlayer
                if (player != null) {
                    val healthPercentage = player.health / player.maxHealth
                    when {
                        healthPercentage > 0.7 -> Color.GREEN
                        healthPercentage > 0.3 -> Color.YELLOW
                        else -> Color.RED
                    }
                } else Color.WHITE
            }
            "Rainbow" -> ColorUtils.rainbow()
            else -> TC
        }
    }

    override fun updateElement() {
        val player = mc.thePlayer
        if (player != null) {
            if (player.health < LastPH) {
                val damageTaken = LastPH - player.health
            }
            LastPH = player.health
        }
        if (KillAura.handleEvents() && KillAura.target != null) {
        }
    }
}
