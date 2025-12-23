package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;

public class AutoUp extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /* ================= 状态 ================= */

    private enum State {
        IDLE,
        MINING,
        PLACING
    }

    private State state = State.IDLE;

    private BlockPos miningPos = null;
    private float breakProgress = 0.0F;

    /* ================= 物品切换（对齐 Scaffold / AutoTool） ================= */

    private int lastSlot = -1;
    private int blockCount = -1;

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

    private void saveSlot() {
        if (lastSlot == -1) {
            lastSlot = mc.thePlayer.inventory.currentItem;
        }
    }

    private void restoreSlot() {
        if (lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = lastSlot;
            lastSlot = -1;
        }
    }

    /* ================= 切换到工具（AutoTool 同款） ================= */

    private void switchToTool(BlockPos pos) {
        int slot = ItemUtil.findInventorySlot(
                mc.thePlayer.inventory.currentItem,
                mc.theWorld.getBlockState(pos).getBlock()
        );

        if (slot != mc.thePlayer.inventory.currentItem) {
            saveSlot();
            mc.thePlayer.inventory.currentItem = slot;
        }
    }

    /* ================= 切换到方块（Scaffold 同款） ================= */

    private boolean switchToBlock() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
        blockCount = Math.min(blockCount, count);

        if (blockCount <= 0) {
            int slot = mc.thePlayer.inventory.currentItem;
            if (blockCount == 0) {
                slot--;
            }
            for (int i = slot; i > slot - 9; i--) {
                int hotbarSlot = (i % 9 + 9) % 9;
                ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                if (ItemUtil.isBlock(candidate)) {
                    saveSlot();
                    mc.thePlayer.inventory.currentItem = hotbarSlot;
                    blockCount = candidate.stackSize;
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /* ================= Update ================= */

    @EventTarget
    public void onUpdate(UpdateEvent event) {

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

    /* ================= 挖方块 ================= */

    private void startMining(BlockPos pos) {
        miningPos = pos;
        breakProgress = 0.0F;

        switchToTool(pos);

        PacketUtil.sendPacket(new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.START_DESTROY_BLOCK,
                pos,
                EnumFacing.DOWN
        ));

        state = State.MINING;
    }

    private void handleMining() {
        if (miningPos == null || BlockUtil.isReplaceable(miningPos)) {
            restoreSlot();
            state = State.PLACING;
            return;
        }

        breakProgress += 0.25F;
        mc.effectRenderer.addBlockHitEffects(miningPos, EnumFacing.DOWN);

        if (breakProgress >= 1.0F) {
            PacketUtil.sendPacket(new C07PacketPlayerDigging(
                    C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK,
                    miningPos,
                    EnumFacing.DOWN
            ));
            restoreSlot();
            state = State.PLACING;
        }
    }

    /* ================= 垫方块 ================= */

    private void handlePlacing() {

        if (!mc.thePlayer.onGround) {
            mc.thePlayer.jump();
            return;
        }

        BlockPos below = getBelowPos();

        if (!BlockUtil.isReplaceable(below)) {
            restoreSlot();
            state = State.IDLE;
            return;
        }

        if (!switchToBlock()) {
            restoreSlot();
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

                restoreSlot();
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
        lastSlot = -1;
        blockCount = -1;
    }
}
