package com.kirbornu.gimpanum.capture;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.core.CoreFakePlayer;
import com.kirbornu.gimpanum.integration.ClaimsSupport;
import com.kirbornu.gimpanum.integration.FtbTeamsSupport;
import com.kirbornu.gimpanum.integration.TeamOwner;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.kirbornu.gimpanum.sublevel.SubLevelSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Владелец Контрольной точки и всё, что из него следует: клейм чанка и цвет
 * частиц.
 */
public class CapturePointBlockEntity extends BlockEntity {

    private static final String KEY_TEAM = "GimpanumPointTeam";
    private static final String KEY_OWNER = "GimpanumPointOwner";
    private static final String KEY_COLOR = "GimpanumPointColor";

    /** Морская волна — цвет ничьей точки. */
    private static final int NEUTRAL_COLOR = 0x00CED1;

    /** Дважды в секунду — заметно глазу и не грузит сеть. */
    private static final int PARTICLE_INTERVAL_TICKS = 10;

    /**
     * Раз в пять секунд точка проверяет, что клейм всё ещё её.
     *
     * <p>Клейм могли снять командой OPAC или перехватить чужим захватом чанка, и
     * тогда точка обязана вернуть его себе — иначе она перестала бы что-либо
     * значить, оставшись просто блоком.
     */
    private static final int UPKEEP_INTERVAL_TICKS = 100;

    /** {@code null} — точка ничья. */
    private TeamOwner owner;

    /**
     * Цвет частиц, посчитанный заранее.
     *
     * <p>Держится отдельно от владельца, потому что читается из чужого мода:
     * дёргать OPAC каждые полсекунды ради цвета незачем, а на время его сбоя
     * точка должна остаться того же цвета, что и была.
     */
    private int color = NEUTRAL_COLOR;

    private int particleTimer;
    private int upkeepTimer;

    /**
     * Клейм ещё не выставлен.
     *
     * <p>Ставить его в {@code onLoad} нельзя: чанк в этот момент ещё грузится, а
     * данные OPAC могут быть не готовы. Откладываем до первого тика — та же
     * причина, по которой Ядро откладывает правку своего состояния блока.
     */
    private boolean claimPending = true;

    public CapturePointBlockEntity(BlockPos pos, BlockState state) {
        super(GimpanumContent.CAPTURE_POINT_BLOCK_ENTITY.get(), pos, state);
    }

    public Optional<TeamOwner> owner() {
        return Optional.ofNullable(owner);
    }

    // --- Захват --------------------------------------------------------------

    /**
     * Закрепляет точку за командой ударившего.
     *
     * <p>Повторный удар своей же командой ничего не делает: иначе каждый удар
     * рассылал бы всем объявление о «захвате».
     */
    public void captureBy(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Optional<TeamOwner> captor = FtbTeamsSupport.ownerOf(serverPlayer);
        if (captor.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gimpanum.point.no_team")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        TeamOwner next = captor.get();
        if (owner != null && owner.ownerId().equals(next.ownerId())) {
            return;
        }

        owner = next;
        refreshColor();
        setChanged();
        sendToClients();
        applyClaim(serverLevel);

        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("gimpanum.point.captured",
                        next.teamName(), player.getName()).withStyle(ChatFormatting.GOLD), false);
    }

    /** Возвращает точку в ничьё состояние. */
    public void releaseToNeutral() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        owner = null;
        color = NEUTRAL_COLOR;
        setChanged();
        sendToClients();
        applyClaim(serverLevel);
    }

    /** Снос точки уносит с собой и клейм. */
    public void onDestroyed() {
        if (level instanceof ServerLevel serverLevel) {
            ClaimsSupport.unclaim(serverLevel, worldPosition);
        }
    }

    // --- Клейм ---------------------------------------------------------------

    /**
     * Переводит чанк точки на текущего владельца, а ничью точку — на фиктивного
     * игрока мода.
     *
     * <p>Фиктивный игрок здесь тот же, от чьего имени взрывается Ядро: одна
     * личность на весь мод — значит и в доверенные её вносить один раз.
     */
    private void applyClaim(ServerLevel serverLevel) {
        // Точке на физической конструкции клейм не нужен: чанк там служебный, и
        // клеймить его бессмысленно. Попасть туда точка не должна вовсе, но
        // проверка дешёвая, а последствия ошибки — чужой клейм в служебном
        // регионе карты.
        if (SubLevelSupport.isOnSubLevel(serverLevel, worldPosition)) {
            Gimpanum.LOGGER.warn("Контрольная точка в {} оказалась на физической конструкции; "
                    + "клейм не выставлен", worldPosition.toShortString());
            return;
        }

        UUID claimOwner = owner != null ? owner.ownerId() : CoreFakePlayer.UUID_VALUE;
        if (owner == null) {
            // Ничья точка подписывается сама: на карте видно, что квадрат занят
            // не игроком, а механикой.
            ClaimsSupport.setAppearance(serverLevel.getServer(), CoreFakePlayer.UUID_VALUE,
                    Component.translatable("gimpanum.point.claim_name").getString(), NEUTRAL_COLOR);
        }
        ClaimsSupport.claim(serverLevel, worldPosition, claimOwner);
    }

    /**
     * Цвет клеймов владельца — им же красятся частицы.
     *
     * <p>Если OPAC цвет не отдал, берётся цвет самой команды FTB: он всё равно
     * ближе к ожиданиям игрока, чем нейтральная волна.
     */
    private void refreshColor() {
        if (owner == null) {
            color = NEUTRAL_COLOR;
            return;
        }
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null) {
            return;
        }
        OptionalInt claimColor = ClaimsSupport.claimColor(server, owner.ownerId());
        color = claimColor.orElse(owner.fallbackColor());
    }

    // --- Тик -----------------------------------------------------------------

    public void serverTick() {
        try {
            tickInternal();
        } catch (Throwable t) {
            // Тик идёт в общем цикле блок-сущностей: исключение отсюда остановило
            // бы тик всего чанка.
            Gimpanum.LOGGER.error("Тик Контрольной точки в {} прерван ошибкой",
                    worldPosition.toShortString(), t);
        }
    }

    private void tickInternal() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (claimPending) {
            claimPending = false;
            refreshColor();
            applyClaim(serverLevel);
        }
        upkeep(serverLevel);
        emitParticles(serverLevel);
    }

    /** Возвращает себе клейм, если его сняли или перехватили помимо точки. */
    private void upkeep(ServerLevel serverLevel) {
        if (++upkeepTimer < UPKEEP_INTERVAL_TICKS) {
            return;
        }
        upkeepTimer = 0;

        UUID expected = owner != null ? owner.ownerId() : CoreFakePlayer.UUID_VALUE;
        UUID actual = ClaimsSupport.ownerAt(serverLevel, worldPosition);
        if (!expected.equals(actual)) {
            applyClaim(serverLevel);
        }
        refreshColor();
    }

    /**
     * Частицы цвета владельца.
     *
     * <p>Рассылает сервер, а не {@code animateTick} на клиенте: цвет живёт в
     * блок-сущности, и клиентскому коду его пришлось бы знать отдельно.
     */
    private void emitParticles(ServerLevel serverLevel) {
        if (++particleTimer < PARTICLE_INTERVAL_TICKS) {
            return;
        }
        particleTimer = 0;

        DustParticleOptions particle = new DustParticleOptions(
                new Vector3f(((color >> 16) & 0xFF) / 255.0F,
                        ((color >> 8) & 0xFF) / 255.0F,
                        (color & 0xFF) / 255.0F),
                1.0F);
        Vec3 worldPos = SubLevelSupport.worldCenter(serverLevel, worldPosition);
        // sendParticles сам отсеивает игроков дальше 32 блоков.
        serverLevel.sendParticles(particle, worldPos.x, worldPos.y, worldPos.z,
                4, 0.35, 0.35, 0.35, 0.0);
    }

    // --- Синхронизация и хранение --------------------------------------------

    private void sendToClients() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(KEY_OWNER) && tag.contains(KEY_TEAM)) {
            owner = new TeamOwner(tag.getString(KEY_TEAM), tag.getUUID(KEY_OWNER),
                    tag.getInt(KEY_COLOR));
        } else {
            owner = null;
        }
        color = tag.contains(KEY_COLOR) ? tag.getInt(KEY_COLOR) : NEUTRAL_COLOR;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putString(KEY_TEAM, owner.teamName());
            tag.putUUID(KEY_OWNER, owner.ownerId());
        }
        tag.putInt(KEY_COLOR, color);
    }
}
