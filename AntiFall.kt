/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package master.koitoyuu.modules.move

import master.koitoyuu.utils.MoveUtils
import master.koitoyuu.utils.PacketUtils
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleInfo
import net.ccbluex.liquidbounce.utils.block.BlockUtils
import net.ccbluex.liquidbounce.utils.misc.FallingPlayer
import net.ccbluex.liquidbounce.utils.misc.RandomUtils
import net.ccbluex.liquidbounce.value.BoolValue
import net.ccbluex.liquidbounce.value.FloatValue
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value.ListValue
import net.minecraft.block.BlockAir
import net.minecraft.network.Packet
import net.minecraft.network.play.INetHandlerPlayServer
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.util.BlockPos
import oh.yalan.NativeClass
import java.util.*
import kotlin.math.abs

@NativeClass
@ModuleInfo(name = "AntiFall",fakeName = "Anti Fall", description = "Automatically setbacks you after falling a certain distance.", category = ModuleCategory.MOVEMENT)
class AntiFall : Module() {
    private val modeValue = ListValue("Mode", arrayOf("Vanilla","Blink","PacketEdit","Jump","PullBack","IDK","MinelandTest"), "Vanilla")
    private val maxFallDistance = IntegerValue("MaxFallDistance", 10, 2, 255)
    private val maxDistanceWithoutGround = FloatValue("MaxDistanceToSetback", 2.5f, 1f, 30f)
    private val onlyVoid = BoolValue("OnlyVoid",true)

    private val c03lag = LinkedList<Packet<INetHandlerPlayServer>>()
    private var detectedLocation: BlockPos? = null
    private var lastFound = 0F
    private var prevX = 0.0
    private var prevY = 0.0
    private var prevZ = 0.0

    override fun onDisable() {
        if (modeValue.get().equals("blink")) {
            for (packet in c03lag) {
                PacketUtils.sendPacketNoEvent(packet)
            }
            c03lag.clear()
        }
        prevX = 0.0
        prevY = 0.0
        prevZ = 0.0
    }

    @EventTarget
    fun onPacket(event:PacketEvent) {
        val packet = event.packet
        if (mc.thePlayer == null) return
        if (mc.thePlayer.onGround && BlockUtils.getBlock(BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0,
                mc.thePlayer.posZ)) !is BlockAir) {
            prevX = mc.thePlayer.prevPosX
            prevY = mc.thePlayer.prevPosY
            prevZ = mc.thePlayer.prevPosZ
        }
        if (packet is C03PacketPlayer) {
            if (packet.onGround && c03lag.size <= 20) {
                repeat(20) {
                    c03lag.add(packet)}
            }
            if (c03lag.size > 20)
                c03lag.clear()
        }
        if (onlyVoid.get() && !MoveUtils.inVoid()) return
        when (modeValue.get()) {
            "MinelandTest" -> {
                if (packet is C03PacketPlayer) packet.onGround = true
                if (mc.thePlayer.fallDistance - lastFound > maxDistanceWithoutGround.get()) {
                    PacketUtils.sendPacketNoEvent(C03PacketPlayer.C04PacketPlayerPosition(prevX,prevY,prevZ,true))
                    mc.thePlayer.setPosition(prevX,prevY,prevZ)
                }
            }
            "Blink" -> {
                if (packet is C03PacketPlayer || packet is C0BPacketEntityAction) {
                    event.cancelEvent()
                }
                if (mc.thePlayer.fallDistance - lastFound > maxDistanceWithoutGround.get()) {
                    for (packets in c03lag) {
                        PacketUtils.sendPacketNoEvent(packets)
                    }
                    mc.thePlayer.setPosition(prevX,prevY,prevZ)
                    c03lag.clear()
                }
            }
            "PacketEdit" -> {
                if (mc.thePlayer.fallDistance - lastFound > maxDistanceWithoutGround.get())
                    if (packet is C03PacketPlayer) {
                        packet.x = prevX
                        packet.y = prevY
                        packet.z = prevZ
                        packet.isMoving = false
                        packet.onGround = true
                        mc.thePlayer.setPositionAndUpdate(prevX, prevY, prevZ)
                        mc.thePlayer.fallDistance = 0f
                        mc.thePlayer.motionY = 0.0
                    }
            }
        }
    }
    @EventTarget
    fun onUpdate(e: UpdateEvent) {
        detectedLocation = null

        if (mc.thePlayer.onGround && BlockUtils.getBlock(BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 1.0,
                        mc.thePlayer.posZ)) !is BlockAir) {
            prevX = mc.thePlayer.prevPosX
            prevY = mc.thePlayer.prevPosY
            prevZ = mc.thePlayer.prevPosZ
        }

        if (!mc.thePlayer.onGround && !mc.thePlayer.isOnLadder && !mc.thePlayer.isInWater) {
            val fallingPlayer = FallingPlayer(
                    mc.thePlayer.posX,
                    mc.thePlayer.posY,
                    mc.thePlayer.posZ,
                    mc.thePlayer.motionX,
                    mc.thePlayer.motionY,
                    mc.thePlayer.motionZ,
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.moveStrafing,
                    mc.thePlayer.moveForward
            )

            detectedLocation = fallingPlayer.findCollision(60)?.pos

            if (detectedLocation != null && abs(mc.thePlayer.posY - detectedLocation!!.y) +
                    mc.thePlayer.fallDistance <= maxFallDistance.get()) {
                lastFound = mc.thePlayer.fallDistance
            }

            if (!MoveUtils.inVoid() && onlyVoid.get()) return
            if (mc.thePlayer.fallDistance - lastFound > maxDistanceWithoutGround.get()) {
                val mode = modeValue.get()

                when (mode.toLowerCase()) {
                    "vanilla" -> {
                        mc.thePlayer.setPositionAndUpdate(prevX, prevY, prevZ)
                        mc.thePlayer.fallDistance = 0F
                        mc.thePlayer.motionY = 0.0
                    }
                    "jump" -> {
                        mc.thePlayer.jump()
                        mc.thePlayer.fallDistance = 0f
                    }
                    "pullback" -> {
                        mc.netHandler.addToSendQueue(C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX,mc.thePlayer.posY + 11f,mc.thePlayer.posZ,true))
                    }
                    "idk" -> {
                        mc.thePlayer.motionY = 0.0
                        mc.netHandler.addToSendQueue(C03PacketPlayer.C05PacketPlayerLook(RandomUtils.nextFloat(-180f,180f),RandomUtils.nextFloat(-90f,90f),true))
                    }
                }
            }
        }
    }

    override val tag: String
        get() = modeValue.get()
}