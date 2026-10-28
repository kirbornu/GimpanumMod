package com.kirbornu.gimpanum.magic;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.core.Rune;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import com.kirbornu.gimpanum.magic.tablet.GlyphItem;
import com.kirbornu.gimpanum.magic.tablet.TabletContents;
import com.kirbornu.gimpanum.magic.tablet.TabletItem;
import com.kirbornu.gimpanum.magic.tablet.TabletMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Всё, что рунная магия регистрирует в игре.
 *
 * <p>Отдельно от {@link com.kirbornu.gimpanum.registry.GimpanumContent}:
 * магия — самостоятельная система со своей вкладкой творческого режима, и её
 * регистрации удобнее видеть вместе, а не вперемешку с туманностями и
 * конвертерами.
 */
public final class MagicContent {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Gimpanum.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gimpanum.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Gimpanum.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Gimpanum.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gimpanum.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Gimpanum.MOD_ID);

    /** Глифы по рунам: {@code glyph_an}, {@code glyph_be} и так далее. */
    public static final Map<Rune, DeferredItem<GlyphItem>> GLYPHS = new EnumMap<>(Rune.class);

    static {
        for (Rune rune : Rune.values()) {
            GLYPHS.put(rune, ITEMS.registerItem("glyph_" + rune.id(),
                    properties -> new GlyphItem(rune, properties), new Item.Properties().stacksTo(64)));
        }
    }

    public static final Supplier<DataComponentType<TabletContents>> TABLET_CONTENTS =
            DATA_COMPONENTS.register("tablet_contents",
                    () -> DataComponentType.<TabletContents>builder()
                            .persistent(TabletContents.CODEC)
                            .networkSynchronized(TabletContents.STREAM_CODEC)
                            .build());

    /** Без содержимого по умолчанию: пустая табличка читается как {@link TabletContents#EMPTY}. */
    public static final DeferredItem<TabletItem> TABLET = ITEMS.registerItem("rune_tablet", TabletItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredHolder<MenuType<?>, MenuType<TabletMenu>> TABLET_MENU =
            MENUS.register("rune_tablet", () -> IMenuTypeExtension.create(TabletMenu::fromNetwork));

    /** Невидимая сущность-заклинание. Крошечная: её не видно и с ней ничто не сталкивается. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpellEntity>> SPELL =
            ENTITIES.register("spell", () -> EntityType.Builder
                    .<SpellEntity>of(SpellEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(4)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .build("spell"));

    /** До какого игрового тика игрок не может колдовать. Ноль — может. */
    public static final Supplier<AttachmentType<Long>> COOLDOWN = ATTACHMENTS.register("magic_cooldown",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("magic",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gimpanum.magic"))
                    .icon(() -> new ItemStack(TABLET.get()))
                    .displayItems((params, output) -> {
                        output.accept(TABLET.get());
                        for (DeferredItem<GlyphItem> glyph : GLYPHS.values()) {
                            output.accept(glyph.get());
                        }
                    })
                    .build());

    private MagicContent() {
    }

    public static GlyphItem glyph(int rune) {
        return GLYPHS.get(Rune.byIndex(rune)).get();
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        MENUS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
