package com.kirbornu.gimpanum.recipe;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.config.JsonConfig;
import com.kirbornu.gimpanum.item.ItemConfig;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import java.util.stream.Stream;

/**
 * Огранка — незеритовый шаблон, доведённый до предела: он поднимал алмаз до
 * незерита, огранка поднимает незерит выше.
 *
 * <p>Огранённая вещь неразрушима, светится и получает прибавку сверх
 * незерита: броня — жёсткость, мечи и топоры — урон, инструменты — скорость
 * копания. Имя, чары и всё прочее переходят с неё как есть.
 *
 * <p>Не обычный рецепт превращения из ванили: тот переписал бы атрибуты
 * вещи готовым списком, одним на все. Здесь прибавка ложится поверх того, что
 * у вещи уже есть. Огранить дважды нельзя — повторная огранка сожгла бы
 * шаблон впустую.
 */
public class FacetingRecipe implements SmithingRecipe {

    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;

    public FacetingRecipe(Ingredient template, Ingredient base, Ingredient addition) {
        this.template = template;
        this.base = base;
        this.addition = addition;
    }

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return template.test(input.template()) && base.test(input.base()) && addition.test(input.addition())
                && !input.base().has(GimpanumContent.FACETED.get());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        return facet(input.base().copyWithCount(1));
    }

    private static ItemStack facet(ItemStack stack) {
        JsonConfig.Section config = ItemConfig.of("faceting");
        Item item = stack.getItem();
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        if (modifiers.modifiers().isEmpty()) {
            modifiers = item.getDefaultAttributeModifiers(stack);
        }
        if (item instanceof ArmorItem armor) {
            EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armor.getEquipmentSlot());
            modifiers = modifiers.withModifierAdded(Attributes.ARMOR_TOUGHNESS,
                    modifier(group, config.number("armor_toughness"), AttributeModifier.Operation.ADD_VALUE), group);
        }
        if (item instanceof SwordItem || item instanceof AxeItem) {
            modifiers = modifiers.withModifierAdded(Attributes.ATTACK_DAMAGE,
                    modifier(EquipmentSlotGroup.MAINHAND, config.number("attack_damage"),
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        if (item instanceof DiggerItem) {
            modifiers = modifiers.withModifierAdded(Attributes.BLOCK_BREAK_SPEED,
                    modifier(EquipmentSlotGroup.MAINHAND, config.number("block_break_speed"),
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        stack.setDamageValue(0);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(GimpanumContent.FACETED.get(), Unit.INSTANCE);
        return stack;
    }

    /** Имя модификатора своё на каждое место: иначе огранённые шлем и сапоги перебивали бы друг друга. */
    private static AttributeModifier modifier(EquipmentSlotGroup group, double amount,
                                              AttributeModifier.Operation operation) {
        return new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath(Gimpanum.MOD_ID, "faceting." + group.getSerializedName()),
                amount, operation);
    }

    /** Образец для книги рецептов и просмотрщиков: огранённый нагрудник. */
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return facet(new ItemStack(Items.NETHERITE_CHESTPLATE));
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public boolean isIncomplete() {
        return Stream.of(template, base, addition).anyMatch(Ingredient::hasNoItems);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GimpanumContent.FACETING.get();
    }

    public static class Serializer implements RecipeSerializer<FacetingRecipe> {

        private static final MapCodec<FacetingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("template").forGetter(recipe -> recipe.template),
                Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
                Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition)
        ).apply(instance, FacetingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FacetingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.template,
                        Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.base,
                        Ingredient.CONTENTS_STREAM_CODEC, recipe -> recipe.addition,
                        FacetingRecipe::new);

        @Override
        public MapCodec<FacetingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FacetingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
