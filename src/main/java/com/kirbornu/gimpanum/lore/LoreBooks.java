package com.kirbornu.gimpanum.lore;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Лор, который игроки находят, а не читают в вики.
 *
 * <p>Каждый файл в {@code config/gimpanum/lore} — одна книга. Первая строка
 * его заголовок, вторая — подпись, остальное — текст. Страницы разделяются
 * строкой из трёх дефисов; если разделителей нет, текст режется сам по
 * границам слов, чтобы не заставлять писать лор с линейкой в руках.
 *
 * <p>Папка, а не датапак: лор — это то, что владелец сервера дописывает по
 * ходу игры, между делом, не пересобирая ничего. Новые файлы подхватываются
 * по {@code /gimpanum lore reload}.
 *
 * <p>Книги ничего не знают о том, откуда выпали. Их роняют некрофаги и
 * конвертеры, и обе стороны спрашивают одно и то же — случайную книгу.
 */
public final class LoreBooks {

    private static final String DIR = "lore";

    /** Кодек заголовка обрезает по этой длине, поэтому режем сами и заранее. */
    private static final int TITLE_LIMIT = 32;

    /**
     * Мягкий предел страницы: дальше ищем ближайший пробел. Окно книги
     * вмещает 14 строк по 114 пикселей — это около 240 знаков кириллицы;
     * всё, что ниже, игра молча не показывает.
     */
    private static final int PAGE_SOFT_LIMIT = 220;

    /** Где в джарке лежат книги, которые кладутся в новую папку. */
    private static final String[] BUILT_IN = {"data", Gimpanum.MOD_ID, DIR};

    private static final String PAGE_BREAK = "---";

    private static List<Book> books = List.of();

    /** Одна книга: заголовок, подпись, страницы. */
    private record Book(String title, String author, List<String> pages) {
    }

    private LoreBooks() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(Gimpanum.MOD_ID).resolve(DIR);
    }

    public static int count() {
        return books.size();
    }

    /** Читает папку, создавая её с образцами, если её ещё нет. */
    public static void load() {
        Path dir = path();
        try {
            if (Files.notExists(dir)) {
                Files.createDirectories(dir);
                writeSamples(dir);
                Gimpanum.LOGGER.info("Создана папка для лора: {}", dir);
            }
            List<Book> read = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    parse(file).ifPresent(read::add);
                }
            }
            books = List.copyOf(read);
            Gimpanum.LOGGER.info("Книг лора загружено: {}", books.size());
        } catch (Exception failure) {
            Gimpanum.LOGGER.error("Не прочиталась папка лора {}", dir, failure);
            books = List.of();
        }
    }

    /** Случайная книга. Пусто, если папка пуста — тогда и ронять нечего. */
    public static Optional<ItemStack> roll(RandomSource random) {
        if (books.isEmpty()) {
            return Optional.empty();
        }
        Book book = books.get(random.nextInt(books.size()));
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(book.title()),
                book.author(),
                0,
                book.pages().stream()
                        .map(page -> Filterable.passThrough((Component) Component.literal(page)))
                        .toList(),
                true));
        return Optional.of(stack);
    }

    private static Optional<Book> parse(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.size() < 3) {
                Gimpanum.LOGGER.warn("Лор {}: нужны хотя бы заголовок, подпись и строка текста", file.getFileName());
                return Optional.empty();
            }
            String title = trim(lines.get(0).trim());
            String author = lines.get(1).trim();
            List<String> pages = pages(lines.subList(2, lines.size()));
            if (title.isEmpty() || pages.isEmpty()) {
                Gimpanum.LOGGER.warn("Лор {}: пустой заголовок или пустой текст", file.getFileName());
                return Optional.empty();
            }
            return Optional.of(new Book(title, author, pages));
        } catch (Exception failure) {
            Gimpanum.LOGGER.error("Лор {} не прочитался", file.getFileName(), failure);
            return Optional.empty();
        }
    }

    /** Страницы: по разделителям, а если их нет — по границам слов. */
    private static List<String> pages(List<String> body) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean explicit = body.stream().anyMatch(line -> line.strip().equals(PAGE_BREAK));
        for (String line : body) {
            if (explicit && line.strip().equals(PAGE_BREAK)) {
                flush(pages, current, false);
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        flush(pages, current, !explicit);
        return pages;
    }

    private static void flush(List<String> pages, StringBuilder text, boolean split) {
        String content = text.toString().strip();
        text.setLength(0);
        if (content.isEmpty()) {
            return;
        }
        if (!split || content.length() <= PAGE_SOFT_LIMIT) {
            pages.add(content);
            return;
        }
        // Режем по последнему пробелу до предела: рвать слово посреди — заметно.
        while (content.length() > PAGE_SOFT_LIMIT) {
            int cut = content.lastIndexOf(' ', PAGE_SOFT_LIMIT);
            if (cut <= 0) {
                cut = PAGE_SOFT_LIMIT;
            }
            pages.add(content.substring(0, cut).strip());
            content = content.substring(cut).strip();
        }
        if (!content.isEmpty()) {
            pages.add(content);
        }
    }

    private static String trim(String title) {
        return title.length() <= TITLE_LIMIT ? title : title.substring(0, TITLE_LIMIT);
    }

    /**
     * Книги из джарки. Их полторы сотни, поэтому они лежат файлами в
     * ресурсах, а не строками в коде, и копируются как есть.
     */
    private static void writeSamples(Path dir) throws IOException {
        Path source = ModList.get().getModFileById(Gimpanum.MOD_ID).getFile().findResource(BUILT_IN);
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.copy(file, dir.resolve(file.getFileName().toString()));
            }
        }
    }
}
