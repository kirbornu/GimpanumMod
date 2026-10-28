package com.kirbornu.gimpanum.magic.tablet;

import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.core.TabletText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Сетка таблички: 7×7 ячеек под глифы, из них видны и работают столько,
 * сколько разрешают улучшения, и два слота улучшений.
 *
 * <p>Пока сетка открыта, рабочая копия содержимого — контейнер меню; после
 * каждого изменения она пишется обратно в табличку. Если улучшение вынули и
 * ячейки исчезли, глифы из них уходят в инвентарь игрока, а не сгорают и не
 * прячутся в невидимых ячейках, — а что не влезло, выпадает рядом.
 *
 * <p>Слот, в котором лежит сама табличка, заперт: вынуть её из-под открытой
 * сетки значило бы писать в предмет, которого у игрока уже нет.
 */
public class TabletMenu extends AbstractContainerMenu {

    public static final int GRID_X = 8;
    public static final int GRID_Y = 18;
    /** Справа от сетки 7×7 — колонка улучшений с подписями; панель поэтому шире ванильной. */
    public static final int UPGRADE_X = 158;
    public static final int UPGRADE_LABEL_X = 140;
    public static final int ROW_UPGRADE_Y = 36;
    public static final int COLUMN_UPGRADE_Y = 82;
    public static final int INVENTORY_X = 18;
    public static final int INVENTORY_Y = 158;
    public static final int WIDTH = 196;
    public static final int HEIGHT = 240;

    public static final int ROW_SLOT = TabletContents.CELLS;
    public static final int COLUMN_SLOT = TabletContents.CELLS + 1;
    private static final int CONTAINER_SIZE = TabletContents.CELLS + 2;
    private static final int PLAYER_START = CONTAINER_SIZE;
    private static final int PLAYER_END = PLAYER_START + 36;

    private final Player player;
    private final InteractionHand hand;
    private final ItemStack tablet;
    private final Item upgrade;
    private final SimpleContainer container = new SimpleContainer(CONTAINER_SIZE);
    private boolean syncing;

    /** Клиент: рука и предмет улучшения приходят от сервера. */
    public static TabletMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        String upgradeId = buffer.readUtf();
        ResourceLocation location = upgradeId.isEmpty() ? null : ResourceLocation.tryParse(upgradeId);
        Item upgrade = location == null ? null : BuiltInRegistries.ITEM.get(location);
        return new TabletMenu(id, inventory, hand, upgrade);
    }

    public TabletMenu(int id, Inventory inventory, InteractionHand hand, Item upgrade) {
        super(MagicContent.TABLET_MENU.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        this.tablet = player.getItemInHand(hand);
        this.upgrade = upgrade;

        TabletContents contents = TabletItem.contents(tablet);
        int[] cells = contents.cells();
        for (int i = 0; i < TabletContents.CELLS; i++) {
            if (cells[i] != TabletText.EMPTY) {
                container.setItem(i, new ItemStack(MagicContent.glyph(cells[i])));
            }
        }
        container.setItem(ROW_SLOT, contents.rowUpgrade());
        container.setItem(COLUMN_SLOT, contents.columnUpgrade());
        container.addListener(this::onContainerChanged);

        for (int row = 0; row < TabletContents.MAX_SIDE; row++) {
            for (int column = 0; column < TabletContents.MAX_SIDE; column++) {
                addSlot(new GlyphSlot(container, row, column));
            }
        }
        addSlot(new UpgradeSlot(container, ROW_SLOT, UPGRADE_X, ROW_UPGRADE_Y));
        addSlot(new UpgradeSlot(container, COLUMN_SLOT, UPGRADE_X, COLUMN_UPGRADE_Y));

        int locked = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(column == locked ? new LockedSlot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58)
                    : new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
    }

    public int rows() {
        return TabletContents.side(container.getItem(ROW_SLOT));
    }

    public int columns() {
        return TabletContents.side(container.getItem(COLUMN_SLOT));
    }

    private void onContainerChanged(Container changed) {
        if (syncing || player.level().isClientSide) {
            return;
        }
        syncing = true;
        try {
            ejectHiddenGlyphs();
            tablet.set(MagicContent.TABLET_CONTENTS.get(), snapshot());
        } finally {
            syncing = false;
        }
    }

    /** Глифы из исчезнувших ячеек — игроку в инвентарь или под ноги. */
    private void ejectHiddenGlyphs() {
        int rows = rows();
        int columns = columns();
        for (int row = 0; row < TabletContents.MAX_SIDE; row++) {
            for (int column = 0; column < TabletContents.MAX_SIDE; column++) {
                int index = TabletContents.index(row, column);
                if ((row >= rows || column >= columns) && !container.getItem(index).isEmpty()) {
                    player.getInventory().placeItemBackInInventory(container.removeItemNoUpdate(index));
                }
            }
        }
    }

    private TabletContents snapshot() {
        int[] cells = new int[TabletContents.CELLS];
        for (int i = 0; i < TabletContents.CELLS; i++) {
            cells[i] = container.getItem(i).getItem() instanceof GlyphItem glyph ? glyph.rune().index()
                    : TabletText.EMPTY;
        }
        return new TabletContents(cells, container.getItem(ROW_SLOT), container.getItem(COLUMN_SLOT));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && player.getItemInHand(hand) == tablet;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        // Цифрой можно поменять местами любой слот и слот хотбара — в том
        // числе тот, где лежит табличка; это запертый слот, и обходить его так нельзя.
        if (type == ClickType.SWAP && hand == InteractionHand.MAIN_HAND && button == player.getInventory().selected) {
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof GlyphItem) {
            if (!moveItemStackTo(stack, 0, TabletContents.CELLS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (upgrade != null && stack.is(upgrade)) {
            if (!moveItemStackTo(stack, ROW_SLOT, COLUMN_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Ячейка сетки: один глиф, и только пока ячейка видна. */
    public final class GlyphSlot extends Slot {
        private final int row;
        private final int column;

        private GlyphSlot(Container container, int row, int column) {
            super(container, TabletContents.index(row, column), GRID_X + column * 18, GRID_Y + row * 18);
            this.row = row;
            this.column = column;
        }

        @Override
        public boolean isActive() {
            return row < rows() && column < columns();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && stack.getItem() instanceof GlyphItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Слот улучшения: до пяти штук предмета из настройки. */
    private final class UpgradeSlot extends Slot {
        private UpgradeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return upgrade != null && stack.is(upgrade);
        }

        @Override
        public int getMaxStackSize() {
            return TabletContents.MAX_UPGRADES;
        }
    }

    /** Слот хотбара, где лежит открытая табличка. */
    private static final class LockedSlot extends Slot {
        private LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
