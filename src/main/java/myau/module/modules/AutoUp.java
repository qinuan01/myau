package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;

public class AutoUp extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private enum State {
        IDLE,
        MINING,
        PLACING
    }

    private State state = State.IDLE;

    private BlockPos miningPos = null;
    private float breakProgress = 0.0F;

    public AutoUp() {
        super("AutoUp", false);
    }

    /* ================= 工具方法 ================= */

    private BlockPos getHeadPos() {
        return new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.height),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
    }

    private BlockPos getBelowPos() {
        return new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY - 1),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
    }

    /* ================= Update ================= */

    @EventTarget
    public void onUpdate(UpdateEvent event) {

        // ★ 关键：模块 & PRE 判断
        if (!this.isEnabled() || event.getType() != EventType.PRE)
            return;

        if (mc.thePlayer == null || mc.theWorld == null)
            return;

        // 静默抬头
        event.setRotation(event.getYaw(), -90.0F, 3);

        switch (state) {
            case IDLE:
                handleIdle();
                break;

            case MINING:
                handleMining();
                break;

            case PLACING:
                handlePlacing();
                break;
        }
    }

    /* ================= 状态逻辑 ================= */

    private void handleIdle() {
        BlockPos head = getHeadPos();

        if (!BlockUtil.isReplaceable(head)) {
            startMining(head);
        } else {
            state = State.PLACING;
        }
    }

    /* ================= 挖方块（参考 BedNuker） ================= */

    private void startMining(BlockPos pos) {
        this.miningPos = pos;
        this.breakProgress = 0.0F;

        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                pos,
                EnumFacing.DOWN
        ));

        state = State.MINING;
    }

    private void handleMining() {
        if (miningPos == null || BlockUtil.isReplaceable(miningPos)) {
            state = State.PLACING;
            return;
        }

        // 模拟真实挖掘进度（可根据需要调）
        breakProgress += 0.25F;

        mc.effectRenderer.addBlockHitEffects(miningPos, EnumFacing.DOWN);

        if (breakProgress >= 1.0F) {
            PacketUtil.sendPacket(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                    miningPos,
                    EnumFacing.DOWN
            ));
            state = State.PLACING;
        }
    }

    /* ================= 垫方块（参考 Scaffold） ================= */

    private void handlePlacing() {

        if (!mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            return;
        }

        BlockPos below = getBelowPos();

        if (!BlockUtil.isReplaceable(below)) {
            state = State.IDLE;
            return;
        }

        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == EnumFacing.DOWN)
                continue;

            BlockPos neighbor = below.offset(facing);

            if (!BlockUtil.isReplaceable(neighbor)) {

                Vec3 hitVec = BlockUtil.getHitVec(
                        neighbor,
                        facing.getOpposite(),
                        mc.thePlayer.rotationYaw,
                        mc.thePlayer.rotationPitch
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

                state = State.IDLE;
                return;
            }
        }
    }

    /* ================= 禁用 ================= */

    @Override
    public void onDisabled() {
        state = State.IDLE;
        miningPos = null;
        breakProgress = 0.0F;
    }
}
