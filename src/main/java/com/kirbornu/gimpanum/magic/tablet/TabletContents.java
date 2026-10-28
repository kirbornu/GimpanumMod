package com.kirbornu.gimpanum.magic.tablet;

import com.kirbornu.gimpanum.magic.core.Rune;
import com.kirbornu.gimpanum.magic.core.TabletText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Что лежит в табличке: глифы по ячейкам и улучшения.
 *
 * <p>Ячейки хранятся на полной сетке 7×7 по строкам, а не на текущей. Так
 * глиф не переезжает, когда добавляется столбец: ячейка (строка 1, столбец 1)
 * — всегда одно и то же место. В ячейке — индекс руны, а не предмет: в
 * ячейку кладут только глиф и только один, и хранить ради этого целый
 * {@link ItemStack} незачем.
 *
 * <p>Размер сетки — это 2 плюс число улучшений в каждом из двух слотов; оба
 * слота хранятся здесь же, поэтому размер живёт в компоненте и не
 * рассинхронизируется со своей причиной.
 *
 * <p>Класс, а не запись: {@link ItemStack} не умеет {@code equals}, а
 * компонент обязан сравниваться по содержимому — иначе две одинаковые
 * таблички не считались бы одинаковыми.
 */
public final class TabletContents {

    public static final int MAX_SIDE = 7;
    public static final int BASE_SIDE = 2;
    public static final int MAX_UPGRADES = MAX_SIDE - BASE_SIDE;
    public static final int CELLS = MAX_SIDE * MAX_SIDE;

    public static final TabletContents EMPTY = new TabletContents(emptyCells(), ItemStack.EMPTY, ItemStack.EMPTY);

    public static final Codec<TabletContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().fieldOf("cells").forGetter(TabletContents::cellList),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("rows", ItemStack.EMPTY).forGetter(TabletContents::rowUpgrade),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("columns", ItemStack.EMPTY).forGetter(TabletContents::columnUpgrade)
    ).apply(instance, TabletContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TabletContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), TabletContents::cellList,
            ItemStack.OPTIONAL_STREAM_CODEC, TabletContents::rowUpgrade,
            ItemStack.OPTIONAL_STREAM_CODEC, TabletContents::columnUpgrade,
            TabletContents::new);

    private final int[] cells;
    private final ItemStack rowUpgrade;
    private final ItemStack columnUpgrade;

    private TabletContents(List<Integer> cells, ItemStack rowUpgrade, ItemStack columnUpgrade) {
        this.cells = new int[CELLS];
        Arrays.fill(this.cells, TabletText.EMPTY);
        for (int i = 0; i < Math.min(CELLS, cells.size()); i++) {
            int rune = cells.get(i);
            // Испорченное значение — пустая ячейка, а не падение при загрузке мира.
            this.cells[i] = rune >= 0 && rune < Rune.count() ? rune : TabletText.EMPTY;
        }
        this.rowUpgrade = rowUpgrade.copy();
        this.columnUpgrade = columnUpgrade.copy();
    }

    public TabletContents(int[] cells, ItemStack rowUpgrade, ItemStack columnUpgrade) {
        this(Arrays.stream(cells).boxed().toList(), rowUpgrade, columnUpgrade);
    }

    private static List<Integer> emptyCells() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < CELLS; i++) {
            out.add(TabletText.EMPTY);
        }
        return out;
    }

    private List<Integer> cellList() {
        return Arrays.stream(cells).boxed().toList();
    }

    public static int index(int row, int column) {
        return row * MAX_SIDE + column;
    }

    public int cell(int row, int column) {
        return cells[index(row, column)];
    }

    public int[] cells() {
        return cells.clone();
    }

    public ItemStack rowUpgrade() {
        return rowUpgrade.copy();
    }

    public ItemStack columnUpgrade() {
        return columnUpgrade.copy();
    }

    public static int side(ItemStack upgrade) {
        return BASE_SIDE + Math.min(MAX_UPGRADES, upgrade.getCount());
    }

    public int rows() {
        return side(rowUpgrade);
    }

    public int columns() {
        return side(columnUpgrade);
    }

    /** Рунописи таблички: только видимая часть сетки, построчно. */
    public List<int[]> runescripts() {
        int rows = rows();
        int columns = columns();
        int[] visible = new int[rows * columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                visible[r * columns + c] = cell(r, c);
            }
        }
        return TabletText.read(visible);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TabletContents that && Arrays.equals(cells, that.cells)
                && ItemStack.matches(rowUpgrade, that.rowUpgrade) && ItemStack.matches(columnUpgrade, that.columnUpgrade);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(cells) + ItemStack.hashItemAndComponents(rowUpgrade) + rowUpgrade.getCount())
                + ItemStack.hashItemAndComponents(columnUpgrade) + columnUpgrade.getCount();
    }
}
