package net.juli2kapo.factoryascent.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

/**
 * The "what does this machine take?" panel docked to the left of a machine GUI: a slot grid of
 * every input the machine accepts, then (greyed) the ones that need a better machine of the same
 * kind. Hovering a slot explains what it turns into. Works without JEI.
 */
final class AcceptsPanel {
    static final int COLUMNS = 5;
    static final int WIDTH = 7 + COLUMNS * 18 + 4 + 6 + 5;
    private static final int GRID_X = 7, GRID_Y = 17, SCROLL_W = 6;

    /** One grid cell: an input (cycling through a tag's items) and every recipe it feeds. */
    private record Cell(List<Holder<Item>> items, int count, List<Use> uses, boolean locked) {}

    /** One recipe as seen from one of its inputs. */
    private record Use(ItemStack result, @Nullable ItemStack byproduct, float chance,
                       List<ItemStack> with, @Nullable ItemStack mold, int ticks, int grade) {}

    /** A row of the scrollable list: either a section header or up to {@link #COLUMNS} cells. */
    private record Line(@Nullable Component header, List<Cell> cells, int color) {}

    private final MachineType type;
    private final List<Line> lines = new ArrayList<>();
    private final int acceptedCount;
    private int scroll;

    AcceptsPanel(MachineType type) {
        this.type = type;
        Map<List<Holder<Item>>, List<Use>> open = new LinkedHashMap<>();
        Map<List<Holder<Item>>, List<Use>> locked = new LinkedHashMap<>();
        Map<List<Holder<Item>>, Integer> counts = new LinkedHashMap<>();
        for (MachineRecipe r : ClientRecipes.recipesFor(type)) {
            boolean isLocked = r.minGrade() > type.grade();
            ItemStack result = r.result().create();
            ItemStack by = r.byproduct().map(b -> b.item().create()).orElse(null);
            float chance = r.byproduct().map(b -> b.chance()).orElse(0f);
            ItemStack mold = r.mold().map(m -> first(m.items().toList())).orElse(null);
            for (int i = 0; i < r.inputs().size(); i++) {
                SizedIngredient in = r.inputs().get(i);
                List<ItemStack> with = new ArrayList<>();
                for (int j = 0; j < r.inputs().size(); j++) {
                    if (j == i) continue;
                    ItemStack other = first(r.inputs().get(j).ingredient().items().toList());
                    other.setCount(r.inputs().get(j).count());
                    with.add(other);
                }
                List<Holder<Item>> key = in.ingredient().items().toList();
                if (key.isEmpty()) continue;
                (isLocked ? locked : open).computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new Use(result, by, chance, with, mold, r.time(), r.minGrade()));
                counts.merge(key, in.count(), Math::min);
            }
        }
        if (type == MachineType.ELECTRIC_FURNACE) {
            for (SmeltingRecipe r : ClientRecipes.vanillaSmelting()) {
                List<Holder<Item>> key = r.input().items().toList();
                if (key.isEmpty() || open.containsKey(key)) continue;
                ItemStack out = smelt(r, key);
                if (out.isEmpty()) continue;
                open.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new Use(out, null, 0, List.of(), null, r.cookingTime() / 5, 1));
                counts.putIfAbsent(key, 1);
            }
        }
        // The machine always runs the best recipe it can for an input (see ProcessingMachineBlockEntity),
        // so show only those; weaker recipes belong to weaker machines.
        open.replaceAll((k, uses) -> {
            int best = uses.stream().mapToInt(Use::grade).max().orElse(1);
            return uses.stream().filter(u -> u.grade() == best).toList();
        });
        locked.replaceAll((k, uses) -> {
            int lowest = uses.stream().mapToInt(Use::grade).min().orElse(1);
            return uses.stream().filter(u -> u.grade() == lowest).toList();
        });
        // An input this machine can already use is not "locked", even if a better machine does more with it.
        locked.keySet().removeAll(open.keySet());
        acceptedCount = open.size();
        addSection(null, open, counts, false);
        if (!locked.isEmpty()) {
            addSection(Component.translatable("gui.factoryascent.needs_better_machine"), locked, counts, true);
        }
    }

    private void addSection(@Nullable Component header, Map<List<Holder<Item>>, List<Use>> cells,
                            Map<List<Holder<Item>>, Integer> counts, boolean lockedSection) {
        if (!lines.isEmpty()) lines.add(new Line(Component.empty(), List.of(), 0));
        if (header != null) lines.add(new Line(header, List.of(), lockedSection ? 0xFF8A2020 : 0xFF404040));
        List<Cell> row = new ArrayList<>();
        for (var e : cells.entrySet()) {
            row.add(new Cell(e.getKey(), counts.getOrDefault(e.getKey(), 1), e.getValue(), lockedSection));
            if (row.size() == COLUMNS) {
                lines.add(new Line(null, row, 0));
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) lines.add(new Line(null, row, 0));
        if (cells.isEmpty()) lines.add(new Line(Component.translatable("gui.factoryascent.no_recipes"), List.of(), 0xFF707070));
    }

    private static ItemStack smelt(SmeltingRecipe r, List<Holder<Item>> items) {
        try {
            return r.assemble(new SingleRecipeInput(new ItemStack(items.getFirst())));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack first(List<Holder<Item>> items) {
        return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.getFirst());
    }

    private static ItemStack cycle(List<Holder<Item>> items, long tick, int count) {
        ItemStack stack = new ItemStack(items.get((int) ((tick / 20) % items.size())));
        stack.setCount(count);
        return stack;
    }

    int acceptedCount() {
        return acceptedCount;
    }

    private int visibleRows(int height) {
        return (height - GRID_Y - 7) / 18;
    }

    private int maxScroll(int height) {
        return Math.max(0, lines.size() - visibleRows(height));
    }

    void scroll(double amount, int height) {
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, maxScroll(height));
    }

    /**
     * Draws the panel with its top-left at (x, y) and returns the cell under the mouse, so the
     * screen can show its tooltip after everything else is drawn.
     */
    @Nullable Object render(GuiGraphicsExtractor g, Font font, int x, int y, int height, int accent,
                            int mouseX, int mouseY, long tick) {
        MachineScreen.panel(g, x, y, WIDTH, height);
        g.fill(x + 3, y + 3, x + WIDTH - 3, y + 5, accent);
        Component title = Component.translatable("gui.factoryascent.accepts", acceptedCount);
        g.text(font, title, x + 7, y + 7, 0xFF404040, false);

        int rows = visibleRows(height);
        Cell hovered = null;
        for (int i = 0; i < rows && i + scroll < lines.size(); i++) {
            Line line = lines.get(i + scroll);
            int ry = y + GRID_Y + i * 18;
            if (line.header() != null) {
                if (line.header().getString().isEmpty()) {
                    // Divider between sections.
                    g.fill(x + GRID_X, ry + 9, x + GRID_X + COLUMNS * 18, ry + 10, 0xFF8B8B8B);
                    g.fill(x + GRID_X, ry + 10, x + GRID_X + COLUMNS * 18, ry + 11, 0xFFFFFFFF);
                } else {
                    g.text(font, line.header(), x + GRID_X + 1, ry + 9, line.color(), false);
                }
                continue;
            }
            for (int c = 0; c < line.cells().size(); c++) {
                Cell cell = line.cells().get(c);
                int sx = x + GRID_X + c * 18, sy = ry;
                slotBack(g, sx, sy);
                ItemStack stack = cycle(cell.items(), tick + c * 7L, cell.count());
                g.item(stack, sx + 1, sy + 1);
                g.itemDecorations(font, stack, sx + 1, sy + 1);
                if (cell.locked()) g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xA0505050);
                boolean hover = mouseX >= sx + 1 && mouseX < sx + 17 && mouseY >= sy + 1 && mouseY < sy + 17;
                if (hover) {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x80FFFFFF);
                    hovered = cell;
                }
            }
        }
        // Scrollbar.
        int barX = x + GRID_X + COLUMNS * 18 + 3, barY = y + GRID_Y, barH = rows * 18;
        g.fill(barX, barY, barX + SCROLL_W, barY + barH, 0xFF373737);
        int max = maxScroll(height);
        int knobH = max == 0 ? barH : Math.max(10, barH * rows / lines.size());
        int knobY = max == 0 ? barY : barY + (barH - knobH) * scroll / max;
        g.fill(barX, knobY, barX + SCROLL_W, knobY + knobH, max == 0 ? 0xFF8B8B8B : 0xFFC6C6C6);
        g.fill(barX + SCROLL_W - 1, knobY, barX + SCROLL_W, knobY + knobH, 0xFF555555);
        return hovered;
    }

    private static void slotBack(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** Tooltip for a hovered cell: the item's name, then what each recipe turns it into. */
    List<Component> tooltip(Object hovered, long tick) {
        Cell cell = (Cell) hovered;
        List<Component> out = new ArrayList<>();
        ItemStack shown = cycle(cell.items(), tick, cell.count());
        out.add(shown.getHoverName().copy().withStyle(ChatFormatting.WHITE));
        for (Use use : cell.uses()) {
            MutableComponent line = Component.literal("→ ").withStyle(ChatFormatting.GRAY)
                    .append(stackName(use.result()).withStyle(ChatFormatting.GREEN));
            if (use.byproduct() != null) {
                line.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
                        .append(stackName(use.byproduct()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" (" + Math.round(use.chance() * 100) + "%)").withStyle(ChatFormatting.GOLD));
            }
            out.add(line);
            for (ItemStack with : use.with()) {
                out.add(Component.translatable("gui.factoryascent.with", stackName(with)).withStyle(ChatFormatting.DARK_AQUA));
            }
            if (use.mold() != null) {
                out.add(Component.translatable("gui.factoryascent.with_mold", use.mold().getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
            }
            out.add(Component.translatable("gui.factoryascent.jei_time",
                    String.format(java.util.Locale.ROOT, "%.1f", use.ticks() / 20f / Math.max(0.01f, type.speed())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (cell.locked()) {
            MachineType needed = weakestFor(cell.uses().getFirst().grade());
            out.add(needed == null
                    ? Component.translatable("gui.factoryascent.jei_needs_later_long", cell.uses().getFirst().grade()).withStyle(ChatFormatting.RED)
                    : Component.translatable("gui.factoryascent.jei_needs_long",
                            Component.translatable("block.factoryascent." + needed.id())).withStyle(ChatFormatting.RED));
        }
        return out;
    }

    /** The weakest machine of this machine's kind that can run a recipe of the given grade. */
    private @Nullable MachineType weakestFor(int grade) {
        MachineType best = null;
        for (MachineType t : MachineType.VALUES) {
            if (t.recipeKind() == type.recipeKind() && t.grade() >= grade && (best == null || t.grade() < best.grade())) best = t;
        }
        return best;
    }

    private static MutableComponent stackName(ItemStack stack) {
        MutableComponent name = stack.getHoverName().copy();
        return stack.getCount() > 1 ? Component.literal(stack.getCount() + "× ").append(name) : name;
    }
}
