package com.kirbornu.gimpanum.magic.cast;

import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.core.Compilation;
import com.kirbornu.gimpanum.magic.core.FieldSet;
import com.kirbornu.gimpanum.magic.core.Program;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import com.kirbornu.gimpanum.magic.spell.SpellSession;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Каст: от рунописей до сущности-заклинания в мире.
 *
 * <p>Всё на сервере. Порядок проверок — от дешёвых и не трогающих игрока к
 * списанию: пустое заклинание и перезарядка ничего не стоят, а цена
 * списывается последней, когда уже ясно, что заклинание состоится.
 */
public final class SpellCaster {

    private SpellCaster() {
    }

    /** Почему не вышло — для сообщения игроку. */
    public enum Failure {
        EMPTY,
        COOLDOWN,
        NOT_READY,
        PRICE,
        LIMIT
    }

    /**
     * @param catalyst предмет во второй руке
     * @return пусто — заклинание сотворено, иначе причина отказа
     */
    public static Optional<Failure> cast(ServerPlayer player, List<int[]> runescripts, ItemStack catalyst) {
        if (runescripts.isEmpty()) {
            return Optional.of(Failure.EMPTY);
        }
        if (!MagicConfig.ready()) {
            return Optional.of(Failure.NOT_READY);
        }
        if (!SpellCooldown.ready(player)) {
            return Optional.of(Failure.COOLDOWN);
        }
        MagicConfig.Loaded config = MagicConfig.get();
        Program<Arcane> program = compile(runescripts, catalyst, config).program();
        int runes = runescripts.stream().mapToInt(word -> word.length).sum();
        if (!SpellPayment.current().tryPay(player, runes, config.settings())) {
            return Optional.of(Failure.PRICE);
        }
        SpellCooldown.start(player, config.settings().cooldownSeconds());

        ServerLevel level = player.serverLevel();
        SpellSession session = new SpellSession(level, player, player.getLookAngle(), config);
        if (SpellEntity.cast(session, player.getEyePosition(), program) == null) {
            return Optional.of(Failure.LIMIT);
        }
        return Optional.empty();
    }

    /** Разбор с арканами из действующих настроек — общий для каста и отладочной команды. */
    public static Compiled compile(List<int[]> runescripts, ItemStack catalyst, MagicConfig.Loaded config) {
        FieldSet fields = config.fieldsFor(catalyst);
        Compilation compilation = Compilation.of(runescripts, config.settings().turtle(), fields,
                id -> config.arcane(id).order());
        return new Compiled(fields, compilation, mapProgram(compilation.program(), config));
    }

    /** Разбор вместе с программой из готовых арканов. */
    public record Compiled(FieldSet fields, Compilation compilation, Program<Arcane> program) {
    }

    private static Program<Arcane> mapProgram(Program<String> program, MagicConfig.Loaded config) {
        return new Program<>(program.steps().stream().map(step -> new Program.Step<>(
                config.arcane(step.root()),
                step.prefixes().stream().map(config::arcane).toList(),
                step.suffixes().stream().map(config::arcane).toList(),
                step.interfixAfter().map(config::arcane))).toList());
    }

    public static Component message(Failure failure) {
        return Component.translatable("gimpanum.magic.fail." + failure.name().toLowerCase(java.util.Locale.ROOT));
    }
}
