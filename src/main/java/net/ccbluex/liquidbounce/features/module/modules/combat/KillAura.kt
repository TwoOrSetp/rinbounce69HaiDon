/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.Backtrack.runWithSimulatedPosition
import net.ccbluex.liquidbounce.features.module.modules.player.Blink
import net.ccbluex.liquidbounce.features.module.modules.world.Fucker
import net.ccbluex.liquidbounce.features.module.modules.world.Nuker
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.*
import net.ccbluex.liquidbounce.utils.attack.CPSCounter
import net.ccbluex.liquidbounce.utils.attack.CooldownHelper.getAttackCooldownProgress
import net.ccbluex.liquidbounce.utils.attack.CooldownHelper.resetLastAttackedTicks
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isLookingOnEntities
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.client.BlinkUtils
import net.ccbluex.liquidbounce.utils.client.ClientUtils.runTimeTicks
import net.ccbluex.liquidbounce.utils.client.ClientUtils.displayChatMessage as chat
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.utils.inventory.ItemUtils.isConsumingItem
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.render.ColorSettingsInteger
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawCircle
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawEntityBox
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawPlatform
import net.ccbluex.liquidbounce.utils.rotation.RandomizationSettings
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.rotation.RaycastUtils.runWithModifiedRaycastResult
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.getVectorForRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.isRotationFaced
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.isVisible
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.rotationDifference
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.searchCenter
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.serverRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.toRotation
import net.ccbluex.liquidbounce.utils.simulation.SimulatedPlayer
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.utils.timing.TickedActions.nextTick
import net.ccbluex.liquidbounce.utils.timing.TimeUtils.randomClickDelay
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.settings.KeyBinding
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemAxe
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.network.play.client.C02PacketUseEntity.Action.*
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.potion.Potion
import net.minecraft.util.*
import org.lwjgl.input.Keyboard
import java.awt.Color
import kotlin.math.*

object KillAura : Module("KillAura", Category.COMBAT, Keyboard.KEY_R) {
    /**
     * OPTIONS
     */

    private val simulateCooldown by boolean("SimulateCooldown", false)
    private val simulateDoubleClicking by boolean("SimulateDoubleClicking", false) { !simulateCooldown }
    private val pauseOnRightClick by boolean("PauseOnRightClick", false)
    
    // RmoveReduceDmg New System (i mean upgrade)
    private val removeReduceDmgEnabled by boolean("RemoveReduceDmg", false)
    private val removeReduceDmgMode by choices("RemoveReduceDmgMode", // mode
        arrayOf("Vanilla", "NCP", "AAC", "Vulcan", "Matrix", "Karhu", "Grim", "Intave", "Verus", "Negativity", "Themis", "Spartan", "Horizon", "Polar", "Smart"), "Smart") { removeReduceDmgEnabled }
    private val removeReduceDmgTiming by choices("RemoveReduceDmgTiming", // mode
        arrayOf("Pre-Hit", "Post-Hit", "Instant", "Delayed", "Smart"), "Smart") { removeReduceDmgEnabled }
    private val removeReduceDmgDelay by int("RemoveReduceDmgDelay", 0, 0..500) { removeReduceDmgEnabled }
    private val removeReduceDmgRandomization by boolean("RemoveReduceDmgRandomization", true) { removeReduceDmgEnabled }
    private val removeReduceDmgRandomRange by int("RemoveReduceDmgRandomRange", 50, 0..200) { removeReduceDmgEnabled && removeReduceDmgRandomization }
    private val removeReduceDmgPacketMode by choices("RemoveReduceDmgPacketMode",
        arrayOf("Single", "Double", "Triple", "Burst", "Adaptive"), "Adaptive") { removeReduceDmgEnabled }
    private val removeReduceDmgOnlyOnBlock by boolean("RemoveReduceDmgOnlyOnBlock", true) { removeReduceDmgEnabled }
    private val removeReduceDmgCompatibility by boolean("RemoveReduceDmgCompatibility", true) { removeReduceDmgEnabled }
    private val removeReduceDmgDebug by boolean("RemoveReduceDmgDebug", false) { removeReduceDmgEnabled }

    // Tracking Var
    private var lastAttackTime = 0L
    private var blockReleasedAt = 0L
    private var removeReduceDmgAttempts = 0
    private var lastRemoveReduceDmgTime = 0L
    private var removeReduceDmgSuccess = 0
    private var removeReduceDmgFails = 0
    private val removeReduceDmgHistory = mutableListOf<Long>()
    private var adaptiveDelay = 0
    private var lastServerResponse = 0L

    // CPS - Attack speed
    private val cps by intRange("CPS", 5..8, 1..50) { !simulateCooldown }.onChanged {
        attackDelay = randomClickDelay(it.first, it.last)
    }

    private val hurtTime by int("HurtTime", 10, 0..10) { !simulateCooldown }

    private val activationSlot by boolean("ActivationSlot", false)
    private val preferredSlot by int("PreferredSlot", 1, 1..9) { activationSlot }

    private val clickOnly by boolean("ClickOnly", false)

    // Range
    // TODO: Make block range independent from attack range
    private val range: Float by float("Range", 3.7f, 1f..8f).onChanged {
        blockRange = blockRange.coerceAtMost(it)
    }
    private val scanRange by float("ScanRange", 2f, 0f..10f)
    private val throughWallsRange by float("ThroughWallsRange", 3f, 0f..8f)
    private val rangeSprintReduction by float("RangeSprintReduction", 0f, 0f..0.4f)

    // Modes
    private val priority by choices(
        "Priority", arrayOf(
            "Health",
            "Distance",
            "Direction",
            "LivingTime",
            "Armor",
            "HurtResistance",
            "HurtTime",
            "HealthAbsorption",
            "RegenAmplifier",
            "OnLadder",
            "InLiquid",
            "InWeb"
        ), "Distance"
    )
    private val targetMode by choices("TargetMode", arrayOf("Single", "Switch", "Multi"), "Switch")
    private val limitedMultiTargets by int("LimitedMultiTargets", 0, 0..50) { targetMode == "Multi" }
    private val maxSwitchFOV by float("MaxSwitchFOV", 90f, 30f..180f) { targetMode == "Switch" }

    // Delay
    private val switchDelay by int("SwitchDelay", 15, 1..1000) { targetMode == "Switch" }

    // Bypass
    private val swing by boolean("Swing", true)
    private val keepSprint by boolean("KeepSprint", true)

    // Settings
    private val autoF5 by boolean("AutoF5", false)
    private val onScaffold by boolean("OnScaffold", false)
    private val onDestroyBlock by boolean("OnDestroyBlock", false)

    // AutoBlock
    val autoBlock by choices("AutoBlock", arrayOf("Off", "Packet", "Fake", "RightHold", "Silent", "Test"), "Packet")
    private val blockMaxRange by float("BlockMaxRange", 3f, 0f..8f) { autoBlock == "Packet" || autoBlock == "Silent" }
    private val unblockMode by choices(
        "UnblockMode", arrayOf("Stop", "Switch", "Empty"), "Stop"
    ) { autoBlock == "Packet" || autoBlock == "Silent" }
    private val releaseAutoBlock by boolean("ReleaseAutoBlock", true) { autoBlock !in arrayOf("Off", "Fake") }
    val forceBlockRender by boolean("ForceBlockRender", true) {
        autoBlock !in arrayOf(
            "Off", "Fake"
        ) && releaseAutoBlock
    }
    private val ignoreTickRule by boolean("IgnoreTickRule", false) {
        autoBlock !in arrayOf(
            "Off", "Fake"
        ) && releaseAutoBlock
    }
    private val blockRate by int("BlockRate", 100, 1..100) { autoBlock !in arrayOf("Off", "Fake") && releaseAutoBlock }

    private val uncpAutoBlock by boolean("UpdatedNCPAutoBlock", false) {
        autoBlock !in arrayOf(
            "Off", "Fake"
        ) && !releaseAutoBlock
    }

    private val switchStartBlock by boolean("SwitchStartBlock", false) { autoBlock !in arrayOf("Off", "Fake") }

    private val interactAutoBlock by boolean("InteractAutoBlock", true) { autoBlock !in arrayOf("Off", "Fake") }

    val blinkAutoBlock by boolean("BlinkAutoBlock", false) { autoBlock !in arrayOf("Off", "Fake") }

    private val blinkBlockTicks by int("BlinkBlockTicks", 3, 2..5) {
        autoBlock !in arrayOf(
            "Off", "Fake"
        ) && blinkAutoBlock
    }

    // AutoBlock conditions
    private val smartAutoBlock by boolean("SmartAutoBlock", false) { autoBlock == "Packet" }

    // Ignore all blocking conditions, except for block rate, when standing still
    private val forceBlock by boolean("ForceBlockWhenStill", true) { smartAutoBlock }

    // Don't block if target isn't holding a sword or an axe
    private val checkWeapon by boolean("CheckEnemyWeapon", true) { smartAutoBlock }

    // TODO: Make block range independent from attack range
    private var blockRange: Float by float("BlockRange", range, 1f..8f) {
        smartAutoBlock
    }.onChange { _, new ->
        new.coerceAtMost(this@KillAura.range)
    }

    // Don't block when you can't get damaged
    private val maxOwnHurtTime by int("MaxOwnHurtTime", 3, 0..10) { smartAutoBlock }

    // Don't block if target isn't looking at you
    private val maxDirectionDiff by float("MaxOpponentDirectionDiff", 60f, 30f..180f) { smartAutoBlock }

    // Don't block if target is swinging an item and therefore cannot attack
    private val maxSwingProgress by int("MaxOpponentSwingProgress", 1, 0..5) { smartAutoBlock }

    // Rotations
    private val options = RotationSettings(this).withoutKeepRotation()

    // Raycast
    private val raycastValue = boolean("RayCast", true) { options.rotationsActive }
    private val raycast by raycastValue
    private val raycastIgnored by boolean(
        "RayCastIgnored", false
    ) { raycastValue.isActive() && options.rotationsActive }
    private val livingRaycast by boolean("LivingRayCast", true) { raycastValue.isActive() && options.rotationsActive }

    // Hit delay
    private val useHitDelay by boolean("UseHitDelay", false)
    private val hitDelayTicks by int("HitDelayTicks", 1, 1..5) { useHitDelay }

    private val generateClicksBasedOnDist by boolean("GenerateClicksBasedOnDistance", false)
    private val cpsMultiplier by intRange("CPS-Multiplier", 1..2, 1..10) { generateClicksBasedOnDist }
    private val distanceFactor by floatRange("DistanceFactor", 5F..10F, 1F..10F) { generateClicksBasedOnDist }

    private val generateSpotBasedOnDistance by boolean("GenerateSpotBasedOnDistance", false) { options.rotationsActive }

    private val randomization = RandomizationSettings(this) { options.rotationsActive }
    private val outBorder by boolean("OutBorder", false) { options.rotationsActive }

    private val highestBodyPointToTargetValue = choices(
        "HighestBodyPointToTarget", arrayOf("Head", "Body", "Feet"), "Head"
    ) {
        options.rotationsActive
    }.onChange { _, new ->
        val newPoint = RotationUtils.BodyPoint.fromString(new)
        val lowestPoint = RotationUtils.BodyPoint.fromString(lowestBodyPointToTarget)
        val coercedPoint = RotationUtils.coerceBodyPoint(newPoint, lowestPoint, RotationUtils.BodyPoint.HEAD)
        coercedPoint.displayName
    }
    private val highestBodyPointToTarget: String by highestBodyPointToTargetValue

    private val lowestBodyPointToTargetValue = choices(
        "LowestBodyPointToTarget", arrayOf("Head", "Body", "Feet"), "Feet"
    ) {
        options.rotationsActive
    }.onChange { _, new ->
        val newPoint = RotationUtils.BodyPoint.fromString(new)
        val highestPoint = RotationUtils.BodyPoint.fromString(highestBodyPointToTarget)
        val coercedPoint = RotationUtils.coerceBodyPoint(newPoint, RotationUtils.BodyPoint.FEET, highestPoint)
        coercedPoint.displayName
    }

    private val lowestBodyPointToTarget: String by lowestBodyPointToTargetValue

    private val horizontalBodySearchRange by floatRange(
        "HorizontalBodySearchRange", 0f..1f, 0f..1f
    ) { options.rotationsActive }

    private val fov by float("FOV", 180f, 0f..180f)

    // Prediction
    private val predictClientMovement by int("PredictClientMovement", 2, 0..5)
    private val predictOnlyWhenOutOfRange by boolean(
        "PredictOnlyWhenOutOfRange", false
    ) { predictClientMovement != 0 }
    private val predictEnemyPosition by float("PredictEnemyPosition", 1.5f, -1f..2f)

    private val forceFirstHit by boolean("ForceFirstHit", false) { !respectMissCooldown && !useHitDelay }

    // Extra swing
    private val failSwing by boolean("FailSwing", true) { swing && options.rotationsActive }
    private val respectMissCooldown by boolean(
        "RespectMissCooldown", false
    ) { swing && failSwing && options.rotationsActive }
    private val swingOnlyInAir by boolean("SwingOnlyInAir", true) { swing && failSwing && options.rotationsActive }
    private val maxRotationDifferenceToSwing by float(
        "MaxRotationDifferenceToSwing", 180f, 0f..180f
    ) { swing && failSwing && options.rotationsActive }
    private val swingWhenTicksLate = boolean("SwingWhenTicksLate", false) {
        swing && failSwing && maxRotationDifferenceToSwing != 180f && options.rotationsActive
    }
    private val ticksLateToSwing by int(
        "TicksLateToSwing", 4, 0..20
    ) { swing && failSwing && swingWhenTicksLate.isActive() && options.rotationsActive }
    private val renderBoxOnSwingFail by boolean("RenderBoxOnSwingFail", false) { failSwing }
    private val renderBoxColor = ColorSettingsInteger(this, "RenderBoxColor") { renderBoxOnSwingFail }.with(Color.CYAN)
    private val renderBoxFadeSeconds by float("RenderBoxFadeSeconds", 1f, 0f..5f) { renderBoxOnSwingFail }

    // Inventory
    private val simulateClosingInventory by boolean("SimulateClosingInventory", false) { !noInventoryAttack }
    private val noInventoryAttack by boolean("NoInvAttack", false)
    private val noInventoryDelay by int("NoInvDelay", 200, 0..500) { noInventoryAttack }
    private val noConsumeAttack by choices(
        "NoConsumeAttack", arrayOf("Off", "NoHits", "NoRotation"), "Off"
    ).subjective()

    // Visuals
    private val mark by choices("Mark", arrayOf("None", "Platform", "Box", "Circle"), "Circle").subjective()
    private val fakeSharp by boolean("FakeSharp", true).subjective()
    private val renderAimPointBox by boolean("RenderAimPointBox", false).subjective()
    private val aimPointBoxColor by color("AimPointBoxColor", Color.CYAN) { renderAimPointBox }.subjective()
    private val aimPointBoxSize by float("AimPointBoxSize", 0.1f, 0f..0.2F) { renderAimPointBox }.subjective()

    // Circle options
    private val circleStartColor by color("CircleStartColor", Color(144, 238, 144)) { mark == "Circle" }.subjective() 
    private val circleEndColor by color("CircleEndColor", Color(173, 216, 230).withAlpha(120)) { mark == "Circle" }.subjective() 
    private val fillInnerCircle by boolean("FillInnerCircle", true) { mark == "Circle" }.subjective() 
    private val withHeight by boolean("WithHeight", true) { mark == "Circle" }.subjective()
    private val animateHeight by boolean("AnimateHeight", false) { withHeight }.subjective()
    private val heightRange by floatRange("HeightRange", 0.0f..0.4f, -2f..2f) { withHeight }.subjective()
    private val extraWidth by float("ExtraWidth", 0F, 0F..2F) { mark == "Circle" }.subjective()
    private val animateCircleY by boolean("AnimateCircleY", true) { fillInnerCircle || withHeight }.subjective()
    private val circleYRange by floatRange("CircleYRange", 0F..0.5F, 0F..2F) { animateCircleY }.subjective()
    private val duration by float(
        "Duration", 1.5F, 0.5F..3F, suffix = "Seconds"
    ) { animateCircleY || animateHeight }.subjective()
    
    private fun getAnimationProgress() = (System.currentTimeMillis() % (duration * 1000L)) / (duration * 1000L)

    // Box option
    private val boxOutline by boolean("Outline", true) { mark == "Box" }.subjective()

    /**
     * MODULE
     */

    // Target
    var target: EntityLivingBase? = null
    private var hittable = false
    private val prevTargetEntities = mutableListOf<Int>()
    
    // Pause on RightClick
    private var pausedByRightClick = false
    
    // Attack delay
    private val attackTimer = MSTimer()
    private var attackDelay = 0
    private var clicks = 0
    private var attackTickTimes = mutableListOf<Pair<MovingObjectPosition, Int>>()

    // Container Delay
    private var containerOpen = -1L

    // Block status
    var renderBlocking = false
    var blockStatus = false
    private var blockStopInDead = false
    private var lastBlockTime = 0L
    private val silentBlockDelay by int("SilentBlockDelay", 50, 0..100) { autoBlock == "Silent" }

    // Switch Delay
    private val switchTimer = MSTimer()

    // Blink AutoBlock
    private var blinked = false

    // Swing fails
    private val swingFails = mutableListOf<SwingFailData>()

    /**
     * Disable kill aura module
     */
    override fun onToggle(state: Boolean) {
        target = null
        hittable = false
        prevTargetEntities.clear()
        attackTickTimes.clear()
        attackTimer.reset()
        clicks = 0
        
        if (autoBlock == "RightHold") {
            mc.gameSettings.keyBindUseItem.pressed = false
        }
        
        if (autoBlock == "Test") { 
             mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1)) 
             mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem)) 
         }

        if (blinkAutoBlock) {
            BlinkUtils.unblink()
            blinked = false
        }

        if (autoF5) mc.gameSettings.thirdPersonView = 0

        stopBlocking(true)

        synchronized(swingFails) {
            swingFails.clear()
        }
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        update()
    }

    fun update() {
        if (pauseOnRightClick && pausedByRightClick) {
            return
        }
        
        if (cancelRun || (noInventoryAttack && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelay))) return

        // Update target
        updateTarget()

        if (autoF5) {
            if (mc.gameSettings.thirdPersonView != 1 && target != null) {
                mc.gameSettings.thirdPersonView = 1
            }
        }
    }

    val onWorld = handler<WorldEvent> {
        attackTickTimes.clear()

        if (blinkAutoBlock && BlinkUtils.isBlinking) BlinkUtils.unblink()

        synchronized(swingFails) {
            swingFails.clear()
        }
    }

    /**
     * Tick event
     */
    val onTick = handler<GameTickEvent>(priority = 2) {
        if (pauseOnRightClick) {
            if (mc.gameSettings.keyBindUseItem.isKeyDown) {
                if (!pausedByRightClick) {
                    pausedByRightClick = true
                    stopBlocking(true)
                }
            } else if (pausedByRightClick) {
                pausedByRightClick = false
            }
        }
        
        if (pausedByRightClick) {
            if (target != null) {
                clicks = 0

            if (blockStatus) {
                stopBlocking(true)
                }
            }
            return@handler
        }
        
        val player = mc.thePlayer ?: return@handler

        if (blockStatus && player.heldItem?.item !is ItemSword) {
            blockStatus = false
            renderBlocking = false
            return@handler
        }

        if (shouldPrioritize()) {
            target = null
            renderBlocking = false
            return@handler
        }

        if (clickOnly && !mc.gameSettings.keyBindAttack.isKeyDown) {
            clicks = 0
            return@handler
        }

        if (blockStatus && autoBlock == "Packet" && releaseAutoBlock && !ignoreTickRule) {
            clicks = 0
            stopBlocking()
            return@handler
        }

        if (cancelRun) {
            target = null
            hittable = false
            stopBlocking()
            return@handler
        }

        if (noInventoryAttack && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelay)) {
            target = null
            hittable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return@handler
        }

        if (simulateCooldown && getAttackCooldownProgress() < 1f) {
            return@handler
        }

        if (target == null && !blockStopInDead) {
            blockStopInDead = true
            stopBlocking()
            return@handler
        }

        if (blinkAutoBlock) {
            when (player.ticksExisted % (blinkBlockTicks + 1)) {
                0 -> {
                    if (blockStatus && !blinked && !BlinkUtils.isBlinking) {
                        blinked = true
                    }
                }

                1 -> {
                    if (blockStatus && blinked && BlinkUtils.isBlinking) {
                        stopBlocking()
                    }
                }

                blinkBlockTicks -> {
                    if (!blockStatus && blinked && BlinkUtils.isBlinking) {
                        BlinkUtils.unblink()
                        blinked = false

                        startBlocking(target!!, interactAutoBlock, autoBlock == "Fake") // block again
                    }
                }
            }
        }
        
        if (autoBlock == "RightHold") {
            val localTarget = target
            if (localTarget != null
                && player.heldItem?.item is ItemSword
                && player.getDistanceToEntityBox(localTarget) <= blockMaxRange) {
                mc.gameSettings.keyBindUseItem.pressed = true
            } else {
                mc.gameSettings.keyBindUseItem.pressed = false
            }
        }

        if (target != null) {
            if (player.getDistanceToEntityBox(target!!) > blockMaxRange && blockStatus) {
                stopBlocking(true)
                return@handler
            } else {
                if (autoBlock != "Off" && !releaseAutoBlock) {
                    renderBlocking = true
                }
            }

            // Usually when you butterfly click, you end up clicking two (and possibly more) times in a single tick.
            // Sometimes you also do not click. The positives outweigh the negatives, however.
            val extraClicks = if (simulateDoubleClicking && !simulateCooldown) nextInt(-1, 1) else 0

            // Generate clicks based on distance from us to target.
            val generatedClicks = if (generateClicksBasedOnDist) {
                val distance = player.getDistanceToEntityBox(target!!)
                ((distance / distanceFactor.random()) * cpsMultiplier.random()).roundToInt()
            } else 0

            var maxClicks = clicks + extraClicks + generatedClicks

            val prevHittable = hittable

            updateHittable()

            if (!prevHittable && hittable && maxClicks == 0 && forceFirstHit) {
                maxClicks++
            }

            repeat(maxClicks) {
                val wasBlocking = blockStatus

                runAttack(it == 0, it + 1 == maxClicks)
                clicks--

                if (wasBlocking && !blockStatus && (releaseAutoBlock && !ignoreTickRule || autoBlock == "Off")) {
                    return@handler
                }
            }
        } else {
            renderBlocking = false
        }
    }

    /**
     * Render event
     */
    val onRender3D = handler<Render3DEvent> {
        
        handleFailedSwings()

        drawAimPointBox()

        if (cancelRun) {
            target = null
            hittable = false
            return@handler
        }

        if (noInventoryAttack && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelay)) {
            target = null
            hittable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return@handler
        }

        target ?: return@handler

        if (attackTimer.hasTimePassed(attackDelay)) {
            if (cps.last > 0) clicks++
            attackTimer.reset()

            attackDelay = randomClickDelay(cps.first, cps.last)
        }

        val hittableColor = if (hittable) Color(37, 126, 255, 70) else Color(255, 0, 0, 70)

        if (targetMode != "Multi") {
            when (mark.lowercase()) {
                "none" -> return@handler
                "platform" -> drawPlatform(target!!, hittableColor)
                "box" -> drawEntityBox(target!!, hittableColor, boxOutline)
                "circle" -> {
                    val progress = getAnimationProgress()
                    
                    drawCircle(
                        target!!,
                        duration * 1000F,
                        heightRange,
                        target!!.width + extraWidth,
                        fillInnerCircle,
                        withHeight,
                        if (animateCircleY) circleYRange else null,
                        circleStartColor.rgb,
                        circleEndColor.rgb
                    )
                }
            }
        }
    }

    /**
     * Attack enemy
     */
    private fun runAttack(isFirstClick: Boolean, isLastClick: Boolean) {
        val currentTarget = this.target ?: return

        val player = mc.thePlayer ?: return
        val world = mc.theWorld ?: return

        if (noConsumeAttack == "NoHits" && isConsumingItem()) {
            return
        }

        // Settings
        val multi = targetMode == "Multi"
        val manipulateInventory = simulateClosingInventory && !noInventoryAttack && serverOpenInventory

        if (hittable && currentTarget.hurtTime > hurtTime) {
            return
        }

        // Check if enemy is not hittable
        if (!hittable && options.rotationsActive) {
            if (swing && failSwing) {
                val rotation = currentRotation ?: player.rotation

                // Can humans keep click consistency when performing massive rotation changes?
                // (10-30 rotation difference/doing large mouse movements for example)
                // Maybe apply to attacks too?
                if (rotationDifference(rotation) > maxRotationDifferenceToSwing) {
                    // At the same time there is also a chance of the user clicking at least once in a while
                    // when the consistency has dropped a lot.
                    val shouldIgnore = swingWhenTicksLate.isActive() && ticksSinceClick() >= ticksLateToSwing

                    if (!shouldIgnore) {
                        return
                    }
                }

                runWithModifiedRaycastResult(rotation, range.toDouble(), throughWallsRange.toDouble()) {
                    if (swingOnlyInAir && !it.typeOfHit.isMiss) {
                        return@runWithModifiedRaycastResult
                    }

                    // Left click miss cool-down logic:
                    // When you click and miss, you receive a 10 tick cool down.
                    // It decreases gradually (tick by tick) when you hold the button.
                    // If you click and then release the button, the cool down drops from where it was immediately to 0.
                    // Most humans will release the button 1-2 ticks max after clicking, leaving them with an average of 10 CPS.
                    // The maximum CPS allowed when you miss a hit is 20 CPS, if you click and release immediately, which is highly unlikely.
                    // With that being said, we force an average of 10 CPS by doing this below, since 10 CPS when missing is possible.
                    if (respectMissCooldown && ticksSinceClick() <= 1 && it.typeOfHit.isMiss) {
                        return@runWithModifiedRaycastResult
                    }

                    val shouldEnterBlockBreakProgress =
                        !shouldDelayClick(it.typeOfHit) || attackTickTimes.lastOrNull()?.first?.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK

                    if (shouldEnterBlockBreakProgress) {
                        // Close inventory when open
                        if (manipulateInventory && isFirstClick) serverOpenInventory = false
                    }

                    val prevCooldown = mc.leftClickCounter

                    // Is any GUI coming from our client?
                    val isAnyClientGuiActive = mc.currentScreen?.javaClass?.`package`?.name?.contains(
                        LiquidBounce.CLIENT_NAME, ignoreCase = true
                    ) == true

                    if (isAnyClientGuiActive) {
                        mc.leftClickCounter = 0
                    }

                    if (!shouldDelayClick(it.typeOfHit)) {
                        attackTickTimes += it to runTimeTicks

                        if (it.typeOfHit.isEntity) {
                            val entity = it.entityHit

                            // Use own function instead of clickMouse() to maintain keep sprint, auto block, etc
                            if (entity is EntityLivingBase && isSelected(entity, true)) {
                                attackEntity(entity, isLastClick)
                            } else attackTickTimes -= it to runTimeTicks
                        } else {
                            // Imitate game click
                            mc.clickMouse()

                            if (renderBoxOnSwingFail) {
                                synchronized(swingFails) {
                                    val centerDistance = (currentTarget.hitBox.center - player.eyes).lengthVector()
                                    val spot = player.eyes + getVectorForRotation(rotation) * centerDistance

                                    swingFails += SwingFailData(spot, System.currentTimeMillis())
                                }
                            }
                        }
                    }

                    if (shouldEnterBlockBreakProgress && isLastClick) {
                        /**
                         * This is used to update the block breaking progress, resulting in sending an animation packet.
                         *
                         * Setting this function's parameter to [false] would still obey vanilla clicking logic,
                         * but only if you were releasing the click button immediately after pressing. Does not seem legit
                         * in the long term, right? This is why we are going to set it to [true], so it can send the animation packet.
                         */
                        mc.sendClickBlockToController(true)
                        /**
                         * Since we want to simulate proper clicking behavior, we schedule the block break progress stop
                         * in the next tick, since that is a doable action by the average player.
                         */
                        nextTick {
                            mc.sendClickBlockToController(false)

                            // Swings are sent a tick after stopping the block break progress.
                            clicks = 0

                            // [manipulateInventory] could have been changed at that point, but it is okay because
                            // serverOpenInventory's backing fields check for same values.
                            if (manipulateInventory) serverOpenInventory = true
                        }
                    }

                    if (isAnyClientGuiActive) {
                        mc.leftClickCounter = prevCooldown
                    }
                }
            }

            return
        }

        // Close inventory when open
        if (manipulateInventory && isFirstClick) serverOpenInventory = false

        blockStopInDead = false

        if (!multi) {
            attackEntity(currentTarget, isLastClick)
        } else {
            var targets = 0

            for (entity in world.loadedEntityList) {
                val distance = player.getDistanceToEntityBox(entity)

                if (entity is EntityLivingBase && isSelected(entity, true) && distance <= getRange(entity)) {
                    attackEntity(entity, isLastClick)

                    targets += 1

                    if (limitedMultiTargets != 0 && limitedMultiTargets <= targets) break
                }
            }
        }

        if (!isLastClick) return

        val switchMode = targetMode == "Switch"

        if (!switchMode || switchTimer.hasTimePassed(switchDelay)) {
            prevTargetEntities += currentTarget.entityId

            if (switchMode) {
                switchTimer.reset()
            }
        }

        // Open inventory
        if (manipulateInventory) serverOpenInventory = true
    }

    /**
     * Update current target
     */
    private fun updateTarget() {
        if (shouldPrioritize()) return

        // Reset fixed target to null
        target = null

        val switchMode = targetMode == "Switch"

        val theWorld = mc.theWorld ?: return
        val thePlayer = mc.thePlayer ?: return

        var bestTarget: EntityLivingBase? = null
        var bestValue: Double? = null

        for (entity in theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !isSelected(
                    entity, true
                ) || switchMode && entity.entityId in prevTargetEntities
            ) continue

            val distance = Backtrack.runWithNearestTrackedDistance(entity) { thePlayer.getDistanceToEntityBox(entity) }

            if (switchMode && distance > range && prevTargetEntities.isNotEmpty()) continue

            val entityFov = rotationDifference(entity)

            if (distance > maxRange || fov != 180F && entityFov > fov) continue

            if (switchMode && !isLookingOnEntities(entity, maxSwitchFOV.toDouble())) continue

            val currentValue = when (priority.lowercase()) {
                "distance" -> distance
                "direction" -> entityFov.toDouble()
                "health" -> entity.health.toDouble()
                "livingtime" -> -entity.ticksExisted.toDouble()
                "armor" -> entity.totalArmorValue.toDouble()
                "hurtresistance" -> entity.hurtResistantTime.toDouble()
                "hurttime" -> entity.hurtTime.toDouble()
                "healthabsorption" -> (entity.health + entity.absorptionAmount).toDouble()
                "regenamplifier" -> if (entity.isPotionActive(Potion.regeneration)) {
                    entity.getActivePotionEffect(Potion.regeneration).amplifier.toDouble()
                } else -1.0

                "inweb" -> if (entity.isInWeb) -1.0 else Double.MAX_VALUE
                "onladder" -> if (entity.isOnLadder) -1.0 else Double.MAX_VALUE
                "inliquid" -> if (entity.isInWater || entity.isInLava) -1.0 else Double.MAX_VALUE
                else -> null
            } ?: continue

            if (bestValue == null || currentValue < bestValue) {
                bestValue = currentValue
                bestTarget = entity
            }
        }

        if (bestTarget != null) {
            if (Backtrack.runWithNearestTrackedDistance(bestTarget) { updateRotations(bestTarget) }) {
                target = bestTarget
                return
            }
        }

        if (prevTargetEntities.isNotEmpty()) {
            prevTargetEntities.clear()
            updateTarget()
        }
    }

    /**
     * Attack [entity]
     */
    private fun attackEntity(entity: EntityLivingBase, isLastClick: Boolean) {
        val thePlayer = mc.thePlayer

        if (shouldPrioritize()) return
        if (autoBlock == "RightHold") {
            mc.gameSettings.keyBindUseItem.pressed = false
        }

        val time = System.currentTimeMillis()
        
        if (autoBlock == "Silent" && time - lastBlockTime >= silentBlockDelay && canBlock) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.keyCode)
            lastBlockTime = time
            return
        }

        if (thePlayer.isBlocking && (autoBlock == "Off" && blockStatus || autoBlock == "Packet" && releaseAutoBlock)) {
            stopBlocking()

            if (!ignoreTickRule || autoBlock == "Off") {
                return
            }
        }

        // The function is only called when we are facing an entity
        if (shouldDelayClick(MovingObjectPosition.MovingObjectType.ENTITY)) {
            return
        }

        if (!blinkAutoBlock || !BlinkUtils.isBlinking) {
            val affectSprint = false.takeIf { KeepSprint.handleEvents() || keepSprint }

            thePlayer.attackEntityWithModifiedSprint(entity, affectSprint) { if (swing) thePlayer.swingItem() }
            if (autoBlock == "RightHold"
                && thePlayer.heldItem?.item is ItemSword
                && thePlayer.getDistanceToEntityBox(entity) <= blockMaxRange) {
                mc.gameSettings.keyBindUseItem.pressed = true
            }
            // Apply enchantment critical effect if FakeSharp is enabled
            if (EnchantmentHelper.getModifierForCreature(
                    thePlayer.heldItem, entity.creatureAttribute
                ) <= 0F && fakeSharp
            ) {
                thePlayer.onEnchantmentCritical(entity)
            }
        }

        // Start blocking after attack
        if (autoBlock != "Off" && (thePlayer.isBlocking || canBlock) && (!blinkAutoBlock && isLastClick || blinkAutoBlock && (!blinked || !BlinkUtils.isBlinking))) {
            startBlocking(entity, interactAutoBlock, autoBlock == "Fake")
        }

        resetLastAttackedTicks()
    }

    /**
     * Update rotations to enemy
     */
    private fun updateRotations(entity: Entity): Boolean {
        val player = mc.thePlayer ?: return false

        if (shouldPrioritize()) return false

        if (!options.rotationsActive) {
            return player.getDistanceToEntityBox(entity) <= range
        }

        val prediction = entity.currPos.subtract(entity.prevPos).times(2 + predictEnemyPosition.toDouble())

        val boundingBox = entity.hitBox.offset(prediction)
        val (currPos, oldPos) = player.currPos to player.prevPos

        val simPlayer = SimulatedPlayer.fromClientPlayer(RotationUtils.modifiedInput)

        simPlayer.rotationYaw = (currentRotation ?: player.rotation).yaw

        var pos = currPos

        repeat(predictClientMovement) {
            val previousPos = simPlayer.pos

            simPlayer.tick()

            if (predictOnlyWhenOutOfRange) {
                player.setPosAndPrevPos(simPlayer.pos)

                val currDist = player.getDistanceToEntityBox(entity)

                player.setPosAndPrevPos(previousPos)

                val prevDist = player.getDistanceToEntityBox(entity)

                player.setPosAndPrevPos(currPos, oldPos)
                pos = simPlayer.pos

                if (currDist <= range && currDist <= prevDist) {
                    return@repeat
                }
            }

            pos = previousPos
        }

        player.setPosAndPrevPos(pos)

        val rotation = searchCenter(
            boundingBox,
            generateSpotBasedOnDistance,
            outBorder && !attackTimer.hasTimePassed(attackDelay / 2),
            randomization,
            predict = false,
            lookRange = range + scanRange,
            attackRange = range,
            throughWallsRange = throughWallsRange,
            bodyPoints = listOf(highestBodyPointToTarget, lowestBodyPointToTarget),
            horizontalSearch = horizontalBodySearchRange
        )

        if (rotation == null) {
            player.setPosAndPrevPos(currPos, oldPos)

            return false
        }

        setTargetRotation(rotation, options = options)

        player.setPosAndPrevPos(currPos, oldPos)

        return true
    }

    private fun ticksSinceClick() = runTimeTicks - (attackTickTimes.lastOrNull()?.second ?: 0)

    /**
     * Check if enemy is hittable with current rotations
     */
    private fun updateHittable() {
        val player = mc.thePlayer ?: return
        val target = this.target ?: run {
            hittable = false
            return
        }

        if (shouldPrioritize()) {
            hittable = false
            return
        }

        if (!options.rotationsActive) {
            hittable = player.getDistanceToEntityBox(target) <= range
            return
        }

        val eyes = player.eyes
        val rotation = currentRotation ?: player.rotation
        val lookVec = getVectorForRotation(rotation)
        val distance = player.getDistanceToEntityBox(target)

        if (distance > range) {
            hittable = false
            return
        }

        val targetBox = if (predictEnemyPosition > 0) {
            val targetMotionX = target.posX - target.prevPosX
            val targetMotionY = target.posY - target.prevPosY
            val targetMotionZ = target.posZ - target.prevPosZ

            target.entityBoundingBox.offset(
                targetMotionX * predictEnemyPosition,
                targetMotionY * predictEnemyPosition,
                targetMotionZ * predictEnemyPosition
            )
        } else {
            target.entityBoundingBox
        }

        if (raycast) {
            val raycastEntity = raycastEntity(
                range.toDouble(), rotation.yaw, rotation.pitch
            ) { entity -> !livingRaycast || entity is EntityLivingBase && entity !is EntityArmorStand }

            if (raycastEntity != null && raycastEntity is EntityLivingBase && (!(raycastEntity is EntityPlayer && raycastEntity.isClientFriend()))) {
                if (raycastIgnored && target != raycastEntity) {
                    this.target = raycastEntity
                }

                hittable = this.target == raycastEntity
                return
            }

            hittable = false
        } else {
            hittable = isRotationFaced(target, range.toDouble(), rotation)

            if (!hittable && predictEnemyPosition > 0) {
                val rayEnd = Vec3(
                    eyes.xCoord + lookVec.xCoord * range.toDouble(),
                    eyes.yCoord + lookVec.yCoord * range.toDouble(),
                    eyes.zCoord + lookVec.zCoord * range.toDouble()
                )
                val intercept = targetBox.calculateIntercept(eyes, rayEnd)
                hittable = intercept != null
            }
        }

        var specialTrackingApplied = false

        if (ForwardTrack.handleEvents()) {
            ForwardTrack.includeEntityTruePos(target) {
                checkIfAimingAtBox(target, rotation, eyes, onSuccess = {
                    hittable = true
                    specialTrackingApplied = true
                })
            }
        }

        if (!hittable && !specialTrackingApplied && Backtrack.handleEvents()) {
            Backtrack.loopThroughBacktrackData(target) {
                var result = false

                checkIfAimingAtBox(target, rotation, eyes, onSuccess = {
                    hittable = true
                    result = true
                }, onFail = {
                    result = false
                })

                return@loopThroughBacktrackData result
            }
        }

        if (!hittable && !specialTrackingApplied) {
            if (targetBox.isVecInside(eyes)) {
                hittable = true
                return
            }

            val rayEnd = Vec3(
                eyes.xCoord + lookVec.xCoord * range.toDouble(),
                eyes.yCoord + lookVec.yCoord * range.toDouble(),
                eyes.zCoord + lookVec.zCoord * range.toDouble()
            )
            val intercept = targetBox.calculateIntercept(eyes, rayEnd)

            hittable = intercept != null && (isVisible(intercept.hitVec) || distance <= throughWallsRange)
        }
    }

    /**
     * Start blocking
     */
    private fun startBlocking(interactEntity: Entity, interact: Boolean, fake: Boolean = false) {
        val player = mc.thePlayer ?: return

        if (blockStatus && (!uncpAutoBlock || !blinkAutoBlock) || shouldPrioritize()) return

        if (mc.thePlayer.isBlocking) {
            blockStatus = true
            renderBlocking = true
            return
        }

        if (unblockMode == "Empty" && player.inventory.firstEmptyStack !in 0..8) {
            return
        }

        if (!fake) {
            if (!(blockRate > 0 && nextInt(endExclusive = 100) <= blockRate)) return

            if (interact) {
                val positionEye = player.eyes

                val boundingBox = interactEntity.hitBox

                val (yaw, pitch) = currentRotation ?: player.rotation

                val vec = getVectorForRotation(Rotation(yaw, pitch))

                val lookAt = positionEye.add(vec * maxRange.toDouble())

                val movingObject = boundingBox.calculateIntercept(positionEye, lookAt) ?: return
                val hitVec = movingObject.hitVec

                sendPackets(
                    C02PacketUseEntity(interactEntity, hitVec - interactEntity.positionVector),
                    C02PacketUseEntity(interactEntity, INTERACT)
                )

            }

            if (switchStartBlock) {
                switchToSlot((SilentHotbar.currentSlot + 1) % 9)
            }

            sendPacket(C08PacketPlayerBlockPlacement(player.heldItem))
            blockStatus = true
        }

        renderBlocking = true

        CPSCounter.registerClick(CPSCounter.MouseButton.RIGHT)
    }

    /**
     * Stop blocking
     */
    private fun stopBlocking(forceStop: Boolean = false) {
        val player = mc.thePlayer ?: return

        if (!forceStop) {
            if (blockStatus && !mc.thePlayer.isBlocking) {

                when (unblockMode.lowercase()) {
                    "stop" -> {
                        sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    }

                    "switch" -> {
                        switchToSlot((SilentHotbar.currentSlot + 1) % 9)
                    }

                    "empty" -> {
                        player.inventory.firstEmptyStack.takeIf { it in 0..8 }.let {
                            if (it == null) {
                                sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                                return@let
                            }

                            switchToSlot(it)
                        }
                    }
                }

                blockStatus = false
            }
        } else {
            if (blockStatus) {
                sendPacket(C07PacketPlayerDigging(RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }

            blockStatus = false
        }

        renderBlocking = false
    }

    val onPacket = handler<PacketEvent> { event ->
        val player = mc.thePlayer ?: return@handler
        val packet = event.packet
        
        if (removeReduceDmgEnabled && packet is C02PacketUseEntity && packet.action == C02PacketUseEntity.Action.ATTACK) {
            handleEnhancedRemoveReduceDamage(player, packet)
        }

        if (autoBlock == "Off" || !blinkAutoBlock || !blinked) return@handler

        if (player.isDead || player.ticksExisted < 20) {
            BlinkUtils.unblink()
            return@handler
        }

        if (Blink.blinkingSend() || Blink.blinkingReceive()) {
            BlinkUtils.unblink()
            return@handler
        }

        BlinkUtils.blink(packet, event)
    }

    /**
     * Checks if raycast landed on a different object
     *
     * The game requires at least 1 tick of cool-down on raycast object type change (miss, block, entity)
     * We are doing the same thing here but allow more cool-down.
     */
    private fun shouldDelayClick(currentType: MovingObjectPosition.MovingObjectType): Boolean {
        if (!useHitDelay) {
            return false
        }

        val lastAttack = attackTickTimes.lastOrNull()

        return lastAttack != null && lastAttack.first.typeOfHit != currentType && runTimeTicks - lastAttack.second <= hitDelayTicks
    }

    private fun checkIfAimingAtBox(
        targetToCheck: Entity, currentRotation: Rotation, eyes: Vec3, onSuccess: () -> Unit,
        onFail: () -> Unit = { },
    ) {
        if (targetToCheck.hitBox.isVecInside(eyes)) {
            onSuccess()
            return
        }

        // Recreate raycast logic
        val intercept = targetToCheck.hitBox.calculateIntercept(
            eyes, eyes + getVectorForRotation(currentRotation) * range.toDouble()
        )

        if (intercept != null) {
            // Is the entity box raycast vector visible? If not, check through-wall range
            hittable =
                isVisible(intercept.hitVec) || mc.thePlayer.getDistanceToEntityBox(targetToCheck) <= throughWallsRange

            if (hittable) {
                onSuccess()
                return
            }
        }

        onFail()
    }

    private fun switchToSlot(slot: Int) {
        SilentHotbar.selectSlotSilently(this, slot, immediate = true)
        SilentHotbar.resetSlot(this, true)
    }

    private fun shouldPrioritize(): Boolean = when {
        !onScaffold && (Scaffold.handleEvents() && (Scaffold.placeRotation != null || currentRotation != null) || Tower.handleEvents() && Tower.isTowering) -> true

        !onDestroyBlock && (Fucker.handleEvents() && !Fucker.noHit && Fucker.pos != null && !Fucker.isOwnBed || Nuker.handleEvents()) -> true

        activationSlot && SilentHotbar.currentSlot != preferredSlot - 1 -> true

        else -> false
    }

    private fun handleFailedSwings() {
        if (!renderBoxOnSwingFail) return

        val box = AxisAlignedBB(0.0, 0.0, 0.0, 0.05, 0.05, 0.05)

        synchronized(swingFails) {
            val fadeSeconds = renderBoxFadeSeconds * 1000L
            val colorSettings = renderBoxColor

            val renderManager = mc.renderManager

            swingFails.removeAll {
                val timestamp = System.currentTimeMillis() - it.startTime
                val transparency = (0f..255f).lerpWith(1 - (timestamp / fadeSeconds).coerceAtMost(1.0F))

                val offsetBox = box.offset(it.vec3 - renderManager.renderPos)

                RenderUtils.drawAxisAlignedBB(offsetBox, colorSettings.color(a = transparency.roundToInt()))

                timestamp > fadeSeconds
            }
        }
    }

    private fun drawAimPointBox() {
        val player = mc.thePlayer ?: return
        val target = this.target ?: return

        if (!renderAimPointBox) {
            return
        }

        val f = aimPointBoxSize.toDouble()

        val box = AxisAlignedBB(0.0, 0.0, 0.0, f, f, f)

        val renderManager = mc.renderManager

        runWithSimulatedPosition(player, player.interpolatedPosition(player.prevPos)) {
            runWithSimulatedPosition(target, target.interpolatedPosition(target.prevPos)) {
                val rotationVec = player.eyes + getVectorForRotation(
                    serverRotation.lerpWith(currentRotation ?: player.rotation, mc.timer.renderPartialTicks)
                ) * player.getDistanceToEntityBox(target).coerceAtMost(range.toDouble())

                val offSetBox = box.offset(rotationVec - renderManager.renderPos)

                RenderUtils.drawAxisAlignedBB(offSetBox, aimPointBoxColor)
            }
        }
    }

    /**
     * Check if run should be cancelled
     */
    private val cancelRun
        inline get() = mc.thePlayer.isSpectator || !isAlive(mc.thePlayer) || noConsumeAttack == "NoRotation" && isConsumingItem()

    /**
     * Check if [entity] is alive
     */
    private fun isAlive(entity: EntityLivingBase) = entity.isEntityAlive && entity.health > 0

    /**
     * Remove Reduce Damage Implementation
     */
    private fun handleEnhancedRemoveReduceDamage(player: EntityPlayerSP, packet: C02PacketUseEntity) {
        val now = System.currentTimeMillis()

        // Check if we should only work when blocking
        if (removeReduceDmgOnlyOnBlock && !mc.thePlayer.isBlocking) {
            if (removeReduceDmgDebug) {
                chat("§7[RemoveReduceDmg] §cSkipped - Player not blocking")
            }
            return
        }

        // Delay Randomize
        val effectiveDelay = calculateEffectiveDelay()

        // Check delay
        if (now - lastRemoveReduceDmgTime < effectiveDelay) {
            if (removeReduceDmgDebug) {
                chat("§7[RemoveReduceDmg] §cSkipped - Delay not met (${now - lastRemoveReduceDmgTime}ms < ${effectiveDelay}ms)")
            }
            return
        }

        lastRemoveReduceDmgTime = now
        removeReduceDmgAttempts++


        removeReduceDmgHistory.add(now)
        if (removeReduceDmgHistory.size > 10) {
            removeReduceDmgHistory.removeAt(0)
        }

        // Timing Mode
        when (removeReduceDmgTiming.lowercase()) {
            "pre-hit" -> executeRemoveReduceDamagePreHit(player, now)
            "post-hit" -> executeRemoveReduceDamagePostHit(player, now)
            "instant" -> executeRemoveReduceDamageInstant(player, now)
            "delayed" -> executeRemoveReduceDamageDelayed(player, now)
            "smart" -> executeRemoveReduceDamageSmart(player, now)
        }

        lastAttackTime = now

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aExecuted - Mode: $removeReduceDmgMode, Timing: $removeReduceDmgTiming, Attempts: $removeReduceDmgAttempts")
        }
    }

    private fun calculateEffectiveDelay(): Long {
        var delay = removeReduceDmgDelay.toLong()

        // if Enable Randomization
        if (removeReduceDmgRandomization) {
            val randomOffset = (Math.random() * removeReduceDmgRandomRange * 2 - removeReduceDmgRandomRange).toInt()
            delay += randomOffset
        }

        // Delay Success Rate
        if (removeReduceDmgMode == "Smart" && removeReduceDmgAttempts > 5) {
            val successRate = removeReduceDmgSuccess.toDouble() / removeReduceDmgAttempts
            if (successRate < 0.7) {
                adaptiveDelay += 10 // Increase delay if success rate is low
            } else if (successRate > 0.9) {
                adaptiveDelay = maxOf(0, adaptiveDelay - 5) // Decrease delay if success rate is high
            }
            delay += adaptiveDelay
        }

        return maxOf(0, delay)
    }

    private fun executeRemoveReduceDamagePreHit(player: EntityPlayerSP, now: Long) {
        // Sent Before Hit
        if (mc.thePlayer.isBlocking) {
            when (removeReduceDmgMode.lowercase()) {
                "vanilla" -> sendVanillaUnblock(now)
                "ncp" -> sendNCPUnblock(now)
                "aac" -> sendAACUnblock(now)
                "vulcan" -> sendVulcanUnblock(now)
                "matrix" -> sendMatrixUnblock(now)
                "karhu" -> sendKarhuUnblock(now)
                "grim" -> sendGrimUnblock(now)
                "intave" -> sendIntaveUnblock(now)
                "verus" -> sendVerusUnblock(now)
                "negativity" -> sendNegativityUnblock(now)
                "themis" -> sendThemisUnblock(now)
                "spartan" -> sendSpartanUnblock(now)
                "horizon" -> sendHorizonUnblock(now)
                "polar" -> sendPolarUnblock(now)
                "smart" -> sendSmartUnblock(now)
            }
        }
    }

    private fun executeRemoveReduceDamagePostHit(player: EntityPlayerSP, now: Long) {
        // Sent After Hit Small Delay
        if (mc.thePlayer.isBlocking) {
            Thread {
                Thread.sleep(5) // Small delay for post-hit
                when (removeReduceDmgMode.lowercase()) {
                    "vanilla" -> sendVanillaUnblock(now)
                    "ncp" -> sendNCPUnblock(now)
                    "aac" -> sendAACUnblock(now)
                    "vulcan" -> sendVulcanUnblock(now)
                    "matrix" -> sendMatrixUnblock(now)
                    "karhu" -> sendKarhuUnblock(now)
                    "grim" -> sendGrimUnblock(now)
                    "intave" -> sendIntaveUnblock(now)
                    "verus" -> sendVerusUnblock(now)
                    "negativity" -> sendNegativityUnblock(now)
                    "themis" -> sendThemisUnblock(now)
                    "spartan" -> sendSpartanUnblock(now)
                    "horizon" -> sendHorizonUnblock(now)
                    "polar" -> sendPolarUnblock(now)
                    "smart" -> sendSmartUnblock(now)
                }
            }.start()
        }
    }

    private fun executeRemoveReduceDamageInstant(player: EntityPlayerSP, now: Long) {
        // Any Delay
        if (mc.thePlayer.isBlocking) {
            when (removeReduceDmgMode.lowercase()) {
                "vanilla" -> sendVanillaUnblock(now)
                "ncp" -> sendNCPUnblock(now)
                "aac" -> sendAACUnblock(now)
                "vulcan" -> sendVulcanUnblock(now)
                "matrix" -> sendMatrixUnblock(now)
                "karhu" -> sendKarhuUnblock(now)
                "grim" -> sendGrimUnblock(now)
                "intave" -> sendIntaveUnblock(now)
                "verus" -> sendVerusUnblock(now)
                "negativity" -> sendNegativityUnblock(now)
                "themis" -> sendThemisUnblock(now)
                "spartan" -> sendSpartanUnblock(now)
                "horizon" -> sendHorizonUnblock(now)
                "polar" -> sendPolarUnblock(now)
                "smart" -> sendSmartUnblock(now)
            }
        }
    }

    private fun executeRemoveReduceDamageDelayed(player: EntityPlayerSP, now: Long) {
        // Addional Delay
        if (mc.thePlayer.isBlocking) {
            Thread {
                Thread.sleep(removeReduceDmgDelay.toLong() + 20)
                when (removeReduceDmgMode.lowercase()) {
                    "vanilla" -> sendVanillaUnblock(now)
                    "ncp" -> sendNCPUnblock(now)
                    "aac" -> sendAACUnblock(now)
                    "vulcan" -> sendVulcanUnblock(now)
                    "matrix" -> sendMatrixUnblock(now)
                    "karhu" -> sendKarhuUnblock(now)
                    "grim" -> sendGrimUnblock(now)
                    "intave" -> sendIntaveUnblock(now)
                    "verus" -> sendVerusUnblock(now)
                    "negativity" -> sendNegativityUnblock(now)
                    "themis" -> sendThemisUnblock(now)
                    "spartan" -> sendSpartanUnblock(now)
                    "horizon" -> sendHorizonUnblock(now)
                    "polar" -> sendPolarUnblock(now)
                    "smart" -> sendSmartUnblock(now)
                }
            }.start()
        }
    }

    private fun executeRemoveReduceDamageSmart(player: EntityPlayerSP, now: Long) {
        // Smart mode - automatically choose best approach based on server detection
        if (mc.thePlayer.isBlocking) {
            val serverIP = mc.currentServerData?.serverIP?.lowercase() ?: ""

            when {
                serverIP.contains("hypixel") -> sendGrimUnblock(now) // Hypixel
                serverIP.contains("minemen") -> sendAACUnblock(now) // Minemen
                serverIP.contains("pvpland") || serverIP.contains("pvp.land") -> sendVulcanUnblock(now) // PvPLand
                serverIP.contains("cubecraft") -> sendMatrixUnblock(now) // CubeCraft
                serverIP.contains("intave") || serverIP.contains("redesky") -> sendIntaveUnblock(now) // Intave servers
                serverIP.contains("verus") || serverIP.contains("jartex") -> sendVerusUnblock(now) // Verus servers
                serverIP.contains("negativity") -> sendNegativityUnblock(now) // Negativity servers
                serverIP.contains("themis") -> sendThemisUnblock(now) // Themis servers
                serverIP.contains("spartan") -> sendSpartanUnblock(now) // Spartan servers
                serverIP.contains("horizon") -> sendHorizonUnblock(now) // Horizon servers
                serverIP.contains("polar") || serverIP.contains("pika") -> sendPolarUnblock(now) // Polar servers
                serverIP.contains("karhu") -> sendKarhuUnblock(now) // Karhu servers
                else -> {
                    val successRate = if (removeReduceDmgAttempts > 0) removeReduceDmgSuccess.toDouble() / removeReduceDmgAttempts else 0.5
                    when {
                        successRate > 0.9 -> sendVanillaUnblock(now)
                        successRate > 0.8 -> sendNCPUnblock(now)
                        successRate > 0.6 -> sendVulcanUnblock(now)
                        successRate > 0.4 -> sendIntaveUnblock(now)
                        successRate > 0.2 -> sendMatrixUnblock(now)
                        else -> sendGrimUnblock(now)
                    }
                }
            }
        }
    }


    private fun sendVanillaUnblock(now: Long) {
        // Simple vanilla unblock
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
            ), false
        )
        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aVanilla unblock sent")
        }
    }

    private fun sendNCPUnblock(now: Long) {

        when (removeReduceDmgPacketMode.lowercase()) {
            "single" -> {
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }
            "double", "adaptive" -> {

                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos(mc.thePlayer.posX.toInt(), mc.thePlayer.posY.toInt(), mc.thePlayer.posZ.toInt()),
                        EnumFacing.UP
                    ), false
                )
            }
        }
        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aNCP unblock sent (${removeReduceDmgPacketMode})")
        }
    }

    private fun sendAACUnblock(now: Long) {

        val player = mc.thePlayer
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos(player.posX.toInt(), (player.posY - 1).toInt(), player.posZ.toInt()),
                EnumFacing.UP
            ), false
        )


        if (removeReduceDmgPacketMode in arrayOf("double", "triple", "adaptive")) {
            Thread {
                Thread.sleep(2)
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }.start()
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aAAC unblock sent")
        }
    }

    private fun sendVulcanUnblock(now: Long) {

        when (removeReduceDmgPacketMode.lowercase()) {
            "single" -> {
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }
            "burst", "adaptive" -> {

                repeat(3) { i ->
                    Thread {
                        Thread.sleep(i.toLong())
                        sendPacket(
                            C07PacketPlayerDigging(
                                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                                if (i == 0) BlockPos.ORIGIN else BlockPos(i, i, i),
                                EnumFacing.values()[i % EnumFacing.values().size]
                            ), false
                        )
                    }.start()
                }
            }
            else -> sendVanillaUnblock(now)
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aVulcan unblock sent")
        }
    }

    private fun sendMatrixUnblock(now: Long) {

        val player = mc.thePlayer
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt()),
                EnumFacing.UP
            ), false
        )


        if (removeReduceDmgPacketMode in arrayOf("double", "triple", "adaptive")) {
            sendPacket(
                C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                    BlockPos.ORIGIN,
                    EnumFacing.DOWN
                ), false
            )
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aMatrix unblock sent")
        }
    }

    private fun sendKarhuUnblock(now: Long) {

        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
            ), false
        )

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aKarhu unblock sent")
        }
    }

    private fun sendGrimUnblock(now: Long) {

        val player = mc.thePlayer
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos(player.posX.toInt(), player.posY.toInt(), player.posZ.toInt()),
                EnumFacing.DOWN
            ), false
        )

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aGrim unblock sent")
        }
    }

    private fun sendIntaveUnblock(now: Long) {

        val player = mc.thePlayer

        when (removeReduceDmgPacketMode.lowercase()) {
            "single" -> {
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }
            "double", "adaptive" -> {

                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )

                Thread {
                    Thread.sleep(1)
                    sendPacket(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                            BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt()),
                            EnumFacing.UP
                        ), false
                    )
                }.start()
            }
            "triple" -> {

                repeat(3) { i ->
                    Thread {
                        Thread.sleep(i.toLong())
                        sendPacket(
                            C07PacketPlayerDigging(
                                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                                if (i == 0) BlockPos.ORIGIN else BlockPos(player.posX.toInt() + i, player.posY.toInt(), player.posZ.toInt() + i),
                                EnumFacing.values()[i % 2]
                            ), false
                        )
                    }.start()
                }
            }
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aIntave unblock sent (${removeReduceDmgPacketMode})")
        }
    }

    private fun sendVerusUnblock(now: Long) {

        when (removeReduceDmgPacketMode.lowercase()) {
            "burst", "adaptive" -> {

                repeat(2) { i ->
                    sendPacket(
                        C07PacketPlayerDigging(
                            C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                            if (i == 0) BlockPos.ORIGIN else BlockPos(0, -1, 0),
                            if (i == 0) EnumFacing.DOWN else EnumFacing.UP
                        ), false
                    )
                }
            }
            else -> {
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aVerus unblock sent")
        }
    }

    private fun sendNegativityUnblock(now: Long) {
        // Negativity-compatible unblock
        val player = mc.thePlayer
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos(player.posX.toInt(), player.posY.toInt(), player.posZ.toInt()),
                EnumFacing.DOWN
            ), false
        )


        if (removeReduceDmgPacketMode in arrayOf("double", "adaptive")) {
            Thread {
                Thread.sleep(3)
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }.start()
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aNegativity unblock sent")
        }
    }

    private fun sendThemisUnblock(now: Long) {

        val player = mc.thePlayer
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos(player.posX.toInt(), (player.posY - 0.5).toInt(), player.posZ.toInt()),
                EnumFacing.UP
            ), false
        )

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aThemis unblock sent")
        }
    }

    private fun sendSpartanUnblock(now: Long) {

        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.DOWN
            ), false
        )

        if (removeReduceDmgPacketMode == "double") {
            Thread {
                Thread.sleep(2)
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos(0, -1, 0),
                        EnumFacing.UP
                    ), false
                )
            }.start()
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aSpartan unblock sent")
        }
    }

    private fun sendHorizonUnblock(now: Long) {

        val player = mc.thePlayer

        when (removeReduceDmgPacketMode.lowercase()) {
            "burst", "adaptive" -> {

                val positions = arrayOf(
                    BlockPos.ORIGIN,
                    BlockPos(player.posX.toInt(), player.posY.toInt(), player.posZ.toInt()),
                    BlockPos(0, -1, 0)
                )

                positions.forEachIndexed { index, pos ->
                    Thread {
                        Thread.sleep(index.toLong())
                        sendPacket(
                            C07PacketPlayerDigging(
                                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                                pos,
                                EnumFacing.values()[index % EnumFacing.values().size]
                            ), false
                        )
                    }.start()
                }
            }
            else -> {
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos(player.posX.toInt(), player.posY.toInt(), player.posZ.toInt()),
                        EnumFacing.DOWN
                    ), false
                )
            }
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aHorizon unblock sent")
        }
    }

    private fun sendPolarUnblock(now: Long) {

        val player = mc.thePlayer
        sendPacket(
            C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos(player.posX.toInt(), player.posY.toInt() - 1, player.posZ.toInt()),
                EnumFacing.UP
            ), false
        )

        if (removeReduceDmgPacketMode in arrayOf("double", "triple", "adaptive")) {
            Thread {
                Thread.sleep(4)
                sendPacket(
                    C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                    ), false
                )
            }.start()
        }

        blockReleasedAt = now
        removeReduceDmgSuccess++

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aPolar unblock sent")
        }
    }

    private fun sendSmartUnblock(now: Long) {

        val recentSuccess = if (removeReduceDmgHistory.size >= 3) {
            val recentAttempts = removeReduceDmgHistory.takeLast(3)
            val recentSuccessCount = recentAttempts.count { now - it < 5000 } // Success if no issues in last 5 seconds
            recentSuccessCount.toDouble() / recentAttempts.size
        } else 0.5

        when {
            recentSuccess > 0.8 -> sendVanillaUnblock(now)
            recentSuccess > 0.6 -> sendNCPUnblock(now)
            recentSuccess > 0.4 -> sendVulcanUnblock(now)
            else -> sendMatrixUnblock(now)
        }

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aSmart unblock sent (success rate: ${(recentSuccess * 100).toInt()}%)")
        }
    }

    /**
     * Check if player is able to block
     */
    private val canBlock: Boolean
        get() {
            val player = mc.thePlayer ?: return false

            if (target != null && player.heldItem?.item is ItemSword) {
                if (smartAutoBlock) {
                    if (player.isMoving && forceBlock) return false

                    if (checkWeapon && target?.heldItem?.item !is ItemSword && target?.heldItem?.item !is ItemAxe) return false

                    if (player.hurtTime > maxOwnHurtTime) return false

                    val rotationToPlayer = toRotation(player.hitBox.center, true, target!!)

                    if (rotationDifference(rotationToPlayer, target!!.rotation) > maxDirectionDiff) return false

                    if (target!!.swingProgressInt > maxSwingProgress) return false

                    if (target!!.getDistanceToEntityBox(player) > blockRange) return false
                }

                if (player.getDistanceToEntityBox(target!!) > blockMaxRange) return false

                return true
            }

            return false
        }

    /**
     * Range
     */
    private val maxRange
        get() = max(range + scanRange, throughWallsRange)

    private fun getRange(entity: Entity) =
        (if (mc.thePlayer.getDistanceToEntityBox(entity) >= throughWallsRange) range + scanRange else throughWallsRange) - if (mc.thePlayer.isSprinting) rangeSprintReduction else 0F

    /**
     * Reset RemoveReduceDamage statistics
     */
    private fun resetRemoveReduceDamageStats() {
        removeReduceDmgAttempts = 0
        removeReduceDmgSuccess = 0
        removeReduceDmgFails = 0
        removeReduceDmgHistory.clear()
        adaptiveDelay = 0
        lastRemoveReduceDmgTime = 0L
        lastServerResponse = 0L

        if (removeReduceDmgDebug) {
            chat("§7[RemoveReduceDmg] §aStatistics reset")
        }
    }

    /**
     * Get RemoveReduceDamage statistics
     */
    fun getRemoveReduceDamageStats(): String {
        val successRate = if (removeReduceDmgAttempts > 0) {
            (removeReduceDmgSuccess.toDouble() / removeReduceDmgAttempts * 100).toInt()
        } else 0

        return "§7[RemoveReduceDmg] §fAttempts: §a$removeReduceDmgAttempts §f| Success: §a$removeReduceDmgSuccess §f| Rate: §a$successRate%"
    }

    /**
     * HUD Tag
     */
    override val tag
        get() = targetMode

    /**
     * Enhanced onEnable with RemoveReduceDamage reset
     */
    override fun onEnable() {
        resetRemoveReduceDamageStats()
        super.onEnable()
    }

    val isBlockingChestAura
        get() = handleEvents() && target != null
}

data class SwingFailData(val vec3: Vec3, val startTime: Long)
