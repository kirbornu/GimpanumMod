package com.kirbornu.gimpanum.magic.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.behavior.Behavior;
import com.kirbornu.gimpanum.magic.behavior.BehaviorLibrary;
import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.magic.config.ArcaneDef;
import com.kirbornu.gimpanum.magic.config.BehaviorSpec;
import com.kirbornu.gimpanum.magic.config.ConfigErrors;
import com.kirbornu.gimpanum.magic.config.Json;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.core.Grammar;
import com.kirbornu.gimpanum.magic.core.Program;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.ArcaneParticles;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import com.kirbornu.gimpanum.magic.spell.SpellSession;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Помощники игровых тестов магии.
 *
 * <p>Кастер — {@link FakePlayer}: настоящий игрок в тесте подключался бы к
 * серверу, и мод слал бы ему пакеты, которые тестовое соединение не
 * принимает. У поддельного игрока свои особенности: он неуязвим и не тикает,
 * поэтому урон и всё, что идёт по тикам (эффекты, движение), проверяется на
 * мобах.
 *
 * <p>Пол тестовых площадок — на относительной высоте 1, воздух — с 2.
 */
final class MagicTestKit {

    /** Индексы рун демонстрационных рунописей: огонь, вода, исцеление, свет. */
    static final int[] FIRE = {6, 6, 10, 3, 1};
    static final int[] WATER = {4, 0};
    static final int[] HEAL = {3};
    static final int[] LIGHT = {0};

    private MagicTestKit() {
    }

    /** Кастер в мире, на (4, 2, 4) площадки {@code magictest}, смотрит на +X. */
    static ServerPlayer player(GameTestHelper h) {
        return player(h, true, new BlockPos(4, 2, 4));
    }

    /**
     * @param inLevel добавлять ли кастера в мир. Не добавленный не станет
     *                целью по умолчанию — ею станет ближайший моб.
     */
    static ServerPlayer player(GameTestHelper h, boolean inLevel, BlockPos at) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "magic-test"));
        BlockPos abs = h.absolutePos(at);
        // yaw −90 — взгляд на +X.
        p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, -90F, 0F);
        p.setYHeadRot(-90F);
        // Хрусталь на оплату: без него каст по-настоящему (через SpellCaster) не пройдёт.
        p.getInventory().setItem(20, new ItemStack(GimpanumContent.CRYSTAL_SHARD.get(), 64));
        if (inLevel) {
            h.getLevel().addNewPlayer(p);
        }
        return p;
    }

    /** Каст демонстрационных арканов из настроек — тем же путём, что и табличкой. */
    static Optional<SpellCaster.Failure> cast(ServerPlayer p, int[]... words) {
        return SpellCaster.cast(p, List.of(words), ItemStack.EMPTY);
    }

    /**
     * Аркан прямо из JSON ролей, минуя файлы настроек.
     *
     * @param roles объект вида {@code {"root": [...], "prefix": [...]}}
     */
    static Arcane arcane(String id, int order, String roles) {
        JsonObject json = JsonParser.parseString(roles).getAsJsonObject();
        ConfigErrors errors = new ConfigErrors();
        Map<Role, List<Behavior>> behaviors = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            List<Behavior> list = new ArrayList<>();
            JsonArray array = json.has(role.key()) ? json.getAsJsonArray(role.key()) : new JsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                Json params = new Json(object, id + "." + role.key() + "[" + i + "]", errors);
                Behavior behavior = BehaviorLibrary.compile(new BehaviorSpec(object.get("type").getAsString(), params),
                        role);
                if (behavior != null) {
                    list.add(behavior);
                }
            }
            behaviors.put(role, List.copyOf(list));
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors.messages()));
        }
        return new Arcane(new ArcaneDef(id, id, order, Map.of(), null, null), behaviors, ArcaneParticles.NONE);
    }

    /** Ошибки разбора одного поведения в роли — для проверок валидации. */
    static List<String> errors(Role role, String behavior) {
        JsonObject object = JsonParser.parseString(behavior).getAsJsonObject();
        ConfigErrors errors = new ConfigErrors();
        JsonElement type = object.get("type");
        BehaviorLibrary.compile(new BehaviorSpec(type.getAsString(), new Json(object, "b", errors)), role);
        return errors.messages();
    }

    /**
     * Каст программы из готовых арканов: роли определяет грамматика по их номерам.
     *
     * @return сущность-заклинание или {@code null}
     */
    static SpellEntity castArcanes(ServerPlayer p, Arcane... arcanes) {
        return castFrom(p, arcanes);
    }

    /**
     * Каст от любой живой сущности. Нужен там, где поддельный игрок не годится:
     * его сетевой обработчик глушит телепорт, и он не сдвинется с места.
     */
    static SpellEntity castFrom(LivingEntity caster, Arcane... arcanes) {
        int[] orders = Arrays.stream(arcanes).mapToInt(Arcane::order).toArray();
        Role[] roles = Grammar.roles(orders);
        Program<Arcane> program = Program.link(List.of(arcanes), roles, Grammar.collapsed(roles));
        SpellSession session = new SpellSession((net.minecraft.server.level.ServerLevel) caster.level(), caster,
                caster.getLookAngle(), MagicConfig.get());
        return SpellEntity.cast(session, caster.getEyePosition(), program);
    }

    /** Моб без ИИ: стоит на месте, но и сам не двигается — для проверок, где движение не важно. */
    static <T extends Mob> T still(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = h.spawn(type, at);
        mob.setNoAi(true);
        return mob;
    }

    static void log(String message) {
        Gimpanum.LOGGER.info("[MAGICTEST] {}", message);
    }
}
