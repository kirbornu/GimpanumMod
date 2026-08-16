package com.kirbornu.gimpanum.converter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Чем торгуют Фонос-конвертеры, найденные в Гимпануме.
 *
 * <p>Конвертер, поставленный игроком, ничего не знает об обмене, пока его не
 * настроят командой. А вот найденный в мире должен что-то предлагать сразу —
 * и предлагать разное, иначе все находки одинаковы и искать их незачем.
 *
 * <p>Список предложений живёт в {@code config/gimpanum/converter_offers.json},
 * а не в датапаке мода: это то, что владелец сервера будет править чаще
 * всего, и лезть ради этого внутрь джарки неправильно. Файл создаётся при
 * первом запуске и перечитывается по {@code /gimpanum converter offers reload}.
 *
 * <p>Вес — относительный: предложение с весом 20 выпадает вдвое чаще, чем с
 * весом 10. Ноль и отрицательные значения выключают предложение.
 */
public final class ConverterOffers {

    /** Одно предложение: что берут, сколько, что дают и насколько часто такое встречается. */
    public record Offer(int weight, ItemStack input, int quota, ItemStack output, int outputCount,
                        Optional<String> label) {

        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                // Умолчание единица, а не десять: тогда в примере видны все веса —
                // кодек не пишет поле, совпадающее с умолчанием.
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Offer::weight),
                ItemStack.SINGLE_ITEM_CODEC.fieldOf("input").forGetter(Offer::input),
                Codec.INT.optionalFieldOf("quota", ConverterConfig.DEFAULT_QUOTA).forGetter(Offer::quota),
                ItemStack.SINGLE_ITEM_CODEC.fieldOf("output").forGetter(Offer::output),
                Codec.INT.optionalFieldOf("output_count", 1).forGetter(Offer::outputCount),
                Codec.STRING.optionalFieldOf("label").forGetter(Offer::label)
        ).apply(instance, Offer::new));

        public ConverterConfig toConfig() {
            return new ConverterConfig(Optional.of(input.copy()), quota,
                    Optional.of(output.copy()), outputCount, label);
        }
    }

    private static final Codec<List<Offer>> CODEC =
            Offer.CODEC.listOf().fieldOf("offers").codec();

    private static final String FILE = "converter_offers.json";

    private static List<Offer> offers = List.of();

    private ConverterOffers() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(Gimpanum.MOD_ID).resolve(FILE);
    }

    /** Читает файл, создавая его с примерами, если файла ещё нет. */
    public static void load(MinecraftServer server) {
        Path file = path();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                writeDemo(server, file);
                Gimpanum.LOGGER.info("Создан пример настроек Фонос-конвертеров: {}", file);
            }
            RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement json = JsonParser.parseReader(reader);
                offers = CODEC.parse(ops, json).resultOrPartial(
                        error -> Gimpanum.LOGGER.error("Настройки Фонос-конвертеров: {}", error))
                        .orElse(List.of());
            }
            Gimpanum.LOGGER.info("Предложений Фонос-конвертеров загружено: {}", offers.size());
        } catch (Exception failure) {
            Gimpanum.LOGGER.error("Не прочитались настройки Фонос-конвертеров {}", file, failure);
            offers = List.of();
        }
    }

    public static int count() {
        return offers.size();
    }

    /** Случайное предложение с учётом весов. Пусто, если список пуст или все веса нулевые. */
    public static Optional<Offer> roll(RandomSource random) {
        int total = 0;
        for (Offer offer : offers) {
            if (offer.weight() > 0) {
                total += offer.weight();
            }
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int pick = random.nextInt(total);
        for (Offer offer : offers) {
            if (offer.weight() <= 0) {
                continue;
            }
            pick -= offer.weight();
            if (pick < 0) {
                return Optional.of(offer);
            }
        }
        return Optional.empty();
    }

    private static void writeDemo(MinecraftServer server, Path file) throws IOException {
        HolderLookup.Provider registries = server.registryAccess();
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        List<Offer> demo = List.of(
                new Offer(20, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 64,
                        new ItemStack(net.minecraft.world.item.Items.DIAMOND), 1, Optional.of("Железо в алмазы")),
                new Offer(20, new ItemStack(net.minecraft.world.item.Items.COAL), 128,
                        new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT), 2, Optional.of("Уголь в золото")),
                new Offer(10, new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH), 256,
                        new ItemStack(net.minecraft.world.item.Items.EMERALD), 1, Optional.of("Плоть в изумруды")),
                new Offer(10, new ItemStack(net.minecraft.world.item.Items.GUNPOWDER), 96,
                        new ItemStack(net.minecraft.world.item.Items.TNT), 4, Optional.of("Порох в динамит")),
                new Offer(5, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP), 16,
                        new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT), 1, Optional.of("Обломки в незерит")),
                new Offer(3, new ItemStack(net.minecraft.world.item.Items.DIAMOND), 32,
                        new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE), 1,
                        Optional.of("Алмазы в райское яблоко"))
        );
        JsonElement json = CODEC.encodeStart(ops, demo)
                .getOrThrow(error -> new IOException("не собрался пример: " + error));
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        }
    }
}
