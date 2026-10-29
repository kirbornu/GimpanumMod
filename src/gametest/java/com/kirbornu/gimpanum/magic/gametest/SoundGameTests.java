package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.FIRE;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.HEAL;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.cast;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.player;

/** Звуки заклинаний: какой аркан, в какой роли и когда звучит. */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class SoundGameTests {

    private record Heard(String sound, Vec3 at) {
    }

    private static final Queue<Heard> HEARD = new ConcurrentLinkedQueue<>();

    static {
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getSound() != null) {
                HEARD.add(new Heard(event.getSound().getRegisteredName(), event.getPosition()));
            }
        });
    }

    /** Звуки, прозвучавшие внутри площадки теста, по порядку. */
    static List<String> heardIn(GameTestHelper h) {
        AABB box = new AABB(h.absolutePos(BlockPos.ZERO).getCenter(), h.absolutePos(new BlockPos(8, 5, 8)).getCenter())
                .inflate(1);
        return HEARD.stream().filter(e -> box.contains(e.at())).map(Heard::sound).toList();
    }

    static long times(List<String> heard, String sound) {
        return heard.stream().filter(sound::equals).count();
    }

    @GameTest(template = "magictest", timeoutTicks = 140)
    public static void rootInterfixRootSounds(GameTestHelper h) {
        // Исцеление — огонь — исцеление: корень, интерфикс (ожидание 4 с), корень.
        ServerPlayer p = player(h);
        h.assertTrue(cast(p, HEAL, FIRE, HEAL).isEmpty(), "каст");
        h.runAfterDelay(3, () -> {
            List<String> heard = heardIn(h);
            log("sounds t3: " + heard);
            h.assertTrue(times(heard, "minecraft:block.amethyst_block.chime") == 1, "корень не прозвучал один раз");
            h.assertTrue(times(heard, "minecraft:entity.blaze.burn") == 1, "интерфикс не прозвучал");
        });
        h.runAfterDelay(100, () -> {
            List<String> heard = heardIn(h);
            log("sounds t100: " + heard);
            h.assertTrue(times(heard, "minecraft:block.amethyst_block.chime") == 2, "второй корень не прозвучал");
            h.succeed();
        });
    }

    @GameTest(template = "magictest", timeoutTicks = 200)
    public static void prefixOnceRootEachPeriod(GameTestHelper h) {
        // Огонь — исцеление: огненный префикс делает исцеление периодическим (0, 3, 6 с).
        ServerPlayer p = player(h);
        h.assertTrue(cast(p, FIRE, HEAL).isEmpty(), "каст");
        h.runAfterDelay(150, () -> {
            List<String> heard = heardIn(h);
            log("sounds periodic: " + heard);
            h.assertTrue(times(heard, "minecraft:item.firecharge.use") == 1, "префикс звучал не один раз");
            h.assertTrue(times(heard, "minecraft:block.amethyst_block.chime") == 3, "корень звучал не три раза");
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void soundValidation(GameTestHelper h) throws Exception {
        java.nio.file.Path file = com.kirbornu.gimpanum.magic.config.MagicConfig.dir().resolve("arcanes.json");
        String original = java.nio.file.Files.readString(file);
        try {
            java.nio.file.Files.writeString(file, original.replace("minecraft:entity.blaze.shoot", "minecraft:nope")
                    .replace("\"pitch\": 1.2}", "\"pitch\": 3}"));
            List<String> errors = com.kirbornu.gimpanum.magic.config.MagicConfig.load(h.getLevel().getServer());
            log("sound errors: " + errors);
            h.assertTrue(errors.stream().anyMatch(e -> e.contains("sounds.root.sound: нет такого звука")),
                    "нет ошибки звука");
            h.assertTrue(errors.stream().anyMatch(e -> e.contains("pitch: должно быть от 0.5 до 2.0")),
                    "нет ошибки высоты");
        } finally {
            java.nio.file.Files.writeString(file, original);
        }
        h.assertTrue(com.kirbornu.gimpanum.magic.config.MagicConfig.load(h.getLevel().getServer()).isEmpty(), "возврат");
        h.succeed();
    }
}
