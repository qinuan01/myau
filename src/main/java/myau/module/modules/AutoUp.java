package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class AutoUp extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public AutoUp() {
        super("AutoUp", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        BlockPos playerPos = mc.thePlayer.getPosition();
        BlockPos headPos = playerPos.up(2);
        BlockPos downPos = playerPos.down();

        // ===== 1. 头上有方块 → 挖掉 =====
        if (!mc.theWorld.isAirBlock(headPos)) {
            faceBlock(headPos);

            mc.playerController.onPlayerDamageBlock(headPos, EnumFacing.DOWN);
            mc.thePlayer.swingItem();
            return;
        }

        // ===== 2. 脚下是空气 → 向下垫 =====
        if (mc.theWorld.isAirBlock(downPos)) {

            // 跳一下
            if (mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }

            placeBlock(downPos);
        }
    }

    // 转头看向方块（1.8 写法）
    private void faceBlock(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 hit = new Vec3(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        );

        double dx = hit.xCoord - eyes.xCoord;
        double dy = hit.yCoord - eyes.yCoord;
        double dz = hit.zCoord - eyes.zCoord;

        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);

        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
    }

    // 放置脚下方块
    private void placeBlock(BlockPos pos) {
    
        if (mc.thePlayer.getHeldItem() == null) return;
    
        Vec3 hitVec = new Vec3(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );
    
        mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                pos,
                EnumFacing.UP,
                hitVec
        );
    
        mc.thePlayer.swingItem();
    }
}
