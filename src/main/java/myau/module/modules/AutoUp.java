package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;

public class AutoUp extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();

    private boolean isMining = false;
    private int mineTicks = 0;

    public AutoUp() {
        super("AutoUp", false);
    }

    private BlockPos getHeadPos() {
        return new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.height + 0.1),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
    }

    private boolean hasBlockAbove() {
        return !BlockUtil.isReplaceable(getHeadPos());
    }

    private void lookUpSilent(UpdateEvent event) {
        event.setRotation(event.getYaw(), -90.0F, 3);
    }

    private void mineHead(BlockPos pos) {
        if (!isMining) {
            isMining = true;
            mineTicks = 0;
            PacketUtil.sendPacket(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                    pos,
                    EnumFacing.DOWN
            ));
        }
    }
    
    private void placeBelow() {
        BlockPos below = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY - 1),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
    
        if (!BlockUtil.isReplaceable(below)) return;
    
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == EnumFacing.DOWN) continue;
    
            BlockPos neighbor = below.offset(facing);
            if (!BlockUtil.isReplaceable(neighbor)) {
    
                float yaw = mc.thePlayer.rotationYaw;
                float pitch = mc.thePlayer.rotationPitch;
    
                Vec3 hitVec = BlockUtil.getHitVec(
                        neighbor,
                        facing.getOpposite(),
                        yaw,
                        pitch
                );
    
                if (mc.playerController.onPlayerRightClick(
                        mc.thePlayer,
                        mc.theWorld,
                        mc.thePlayer.getHeldItem(),
                        neighbor,
                        facing.getOpposite(),
                        hitVec
                )) {
                    mc.thePlayer.swingItem();
                }
                return;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!timer.hasTimeElapsed(150)) return;

        timer.reset();
        lookUpSilent(event);

        if (hasBlockAbove()) {
            mineHead(getHeadPos());
        } else {
            if (mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
            placeBelow();
        }

        if (isMining) {
            mineTicks++;
            if (mineTicks >= 4) {
                PacketUtil.sendPacket(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                        getHeadPos(),
                        EnumFacing.DOWN
                ));
                isMining = false;
            }
        }
    }

    @Override
    public void onDisabled() {
        isMining = false;
        mineTicks = 0;
    }
}
