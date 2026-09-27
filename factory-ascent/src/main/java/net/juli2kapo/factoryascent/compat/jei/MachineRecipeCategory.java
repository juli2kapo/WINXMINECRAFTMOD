package net.juli2kapo.factoryascent.compat.jei;

import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

/** One JEI category per machine recipe kind; its catalysts are every machine that runs that kind. */
final class MachineRecipeCategory extends AbstractRecipeCategory<RecipeHolder<MachineRecipe>> {
    static final int WIDTH = 150;
    static final int HEIGHT = 46;

    private final RecipeKind kind;

    MachineRecipeCategory(IGuiHelper gui, RecipeKind kind, IRecipeType<RecipeHolder<MachineRecipe>> type) {
        super(type, Component.translatable("jei." + FactoryAscent.MOD_ID + "." + kind.id()),
                gui.createDrawableItemLike(ModBlocks.machine(icon(kind)).get()), WIDTH, HEIGHT);
        this.kind = kind;
    }

    /** The machines that run this kind, weakest first. */
    static List<MachineType> machines(RecipeKind kind) {
        return java.util.Arrays.stream(MachineType.VALUES)
                .filter(t -> t.recipeKind() == kind)
                .sorted(java.util.Comparator.comparingInt(MachineType::grade))
                .toList();
    }

    private static MachineType icon(RecipeKind kind) {
        List<MachineType> list = machines(kind);
        // The electric machine is the recognisable one where there is one.
        return list.stream().filter(MachineType::usesEnergy).findFirst().orElse(list.getFirst());
    }

    /** The first (weakest) machine good enough for the recipe, or null if none exists yet. */
    static @Nullable MachineType weakestFor(RecipeKind kind, int minGrade) {
        for (MachineType type : machines(kind)) {
            if (type.grade() >= minGrade) return type;
        }
        return null;
    }

    private int inputsX() {
        return kind.usesMold() ? 22 : 0;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        recipe.mold().ifPresent(mold -> builder.addInputSlot(1, 5)
                .setStandardSlotBackground()
                .add(mold)
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("gui.factoryascent.jei_mold").withStyle(ChatFormatting.GRAY))));
        int x = inputsX();
        for (SizedIngredient input : recipe.inputs()) {
            builder.addInputSlot(x + 1, 5)
                    .setStandardSlotBackground()
                    .addItemStacks(input.ingredient().items().map(item -> new net.minecraft.world.item.ItemStack(item, input.count())).toList());
            x += 18;
        }
        int outX = Math.max(x, 36) + 28;
        builder.addOutputSlot(outX + 1, 5).setOutputSlotBackground().add(recipe.result());
        recipe.byproduct().ifPresent(chance -> builder.addOutputSlot(outX + 23, 5)
                .setStandardSlotBackground()
                .add(chance.item())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("gui.factoryascent.jei_chance", Math.round(chance.chance() * 100))
                                .withStyle(ChatFormatting.GOLD))));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        int x = inputsX() + 18 * recipe.inputs().size();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(Math.max(x, 36) + 3, 5);

        String seconds = String.format(java.util.Locale.ROOT, "%.1f", recipe.time() / 20f);
        builder.addText(Component.translatable("gui.factoryascent.jei_time", seconds), WIDTH, 9)
                .setPosition(0, 27).setColor(0xFF606060);

        recipe.byproduct().ifPresent(chance -> builder.addText(
                        Component.literal(Math.round(chance.chance() * 100) + "%"), 18, 9)
                .setPosition(Math.max(x, 36) + 51, 25).setColor(0xFFB07000));

        MachineType weakest = weakestFor(kind, recipe.minGrade());
        MachineType first = machines(kind).getFirst();
        if (weakest != first) {
            Component needs = weakest == null
                    ? Component.translatable("gui.factoryascent.jei_needs_later")
                    : Component.translatable("gui.factoryascent.jei_needs", ModBlocks.machine(weakest).get().getName());
            Component full = weakest == null
                    ? Component.translatable("gui.factoryascent.jei_needs_later_long", recipe.minGrade())
                    : Component.translatable("gui.factoryascent.jei_needs_long", ModBlocks.machine(weakest).get().getName());
            builder.addText(needs, WIDTH, 9).setPosition(0, 37).setColor(0xFFA02020).setTooltip(full);
        }
    }
}
