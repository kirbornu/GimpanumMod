package com.kirbornu.gimpanum.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kirbornu.gimpanum.Gimpanum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Что лежит внутри Замороженной органики.
 *
 * <p>Список живёт в {@code config/gimpanum/thawed_organics.json}, а не в теге
 * предметов, как было раньше. Причина не в удобстве правки, а в весах: тег —
 * это множество без порядка и без кратностей, и «пшеница попадается вдвое чаще
 * василька» в нём выразить нечем. Заодно владельцу сервера больше не нужен
 * датапак ради одной строки.
 *
 * <p>Разбираем по одной находке, а не список целиком: половина образца
 * ссылается на предметы Farmer's Delight и Vinery, и без какого-нибудь из этих
 * модов разбор всего списка провалился бы — из-за одной строки органика
 * перестала бы оттаивать вовсе. Непонятная строка пропускается с записью в
 * журнал, остальные работают. Это ровно то, что раньше делал
 * {@code "required": false} в теге.
 *
 * <p>Файл создаётся при первом запуске из образца в джарке и перечитывается по
 * {@code /gimpanum thawing reload}.
 */
public final class ThawedOrganics {

    /** Одна находка: что выпадет и насколько часто. */
    public record Find(int weight, ItemStack item) {

        public static final Codec<Find> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Find::weight),
                // Полный ItemStack.CODEC, а не SINGLE_ITEM_CODEC: находка может
                // быть и пригоршней семян, и предметом с компонентами.
                ItemStack.CODEC.fieldOf("item").forGetter(Find::item)
        ).apply(instance, Find::new));
    }

    private static final String FILE = "thawed_organics.json";

    /** Образец, из которого создаётся файл настроек при первом запуске. */
    private static final String DEFAULTS = "/data/gimpanum/thawing/default_results.json";

    private static List<Find> finds = List.of();

    private ThawedOrganics() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(Gimpanum.MOD_ID).resolve(FILE);
    }

    public static int count() {
        return finds.size();
    }

    /** Читает файл, создавая его из образца, если файла ещё нет. */
    public static void load(MinecraftServer server) {
        Path file = path();
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                writeDefaults(file);
                Gimpanum.LOGGER.info("Создан пример содержимого Замороженной органики: {}", file);
            }
            RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                finds = parse(ops, JsonParser.parseReader(reader));
            }
            Gimpanum.LOGGER.info("Находок в Замороженной органике загружено: {}", finds.size());
        } catch (Exception failure) {
            Gimpanum.LOGGER.error("Не прочиталось содержимое Замороженной органики {}", file, failure);
            finds = List.of();
        }
    }

    /**
     * Случайная находка с учётом весов.
     *
     * <p>Пусто — когда список пуст или все веса нулевые; тогда печь выдаёт
     * результат, записанный в самом рецепте. Пусто будет и на клиенте
     * выделенного сервера: файл читает сервер, и просмотрщики рецептов у
     * игрока увидят один и тот же образцовый предмет вместо случайного. Так и
     * задумано — иначе список в JEI дёргался бы при каждой перерисовке.
     */
    public static Optional<ItemStack> roll(RandomSource random) {
        int total = 0;
        for (Find find : finds) {
            if (find.weight() > 0) {
                total += find.weight();
            }
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int pick = random.nextInt(total);
        for (Find find : finds) {
            if (find.weight() <= 0) {
                continue;
            }
            pick -= find.weight();
            if (pick < 0) {
                return Optional.of(find.item().copy());
            }
        }
        return Optional.empty();
    }

    private static List<Find> parse(RegistryOps<JsonElement> ops, JsonElement json) {
        List<Find> parsed = new ArrayList<>();
        if (!(json instanceof JsonObject root) || !(root.get("results") instanceof JsonArray array)) {
            Gimpanum.LOGGER.error("Содержимое Замороженной органики: нет списка results");
            return List.of();
        }
        for (JsonElement element : array) {
            Find.CODEC.parse(ops, element)
                    .resultOrPartial(error -> Gimpanum.LOGGER.warn(
                            "Находка Замороженной органики пропущена ({}): {}", error, element))
                    .ifPresent(parsed::add);
        }
        return List.copyOf(parsed);
    }

    /**
     * Кладёт рядом с настройками образец из джарки — целиком, как он есть.
     *
     * <p>Копируем текстом, а не собираем кодеком из кода: в образце
     * перечислены предметы чужих модов, и собрать их в {@link ItemStack} без
     * этих модов невозможно, а образец нужен полный.
     */
    private static void writeDefaults(Path file) throws IOException {
        try (InputStream source = ThawedOrganics.class.getResourceAsStream(DEFAULTS)) {
            if (source == null) {
                throw new IOException("в джарке нет образца " + DEFAULTS);
            }
            Files.copy(source, file);
        }
    }
}
