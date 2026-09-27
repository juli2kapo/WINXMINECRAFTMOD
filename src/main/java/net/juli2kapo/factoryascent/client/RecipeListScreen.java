package net.juli2kapo.factoryascent.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

/**
 * "What can this machine do?" — every recipe of a machine, opened with the {@code ?} button. Recipes
 * that need a better machine are shown dimmed with the grade they need. Works without JEI.
 */
public class RecipeListScreen extends Screen {
    private static final int W = 200, ROW = 22, VISIBLE = 8;
    private final MachineType type;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    private record Row(List<Ingredient> inputs, List<Integer> counts, @Nullable Ingredient mold, ItemStack result,
                       @Nullable ItemStack byproduct, float chance, boolean locked) {}

    public RecipeListScreen(MachineType type) {
        super(Component.translatable("gui.factoryascent.recipes_for", Component.translatable("block.factoryascent." + type.id())));
        this.type = type;
        for (MachineRecipe r : ClientRecipes.recipesFor(type)) {
            rows.add(new Row(r.inputs().stream().map(SizedIngredient::ingredient).toList(),
                    r.inputs().stream().map(SizedIngredient::count).toList(),
                    r.mold().orElse(null), r.result().create(),
                    r.byproduct().map(b -> b.item().create()).orElse(null),
                    r.byproduct().map(b -> b.chance()).orElse(0f), r.minGrade() > type.grade()));
        }
        if (type == MachineType.ELECTRIC_FURNACE) {
            for (SmeltingRecipe r : ClientRecipes.vanillaSmelting()) {
                ItemStack out = resultOf(r);
                if (!out.isEmpty()) rows.add(new Row(List.of(r.input()), List.of(1), null, out, null, 0, false));
            }
        }
    }

    private static ItemStack resultOf(SmeltingRecipe r) {
        try {
            return r.assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(firstItem(r.input(), 0)));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack firstItem(Ingredient ingredient, long tick) {
        List<Holder<Item>> items = ingredient.items().toList();
        if (items.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(items.get((int) ((tick / 20) % items.size())));
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - (VISIBLE * ROW + 30)) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        int h = VISIBLE * ROW + 30;
        g.fill(x - 1, y - 1, x + W + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + W, y + h, 0xFFC6C6C6);
        g.text(font, title, x + 6, y + 6, 0xFF404040, false);
        long tick = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        if (rows.isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.no_recipes"), x + 6, y + 24, 0xFF606060, false);
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < VISIBLE && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int ry = y + 20 + i * ROW;
            g.fill(x + 4, ry, x + W - 4, ry + ROW - 2, row.locked() ? 0xFFA8A8A8 : 0xFFB8B8B8);
            int cx = x + 6;
            for (int k = 0; k < row.inputs().size(); k++) {
                ItemStack stack = firstItem(row.inputs().get(k), tick + k * 7L);
                stack.setCount(row.counts().get(k));
                g.item(stack, cx, ry + 2);
                g.itemDecorations(font, stack, cx, ry + 2);
                if (hit(mouseX, mouseY, cx, ry + 2)) hovered = stack;
                cx += 18;
            }
            if (row.mold() != null) {
                ItemStack mold = firstItem(row.mold(), tick);
                g.text(font, "+", cx, ry + 6, 0xFF404040, false);
                g.item(mold, cx + 7, ry + 2);
                if (hit(mouseX, mouseY, cx + 7, ry + 2)) hovered = mold;
                cx += 25;
            }
            g.text(font, "→", cx + 2, ry + 6, 0xFF404040, false);
            cx += 14;
            g.item(row.result(), cx, ry + 2);
            g.itemDecorations(font, row.result(), cx, ry + 2);
            if (hit(mouseX, mouseY, cx, ry + 2)) hovered = row.result();
            cx += 18;
            if (row.byproduct() != null) {
                g.item(row.byproduct(), cx, ry + 2);
                g.text(font, Math.round(row.chance() * 100) + "%", cx + 17, ry + 6, 0xFF404040, false);
                if (hit(mouseX, mouseY, cx, ry + 2)) hovered = row.byproduct();
            }
            if (row.locked()) {
                Component lock = Component.translatable("gui.factoryascent.needs_better").withStyle(ChatFormatting.DARK_RED);
                g.text(font, lock, x + W - 6 - font.width(lock), ry + 6, 0xFF8A2020, false);
            }
        }
        if (rows.size() > VISIBLE) {
            String page = (scroll + 1) + "-" + Math.min(rows.size(), scroll + VISIBLE) + " / " + rows.size();
            g.text(font, page, x + W - 6 - font.width(page), y + 6, 0xFF606060, false);
        }
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    private static boolean hit(int mx, int my, int x, int y) {
        return mx >= x && mx < x + 16 && my >= y && my < y + 16;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - VISIBLE), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = left(), y = top();
        if (event.x() < x || event.x() > x + W || event.y() < y || event.y() > y + VISIBLE * ROW + 30) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.popScreenLayer();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
