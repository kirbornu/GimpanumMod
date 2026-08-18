package com.kirbornu.gimpanum.client.dashboard;

import com.kirbornu.gimpanum.core.BoundTeam;
import com.kirbornu.gimpanum.core.CoreConfig;
import com.kirbornu.gimpanum.core.SpawnSettings;
import com.kirbornu.gimpanum.dashboard.ConfigSection;
import com.kirbornu.gimpanum.dashboard.CoreAction;
import com.kirbornu.gimpanum.dashboard.CoreRow;
import com.kirbornu.gimpanum.network.DashboardActionPayload;
import com.kirbornu.gimpanum.network.DashboardApplyPayload;
import com.kirbornu.gimpanum.network.DashboardRefreshPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Консоль Ядер фоносомики.
 *
 * <p>Устроена вокруг двух областей выбора, и путать их нельзя. Слева список
 * Ядер: одно из них <b>выбрано</b> — его настройку правит правая половина
 * окна; произвольное их число <b>отмечено</b> галочкой — к ним применяются
 * действия из нижней строки и перенос настройки. Отмечено ничего — действия
 * идут к выбранному, потому что «ничего не отмечено» почти всегда значит
 * «делаю с тем, на что смотрю».
 *
 * <p>Правки настройки копятся в черновике и уходят на сервер по «Сохранить», а
 * не по каждому нажатию. Иначе каждая набранная в поле цифра оборачивалась бы
 * пакетом и записью в NBT, а промежуточные состояния — вроде пустой строки в
 * поле числа — попадали бы на сервер как настоящие значения.
 *
 * <p>Исключение — привязка экипажей: их состав снимает FTB Teams на сервере, и
 * клиенту его взять неоткуда. Такие правки уходят сразу отдельным действием.
 *
 * <p>Список приходит из указателя целиком, вместе с Ядрами в выгруженных
 * чанках. Это и есть главная причина существования окна: до Ядра на корабле в
 * другом конце карты иначе не дотянуться.
 */
public class CoreDashboardScreen extends Screen {

    private static final int LIST_WIDTH = 200;
    private static final int ROW_HEIGHT = 26;
    private static final int MARGIN = 8;
    private static final int TAB_HEIGHT = 18;
    private static final int LINE = 12;

    /** Сколько миллисекунд ждёт подтверждения кнопка, отменить которую нельзя. */
    private static final long CONFIRM_WINDOW = 3000L;

    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_DIM = 0xFF909090;
    private static final int COLOR_WARN = 0xFFE0B040;
    private static final int COLOR_DANGER = 0xFFE06060;
    private static final int COLOR_OK = 0xFF70C070;

    /** Вкладки правой половины. */
    private enum Tab {
        OVERVIEW("gimpanum.dashboard.tab.overview"),
        FLAGS("gimpanum.dashboard.tab.flags"),
        PLAYERS("gimpanum.dashboard.tab.players"),
        TEAMS("gimpanum.dashboard.tab.teams"),
        COMMANDS("gimpanum.dashboard.tab.commands"),
        SEAL("gimpanum.dashboard.tab.seal"),
        SPAWN("gimpanum.dashboard.tab.spawn"),
        EXPLOSION("gimpanum.dashboard.tab.explosion"),
        TRANSFER("gimpanum.dashboard.tab.transfer");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable(key);
        }
    }

    /** По чему сортировать список. */
    private enum SortKey {
        NAME("gimpanum.dashboard.sort.name"),
        PLACE("gimpanum.dashboard.sort.place"),
        STATE("gimpanum.dashboard.sort.state");

        private final String key;

        SortKey(String key) {
            this.key = key;
        }

        Component label() {
            return Component.translatable(key);
        }
    }

    private List<CoreRow> rows = List.of();
    private boolean mayEditCommands;

    private final Set<UUID> checked = new LinkedHashSet<>();
    @Nullable
    private UUID selectedId;

    /**
     * Несохранённые правки, по одной на Ядро.
     *
     * <p>Отдельно от {@link #rows} по двум причинам. Список перерисовывается
     * ответом сервера на любое действие — набранное в поле пропадало бы от
     * чужого нажатия. И по Ядру, а не одна на окно: переключиться на соседнее
     * Ядро, чтобы свериться, и вернуться — обычное дело, и терять при этом
     * набранное неприемлемо. Правленые Ядра помечены в списке звёздочкой.
     */
    private final Map<UUID, CoreConfig> drafts = new HashMap<>();

    /**
     * Настройка Ядра на тот момент, когда черновик был заведён.
     *
     * <p>Нужна для того, чтобы отличить «оператор это правил» от «оператор
     * этого не трогал». Без такого различения черновик отменял бы чужие
     * изменения: нажатие «Снять предохранитель» меняет Ядро на сервере, а
     * лежащий рядом черновик всё ещё помнит прежнее значение — и следующее
     * «Сохранить» вернуло бы предохранитель на место. Сравнение с исходной
     * настройкой показывает, какие части правил именно оператор; всё
     * остальное берётся из свежего ответа сервера.
     */
    private final Map<UUID, CoreConfig> baselines = new HashMap<>();

    private String search = "";
    private SortKey sortKey = SortKey.NAME;
    private boolean sortAscending = true;
    private Tab tab = Tab.OVERVIEW;

    /** Какие части настройки уходят при переносе на отмеченные Ядра. */
    private int transferMask = ConfigSection.ALL & ~ConfigSection.NAME.bit();

    /** Насколько промотан длинный список внутри вкладки — игроков или команд. */
    private int pageOffset;

    @Nullable
    private CoreAction pendingConfirm;
    private long confirmDeadline;

    private CoreList list;
    private EditBox searchBox;

    // Поля правой половины. Существуют только на своей вкладке, поэтому
    // обнуляются при каждой пересборке и читаются только через harvest().
    @Nullable
    private EditBox nameBox;
    @Nullable
    private EditBox priceBox;
    @Nullable
    private EditBox postfixBox;
    @Nullable
    private EditBox intervalBox;
    @Nullable
    private EditBox countBox;
    @Nullable
    private EditBox itemBox;
    @Nullable
    private EditBox powerBox;
    @Nullable
    private EditBox entryBox;

    public CoreDashboardScreen() {
        super(Component.translatable("gimpanum.dashboard.title"));
    }

    /**
     * Принимает состояние с сервера: открывает окно или обновляет открытое.
     *
     * <p>Обновление не сбрасывает ни выбор, ни отметки, ни черновик — иначе
     * любое действие стирало бы то, что оператор набрал в соседнем поле.
     */
    public static void accept(List<CoreRow> rows, boolean mayEditCommands) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CoreDashboardScreen open) {
            open.update(rows, mayEditCommands);
            return;
        }
        CoreDashboardScreen screen = new CoreDashboardScreen();
        screen.rows = List.copyOf(rows);
        screen.mayEditCommands = mayEditCommands;
        minecraft.setScreen(screen);
    }

    private void update(List<CoreRow> fresh, boolean mayEdit) {
        this.rows = List.copyOf(fresh);
        this.mayEditCommands = mayEdit;
        // Отметки на исчезнувших Ядрах держать незачем: они бы молча уходили в
        // каждое следующее действие и ничего не делали.
        checked.removeIf(id -> find(id).isEmpty());
        drafts.keySet().removeIf(id -> find(id).isEmpty());
        baselines.keySet().removeIf(id -> !drafts.containsKey(id));
        rebase();
        if (selectedId != null && find(selectedId).isEmpty()) {
            selectedId = null;
        }
        refresh();
    }

    /**
     * Переносит черновики на свежую настройку с сервера.
     *
     * <p>Правленые оператором части остаются как есть, остальные заменяются
     * тем, что пришло. Так уживаются два источника изменений: поля, которые
     * правят в окне, и действия — снятие предохранителя, привязка экипажа, —
     * которые выполняет сервер и о которых клиент узнаёт только из ответа.
     */
    private void rebase() {
        for (CoreRow row : rows) {
            CoreConfig pending = drafts.get(row.id());
            if (pending == null) {
                continue;
            }
            CoreConfig base = baselines.getOrDefault(row.id(), row.config());
            drafts.put(row.id(),
                    ConfigSection.merge(row.config(), pending, ConfigSection.changed(base, pending)));
            baselines.put(row.id(), row.config());
        }
    }

    /**
     * Пересобирает окно, сохраняя прокрутку списка.
     *
     * <p>Отметить десяток Ядер — обычное дело, а каждая отметка перестраивает
     * виджеты; без сохранения прокрутки список отматывался бы в начало на
     * каждой галочке, и отметить что-нибудь ниже первого экрана было бы
     * невозможно.
     */
    private void refresh() {
        double scroll = list == null ? 0.0 : list.getScrollAmount();
        rebuildWidgets();
        if (list != null) {
            list.setScrollAmount(scroll);
        }
    }

    // --- Сборка окна ----------------------------------------------------------

    @Override
    protected void init() {
        nameBox = priceBox = postfixBox = intervalBox = countBox = itemBox = powerBox = entryBox = null;

        int listBottom = height - MARGIN - 2 * (TAB_HEIGHT + 4);
        int listTop = MARGIN + 20 + TAB_HEIGHT + 4;

        searchBox = new EditBox(font, MARGIN, MARGIN + 20, LIST_WIDTH - 74, TAB_HEIGHT,
                Component.translatable("gimpanum.dashboard.search"));
        searchBox.setValue(search);
        searchBox.setHint(Component.translatable("gimpanum.dashboard.search"));
        searchBox.setResponder(value -> {
            search = value;
            list.reload();
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(sortLabel(), button -> {
            if (sortAscending) {
                sortAscending = false;
            } else {
                sortAscending = true;
                sortKey = SortKey.values()[(sortKey.ordinal() + 1) % SortKey.values().length];
            }
            button.setMessage(sortLabel());
            list.reload();
        }).bounds(MARGIN + LIST_WIDTH - 70, MARGIN + 20, 70, TAB_HEIGHT).build());

        list = new CoreList(listTop, listBottom - listTop);
        addRenderableWidget(list);

        int buttonsY = listBottom + 4;
        int third = (LIST_WIDTH - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("gimpanum.dashboard.select_all"),
                b -> setAllChecked(true)).bounds(MARGIN, buttonsY, third, TAB_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gimpanum.dashboard.select_none"),
                b -> setAllChecked(false)).bounds(MARGIN + third + 4, buttonsY, third, TAB_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gimpanum.dashboard.select_invert"),
                b -> invertChecked()).bounds(MARGIN + 2 * (third + 4), buttonsY, third, TAB_HEIGHT).build());

        addRenderableWidget(Button.builder(Component.translatable("gimpanum.dashboard.refresh"),
                b -> PacketDistributor.sendToServer(new DashboardRefreshPayload()))
                .bounds(MARGIN, buttonsY + TAB_HEIGHT + 4, LIST_WIDTH, TAB_HEIGHT).build());

        initRightPanel();
    }

    private Component sortLabel() {
        return Component.literal(sortAscending ? "^ " : "v ").append(sortKey.label());
    }

    private int rightX() {
        return MARGIN + LIST_WIDTH + MARGIN;
    }

    private int rightWidth() {
        return width - rightX() - MARGIN;
    }

    private void initRightPanel() {
        int x = rightX();
        int panelWidth = rightWidth();

        // Вкладки двумя рядами: одним рядом девять ярлыков читаемой ширины не
        // помещаются даже на широком экране.
        Tab[] tabs = Tab.values();
        int firstRow = 5;
        layoutTabs(tabs, 0, firstRow, x, MARGIN + 20, panelWidth);
        layoutTabs(tabs, firstRow, tabs.length, x, MARGIN + 20 + TAB_HEIGHT + 2, panelWidth);

        int contentTop = MARGIN + 20 + 2 * (TAB_HEIGHT + 2) + 6;
        int contentBottom = height - MARGIN - 2 * (TAB_HEIGHT + 4);

        if (selectedId != null) {
            initTab(x, contentTop, panelWidth, contentBottom);
        }

        initActionBar(x, contentBottom + 4, panelWidth);
    }

    private void layoutTabs(Tab[] tabs, int from, int to, int x, int y, int panelWidth) {
        int count = to - from;
        if (count <= 0) {
            return;
        }
        int cell = (panelWidth - 2 * (count - 1)) / count;
        for (int i = from; i < to; i++) {
            Tab which = tabs[i];
            boolean active = which == tab;
            Component label = active
                    ? which.label().copy().withStyle(ChatFormatting.YELLOW)
                    : which.label();
            Button button = Button.builder(label, b -> {
                harvest();
                tab = which;
                pageOffset = 0;
                refresh();
            }).bounds(x + (i - from) * (cell + 2), y, cell, TAB_HEIGHT).build();
            button.active = !active;
            addRenderableWidget(button);
        }
    }

    /**
     * Нижняя строка — действия над отмеченными Ядрами.
     *
     * <p>Она одна на все вкладки намеренно: действие не зависит от того, какую
     * часть настройки сейчас смотрят, и пряталось бы от оператора без всякой
     * причины.
     */
    private void initActionBar(int x, int y, int panelWidth) {
        int half = (panelWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gimpanum.dashboard.save"),
                b -> save()).bounds(x, y, half, TAB_HEIGHT).build())
                .active = selectedId != null;

        Button transfer = Button.builder(
                Component.translatable("gimpanum.dashboard.transfer", checked.size()),
                b -> transfer()).bounds(x + half + 4, y, half, TAB_HEIGHT).build();
        transfer.active = selectedId != null && !checked.isEmpty();
        addRenderableWidget(transfer);

        int row = y + TAB_HEIGHT + 4;
        CoreAction[] bar = {CoreAction.ARM, CoreAction.DISARM, CoreAction.LOCK, CoreAction.UNLOCK,
                CoreAction.TELEPORT, CoreAction.DELETE, CoreAction.DETONATE};
        int cell = (panelWidth - 6 * 2) / bar.length;
        for (int i = 0; i < bar.length; i++) {
            CoreAction action = bar[i];
            boolean armed = pendingConfirm == action && System.currentTimeMillis() < confirmDeadline;
            Component label = armed
                    ? Component.translatable("gimpanum.dashboard.confirm").withStyle(ChatFormatting.RED)
                    : actionLabel(action);
            Button button = Button.builder(label, b -> onAction(action))
                    .bounds(x + i * (cell + 2), row, cell, TAB_HEIGHT).build();
            button.active = !targets().isEmpty()
                    && (action != CoreAction.TELEPORT || targets().size() == 1);
            addRenderableWidget(button);
        }
    }

    private static Component actionLabel(CoreAction action) {
        return Component.translatable("gimpanum.dashboard.action." + action.name().toLowerCase(Locale.ROOT));
    }

    // --- Вкладки --------------------------------------------------------------

    private void initTab(int x, int y, int panelWidth, int bottom) {
        switch (tab) {
            case OVERVIEW -> {
                nameBox = new EditBox(font, x + 60, y, Math.min(180, panelWidth - 60), TAB_HEIGHT,
                        Component.translatable("gimpanum.dashboard.name"));
                nameBox.setMaxLength(32);
                nameBox.setValue(draft().name());
                addRenderableWidget(nameBox);
            }
            case FLAGS -> {
                addCheck(x, y, "gimpanum.dashboard.armed", draft().armed(),
                        value -> setDraft(draft().withArmed(value)));
                addCheck(x, y + 22, "gimpanum.dashboard.invulnerable", draft().invulnerable(),
                        value -> setDraft(draft().withInvulnerable(value)));
                addCheck(x, y + 44, "gimpanum.dashboard.autofragile", draft().autoDisableInvulnerable(),
                        value -> setDraft(draft().withAutoDisableInvulnerable(value)));
            }
            case PLAYERS -> stringList(x, y, panelWidth, bottom, draft().boundPlayers(),
                    "gimpanum.dashboard.add_player",
                    value -> {
                        List<String> next = new ArrayList<>(draft().boundPlayers());
                        if (!next.contains(value)) {
                            next.add(value);
                        }
                        setDraft(draft().withBoundPlayers(next));
                    },
                    index -> {
                        List<String> next = new ArrayList<>(draft().boundPlayers());
                        next.remove(index);
                        setDraft(draft().withBoundPlayers(next));
                    });
            case TEAMS -> initTeams(x, y, panelWidth, bottom);
            case COMMANDS -> {
                if (!mayEditCommands) {
                    return;
                }
                addCheck(x, y, "gimpanum.dashboard.death_commands", draft().deathCommands(),
                        value -> setDraft(draft().withDeathCommands(value)));
                stringList(x, y + 24, panelWidth, bottom, draft().commands(),
                        "gimpanum.dashboard.add_command",
                        value -> {
                            List<String> next = new ArrayList<>(draft().commands());
                            next.add(value);
                            setDraft(draft().withCommands(next));
                        },
                        index -> {
                            List<String> next = new ArrayList<>(draft().commands());
                            next.remove(index);
                            setDraft(draft().withCommands(next));
                        });
            }
            case SEAL -> {
                addCheck(x, y, "gimpanum.dashboard.seal_enabled", draft().sealEnabled(),
                        value -> setDraft(draft().withSealEnabled(value)));
                priceBox = numberBox(x + 110, y + 24, 60, String.valueOf(draft().sealPrice()), false);
                postfixBox = new EditBox(font, x + 110, y + 48, Math.min(200, panelWidth - 110),
                        TAB_HEIGHT, Component.translatable("gimpanum.dashboard.postfix"));
                postfixBox.setMaxLength(64);
                postfixBox.setValue(draft().sealPostfix().orElse(""));
                addRenderableWidget(postfixBox);
            }
            case SPAWN -> {
                SpawnSettings spawn = draft().spawn();
                addCheck(x, y, "gimpanum.dashboard.spawn_enabled", spawn.enabled(),
                        value -> setDraft(draft().withSpawn(draft().spawn().withEnabled(value))));
                intervalBox = numberBox(x + 110, y + 24, 60, String.valueOf(spawn.intervalSeconds()), false);
                countBox = numberBox(x + 110, y + 48, 60, String.valueOf(spawn.count()), false);
                itemBox = new EditBox(font, x + 110, y + 72, Math.min(200, panelWidth - 110),
                        TAB_HEIGHT, Component.translatable("gimpanum.dashboard.spawn_item"));
                itemBox.setValue(spawn.item().map(ResourceLocation::toString).orElse(""));
                itemBox.setHint(Component.translatable("gimpanum.dashboard.spawn_seal"));
                addRenderableWidget(itemBox);
            }
            case EXPLOSION -> {
                addCheck(x, y, "gimpanum.dashboard.explosion_enabled", draft().explosionEnabled(),
                        value -> setDraft(draft().withExplosionEnabled(value)));
                powerBox = numberBox(x + 110, y + 24, 60, formatPower(draft().explosionPower()), true);
                addCheck(x, y + 48, "gimpanum.dashboard.explosion_fire", draft().explosionFire(),
                        value -> setDraft(draft().withExplosion(draft().explosionPower(), value)));
            }
            case TRANSFER -> {
                ConfigSection[] sections = ConfigSection.values();
                for (int i = 0; i < sections.length; i++) {
                    ConfigSection section = sections[i];
                    addCheck(x + (i % 2) * (panelWidth / 2), y + (i / 2) * 22,
                            "gimpanum.dashboard.section." + section.name().toLowerCase(Locale.ROOT),
                            section.in(transferMask),
                            value -> transferMask = value
                                    ? transferMask | section.bit()
                                    : transferMask & ~section.bit());
                }
            }
        }
    }

    private void initTeams(int x, int y, int panelWidth, int bottom) {
        entryBox = new EditBox(font, x, y, Math.min(200, panelWidth - 80), TAB_HEIGHT,
                Component.translatable("gimpanum.dashboard.add_team"));
        entryBox.setHint(Component.translatable("gimpanum.dashboard.add_team"));
        addRenderableWidget(entryBox);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            String value = entryBox.getValue().trim();
            if (!value.isEmpty()) {
                sendAction(CoreAction.TEAM_ADD, value);
            }
        }).bounds(x + Math.min(200, panelWidth - 80) + 4, y, 20, TAB_HEIGHT).build());

        List<BoundTeam> teams = draft().boundTeams();
        int rowY = y + TAB_HEIGHT + 6;
        for (BoundTeam team : teams) {
            if (rowY + TAB_HEIGHT > bottom) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal("x"),
                            b -> sendAction(CoreAction.TEAM_REMOVE, team.teamName()))
                    .bounds(x, rowY, 20, TAB_HEIGHT).build());
            rowY += TAB_HEIGHT + 2;
        }
    }

    /**
     * Список строк с кнопкой добавления и крестиком у каждой.
     *
     * <p>Один и тот же для привязанных игроков и для команд: устроены они
     * одинаково, а два почти совпадающих куска разошлись бы при первой же
     * правке.
     */
    private void stringList(int x, int y, int panelWidth, int bottom, List<String> values,
                            String hintKey, java.util.function.Consumer<String> onAdd,
                            java.util.function.IntConsumer onRemove) {
        int boxWidth = Math.min(280, panelWidth - 30);
        entryBox = new EditBox(font, x, y, boxWidth, TAB_HEIGHT, Component.translatable(hintKey));
        entryBox.setMaxLength(256);
        entryBox.setHint(Component.translatable(hintKey));
        addRenderableWidget(entryBox);
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            String value = entryBox.getValue().trim();
            if (!value.isEmpty()) {
                onAdd.accept(value);
                refresh();
            }
        }).bounds(x + boxWidth + 4, y, 20, TAB_HEIGHT).build());

        addPager(x + boxWidth + 28, y, values.size(), capacity(y, bottom));

        int rowY = y + TAB_HEIGHT + 6;
        for (int i = pageOffset; i < values.size(); i++) {
            if (rowY + TAB_HEIGHT > bottom) {
                break;
            }
            int index = i;
            addRenderableWidget(Button.builder(Component.literal("x"), b -> {
                onRemove.accept(index);
                refresh();
            }).bounds(x, rowY, 20, TAB_HEIGHT).build());
            rowY += TAB_HEIGHT + 2;
        }
    }

    /** Сколько строк помещается под шапкой вкладки. */
    private int capacity(int y, int bottom) {
        return Math.max(1, (bottom - (y + TAB_HEIGHT + 6)) / (TAB_HEIGHT + 2));
    }

    /**
     * Две стрелки прокрутки длинного списка внутри вкладки.
     *
     * <p>Без них у Ядра с двумя десятками команд лишние строки просто не
     * помещались бы в окно — и, что хуже, их нельзя было бы удалить, потому
     * что крестика рядом с невидимой строкой нет.
     */
    private void addPager(int x, int y, int total, int capacity) {
        if (total <= capacity) {
            pageOffset = 0;
            return;
        }
        pageOffset = Math.min(pageOffset, total - capacity);
        Button up = Button.builder(Component.literal("^"), b -> {
            pageOffset = Math.max(0, pageOffset - capacity);
            refresh();
        }).bounds(x, y, 20, TAB_HEIGHT).build();
        up.active = pageOffset > 0;
        addRenderableWidget(up);

        Button down = Button.builder(Component.literal("v"), b -> {
            pageOffset = Math.min(total - capacity, pageOffset + capacity);
            refresh();
        }).bounds(x + 22, y, 20, TAB_HEIGHT).build();
        down.active = pageOffset + capacity < total;
        addRenderableWidget(down);
    }

    private void addCheck(int x, int y, String key, boolean selected, java.util.function.Consumer<Boolean> onChange) {
        addRenderableWidget(Checkbox.builder(Component.translatable(key), font)
                .pos(x, y)
                .selected(selected)
                .onValueChange((box, value) -> onChange.accept(value))
                .build());
    }

    /** Поле числа: посторонние символы не принимаются вовсе, а не отсеиваются потом. */
    private EditBox numberBox(int x, int y, int boxWidth, String value, boolean decimal) {
        EditBox box = new EditBox(font, x, y, boxWidth, TAB_HEIGHT, Component.empty());
        box.setFilter(text -> text.isEmpty()
                || text.matches(decimal ? "\\d{0,4}(\\.\\d{0,2})?" : "\\d{0,9}"));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private static String formatPower(float power) {
        return power == Math.round(power)
                ? String.valueOf(Math.round(power))
                : String.format(Locale.ROOT, "%.2f", power);
    }

    // --- Черновик -------------------------------------------------------------

    /**
     * Забирает в черновик всё, что набрано в полях прямо сейчас.
     *
     * <p>Поля пересобираются при каждой смене вкладки, поэтому набранное надо
     * снять до того, как виджет исчезнет. Пустое или неразобранное поле
     * оставляет прежнее значение: недописанное число — не повод обнулить
     * настройку.
     */
    private void harvest() {
        if (nameBox != null && !nameBox.getValue().isBlank()) {
            setDraft(draft().withName(nameBox.getValue().trim()));
        }
        if (priceBox != null) {
            parseInt(priceBox.getValue()).ifPresent(value -> setDraft(draft().withSealPrice(value)));
        }
        if (postfixBox != null) {
            String value = postfixBox.getValue().trim();
            setDraft(draft().withSealPostfix(value.isEmpty() ? Optional.empty() : Optional.of(value)));
        }
        if (intervalBox != null) {
            parseInt(intervalBox.getValue()).filter(value -> value >= 1)
                    .ifPresent(value -> setDraft(draft().withSpawn(draft().spawn().withIntervalSeconds(value))));
        }
        if (countBox != null) {
            parseInt(countBox.getValue()).filter(value -> value >= 1 && value <= SpawnSettings.MAX_COUNT)
                    .ifPresent(value -> setDraft(draft().withSpawn(draft().spawn().withCount(value))));
        }
        if (itemBox != null) {
            String value = itemBox.getValue().trim();
            setDraft(draft().withSpawn(draft().spawn().withItem(
                    value.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(value)))));
        }
        if (powerBox != null) {
            parseFloat(powerBox.getValue()).filter(value -> value >= 0.0F && value <= 100.0F)
                    .ifPresent(value -> setDraft(draft().withExplosion(value, draft().explosionFire())));
        }
    }

    private static Optional<Integer> parseInt(String text) {
        try {
            return Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Float> parseFloat(String text) {
        try {
            return Optional.of(Float.parseFloat(text.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /** Черновик выбранного Ядра; при первом обращении — копия его настройки. */
    private CoreConfig draft() {
        if (selectedId == null) {
            return CoreConfig.EMPTY;
        }
        return drafts.computeIfAbsent(selectedId, id -> {
            CoreConfig config = find(id).map(CoreRow::config).orElse(CoreConfig.EMPTY);
            baselines.put(id, config);
            return config;
        });
    }

    private void setDraft(CoreConfig value) {
        if (selectedId != null) {
            drafts.put(selectedId, value);
        }
    }

    /** Расходится ли черновик с тем, что знает сервер. */
    private boolean isModified(CoreRow row) {
        CoreConfig pending = drafts.get(row.id());
        return pending != null && !pending.equals(row.config());
    }

    private void select(CoreRow row) {
        harvest();
        selectedId = row.id();
        pendingConfirm = null;
        pageOffset = 0;
        refresh();
    }

    // --- Отправка -------------------------------------------------------------

    private void save() {
        if (selectedId == null) {
            return;
        }
        harvest();
        PacketDistributor.sendToServer(
                new DashboardApplyPayload(List.of(selectedId), draft(), ConfigSection.ALL));
    }

    /** Переносит выбранные части настройки текущего Ядра на все отмеченные. */
    private void transfer() {
        if (selectedId == null || checked.isEmpty()) {
            return;
        }
        harvest();
        // Имя из переноса выбрасывается всегда: оно обязано быть уникальным, и
        // сервер всё равно его отклонит. Убираем здесь, чтобы не мигало
        // сообщение об отказе на каждое Ядро.
        int mask = transferMask & ~ConfigSection.NAME.bit();
        PacketDistributor.sendToServer(
                new DashboardApplyPayload(List.copyOf(checked), draft(), mask));
    }

    /**
     * Действия, которые нельзя отменить, требуют второго нажатия.
     *
     * <p>Удаление отмеченного десятка Ядер — единственное действие в окне,
     * которое ничем не восстановить, а кнопка стоит в одном ряду с телепортом.
     */
    private void onAction(CoreAction action) {
        boolean dangerous = action == CoreAction.DELETE || action == CoreAction.DETONATE;
        if (dangerous && (pendingConfirm != action || System.currentTimeMillis() >= confirmDeadline)) {
            pendingConfirm = action;
            confirmDeadline = System.currentTimeMillis() + CONFIRM_WINDOW;
            refresh();
            return;
        }
        pendingConfirm = null;
        sendAction(action, "");
    }

    private void sendAction(CoreAction action, String argument) {
        List<UUID> targets = targets();
        if (targets.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new DashboardActionPayload(targets, action, argument));
    }

    /** К чему применять действие: к отмеченным, а если их нет — к выбранному. */
    private List<UUID> targets() {
        if (!checked.isEmpty()) {
            return List.copyOf(checked);
        }
        return selectedId == null ? List.of() : List.of(selectedId);
    }

    // --- Список ---------------------------------------------------------------

    private Optional<CoreRow> find(UUID id) {
        return rows.stream().filter(row -> row.id().equals(id)).findFirst();
    }

    private List<CoreRow> visible() {
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<CoreRow> filtered = new ArrayList<>();
        for (CoreRow row : rows) {
            if (needle.isEmpty()
                    || row.name().toLowerCase(Locale.ROOT).contains(needle)
                    || row.dimension().toString().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(row);
            }
        }
        Comparator<CoreRow> comparator = switch (sortKey) {
            case NAME -> Comparator.comparing(CoreRow::name, String.CASE_INSENSITIVE_ORDER);
            case PLACE -> Comparator.comparing((CoreRow row) -> row.dimension().toString())
                    .thenComparingInt(row -> row.pos().getX());
            // Сначала то, что требует внимания: боевые, потом закрытые замком.
            case STATE -> Comparator.comparing((CoreRow row) -> !row.config().armed())
                    .thenComparing(row -> !row.locked())
                    .thenComparing(CoreRow::name, String.CASE_INSENSITIVE_ORDER);
        };
        filtered.sort(sortAscending ? comparator : comparator.reversed());
        return filtered;
    }

    private void setAllChecked(boolean value) {
        checked.clear();
        if (value) {
            visible().forEach(row -> checked.add(row.id()));
        }
        refresh();
    }

    private void invertChecked() {
        Set<UUID> next = new LinkedHashSet<>();
        for (CoreRow row : visible()) {
            if (!checked.contains(row.id())) {
                next.add(row.id());
            }
        }
        checked.clear();
        checked.addAll(next);
        refresh();
    }

    // --- Отрисовка ------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, MARGIN, MARGIN, COLOR_TEXT);
        graphics.drawString(font, Component.translatable("gimpanum.dashboard.counts",
                rows.size(), checked.size()), MARGIN + LIST_WIDTH + MARGIN, MARGIN, COLOR_DIM);

        if (selectedId == null) {
            graphics.drawString(font, Component.translatable("gimpanum.dashboard.pick"),
                    rightX(), MARGIN + 20 + 2 * (TAB_HEIGHT + 2) + 6, COLOR_DIM);
            return;
        }
        renderTab(graphics);
    }

    private void renderTab(GuiGraphics graphics) {
        int x = rightX();
        int y = MARGIN + 20 + 2 * (TAB_HEIGHT + 2) + 6;
        CoreRow row = find(selectedId).orElse(null);
        if (row == null) {
            return;
        }

        switch (tab) {
            case OVERVIEW -> {
                graphics.drawString(font, Component.translatable("gimpanum.dashboard.name"),
                        x, y + 5, COLOR_TEXT);
                int line = y + TAB_HEIGHT + 8;
                graphics.drawString(font, Component.literal(row.dimension() + "  "
                        + row.pos().toShortString()), x, line, COLOR_DIM);
                line += LINE;
                graphics.drawString(font, row.loaded()
                                ? Component.translatable("gimpanum.dashboard.loaded")
                                : Component.translatable("gimpanum.dashboard.unloaded"),
                        x, line, row.loaded() ? COLOR_OK : COLOR_WARN);
                line += LINE;
                graphics.drawString(font, row.config().armed()
                                ? Component.translatable("gimpanum.core.armed")
                                : Component.translatable("gimpanum.core.safe"),
                        x, line, row.config().armed() ? COLOR_DANGER : COLOR_DIM);
                line += LINE;
                if (row.locked()) {
                    graphics.drawString(font, Component.translatable("gimpanum.dashboard.is_locked"),
                            x, line, COLOR_WARN);
                    line += LINE;
                }
                graphics.drawString(font, Component.literal(row.id().toString()), x, line, 0xFF606060);
            }
            case PLAYERS -> renderStrings(graphics, x, y, draft().boundPlayers());
            case COMMANDS -> {
                if (!mayEditCommands) {
                    graphics.drawString(font, Component.translatable("gimpanum.dashboard.needs_op"),
                            x, y, COLOR_WARN);
                    return;
                }
                renderStrings(graphics, x, y + 24, draft().commands());
            }
            case TEAMS -> {
                int line = y + TAB_HEIGHT + 6;
                for (BoundTeam team : draft().boundTeams()) {
                    graphics.drawString(font, Component.literal(team.teamName() + ": "
                            + String.join(", ", team.members())), x + 24, line + 5, COLOR_TEXT);
                    line += TAB_HEIGHT + 2;
                }
            }
            case SEAL -> {
                graphics.drawString(font, Component.translatable("gimpanum.dashboard.price"),
                        x, y + 29, COLOR_TEXT);
                graphics.drawString(font, Component.translatable("gimpanum.dashboard.postfix"),
                        x, y + 53, COLOR_TEXT);
            }
            case SPAWN -> {
                graphics.drawString(font, Component.translatable("gimpanum.dashboard.interval"),
                        x, y + 29, COLOR_TEXT);
                graphics.drawString(font, Component.translatable("gimpanum.dashboard.count"),
                        x, y + 53, COLOR_TEXT);
                graphics.drawString(font, Component.translatable("gimpanum.dashboard.spawn_item"),
                        x, y + 77, COLOR_TEXT);
            }
            case EXPLOSION -> graphics.drawString(font,
                    Component.translatable("gimpanum.dashboard.power"), x, y + 29, COLOR_TEXT);
            case TRANSFER -> graphics.drawString(font,
                    Component.translatable("gimpanum.dashboard.transfer_hint", checked.size()),
                    x, y + 4 * 22 + 8, COLOR_DIM);
            default -> {
            }
        }
    }

    private void renderStrings(GuiGraphics graphics, int x, int y, List<String> values) {
        int line = y + TAB_HEIGHT + 6;
        int bottom = height - MARGIN - 2 * (TAB_HEIGHT + 4);
        for (int i = pageOffset; i < values.size(); i++) {
            if (line + TAB_HEIGHT > bottom) {
                break;
            }
            graphics.drawString(font, font.plainSubstrByWidth(values.get(i), rightWidth() - 28),
                    x + 24, line + 5, COLOR_TEXT);
            line += TAB_HEIGHT + 2;
        }
        if (!values.isEmpty()) {
            // Счётчик у правого края шапки: слева от него поле ввода, кнопка
            // добавления и стрелки прокрутки.
            String counter = (pageOffset + 1) + "-"
                    + Math.min(values.size(), pageOffset + capacity(y, bottom))
                    + " / " + values.size();
            graphics.drawString(font, counter,
                    x + rightWidth() - font.width(counter), y + 5, COLOR_DIM);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Список Ядер с отметками. */
    private class CoreList extends ObjectSelectionList<CoreList.Row> {

        CoreList(int top, int listHeight) {
            super(CoreDashboardScreen.this.minecraft, LIST_WIDTH, listHeight, top, ROW_HEIGHT);
            setX(MARGIN);
            reload();
        }

        void reload() {
            clearEntries();
            for (CoreRow row : visible()) {
                Row entry = new Row(row);
                addEntry(entry);
                if (row.id().equals(selectedId)) {
                    setSelected(entry);
                }
            }
        }

        @Override
        public int getRowWidth() {
            return LIST_WIDTH - 12;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + LIST_WIDTH - 6;
        }

        /** Одна строка: отметка, имя со значками и место мелким шрифтом. */
        private class Row extends ObjectSelectionList.Entry<Row> {

            private final CoreRow data;

            Row(CoreRow data) {
                this.data = data;
            }

            @Override
            public Component getNarration() {
                return Component.literal(data.name());
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                               int rowHeight, int mouseX, int mouseY, boolean hovering, float partialTick) {
                boolean marked = checked.contains(data.id());
                // Только те знаки, что заведомо есть в шрифте игры. Стрелки,
                // флажки и молнии из блока геометрических символов в ванильном
                // шрифте отсутствуют и вышли бы пустыми рамками.
                graphics.drawString(font, marked ? "[x]" : "[ ]", left + 2, top + 8,
                        marked ? COLOR_OK : COLOR_DIM);

                int nameColor = data.locked() ? COLOR_WARN
                        : data.config().armed() ? COLOR_DANGER : COLOR_TEXT;
                String name = (isModified(data) ? "*" : "") + data.name();
                graphics.drawString(font, name, left + 24, top + 3, nameColor);

                StringBuilder badges = new StringBuilder();
                if (data.config().armed()) {
                    badges.append('!');
                }
                if (data.config().invulnerable()) {
                    badges.append('#');
                }
                if (data.locked()) {
                    badges.append('L');
                }
                if (!data.loaded()) {
                    badges.append('~');
                }
                if (!badges.isEmpty()) {
                    graphics.drawString(font, badges.toString(),
                            left + rowWidth - font.width(badges.toString()) - 4, top + 3, COLOR_DIM);
                }

                String place = data.dimension().getPath() + " " + data.pos().toShortString();
                graphics.drawString(font, font.plainSubstrByWidth(place, rowWidth - 28),
                        left + 24, top + 14, COLOR_DIM);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // Левый край строки — отметка, остальное — выбор. Так отмечать
                // можно не отрывая взгляда от списка, не сбивая при этом того,
                // чью настройку правят справа.
                if (mouseX < CoreList.this.getX() + 24) {
                    if (!checked.remove(data.id())) {
                        checked.add(data.id());
                    }
                    refresh();
                    return true;
                }
                select(data);
                return true;
            }
        }
    }
}
