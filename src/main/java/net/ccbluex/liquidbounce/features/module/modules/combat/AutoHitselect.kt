/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.config.*
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.MovementInput
import net.minecraft.util.MathHelper
import kotlin.math.abs

/**
 * Automatically performs hit selection to initiate combos
 */
object AutoHitselect : Module("AutoHitselect", Category.COMBAT) {
    private val maxWaitTime by int("MaxWaitTime", 500, 100..1000)
    private val rangeValue by float("Range", 8f, 1f..20f)
    private val maxAngleValue by float("MaxAngle", 120f, 30f..180f)
    private val clickDelay by int("ClickDelay", 100, 50..500)

    private var blockClicking = false
    private var wTap = false
    private var shouldClick = false
    private var startedCombo = false
    private var waitTimerReset = false

    private var lastResetTime = 0L
    private var lastClickTime = 0L
    private var waitStartTime = 0L

    val onGameTick = handler<GameTickEvent> {
        val thePlayer = mc.thePlayer ?: return@handler
        val target = getNearestEntityInRange() ?: run {
            resetState()
            return@handler
        }

        val calcYaw = (MathHelper.atan2(
            thePlayer.posZ - target.posZ,
            thePlayer.posX - target.posX
        ) * 180.0 / Math.PI - 90.0).toFloat()
        
        val diffX = abs(MathHelper.wrapAngleTo180_float(calcYaw - target.rotationYawHead))

        if (diffX > maxAngleValue) {
            resetState()
            return@handler
        }

        val playerHurt = thePlayer.hurtTime > 0
        val targetHurt = target.hurtTime > 0

        if (!playerHurt && !targetHurt) {
            if (System.currentTimeMillis() - lastResetTime > 250) {
                startedCombo = false
                lastClickTime = System.currentTimeMillis()
                shouldClick = false
            }
        } else {
            lastResetTime = System.currentTimeMillis()
        }

                    if (mc.thePlayer.getDistanceToEntityBox(target) < 3) {
            if (!waitTimerReset) {
                waitStartTime = System.currentTimeMillis()
                waitTimerReset = true
            }
        } else {
            waitTimerReset = false
        }

        if (!playerHurt && !targetHurt && System.currentTimeMillis() - waitStartTime > maxWaitTime) {
            lastClickTime = System.currentTimeMillis() + 999999
            shouldClick = true
            startedCombo = true
        }

        if (!startedCombo) {
            if (playerHurt && !targetHurt) {
                if (System.currentTimeMillis() - lastClickTime > clickDelay) {
                    shouldClick = true
                    startedCombo = true
                    wTap = true
                } else {
                    blockClicking = true
                    return@handler
                }
            } else {
                blockClicking = true
                return@handler
            }
        }

        blockClicking = !shouldClick && (!playerHurt || !targetHurt)
    }

    val onMoveInput = handler<MovementInputEvent> { event ->
        if (wTap) {
            event.originalInput.moveForward = 0f
            wTap = false
        }
    }

    private fun resetState() {
        blockClicking = false
        startedCombo = false
        shouldClick = false
    }

    private fun getNearestEntityInRange(): EntityLivingBase? {
        val thePlayer = mc.thePlayer ?: return null
        val entities = mc.theWorld.loadedEntityList ?: return null

        return entities.asSequence()
            .filterIsInstance<EntityLivingBase>()
            .filter { isSelected(it, true) && mc.thePlayer.getDistanceToEntityBox(it) <= rangeValue }
            .minByOrNull { mc.thePlayer.getDistanceToEntityBox(it) }
    }

    override val tag
        get() = "$maxWaitTime ms"
}
