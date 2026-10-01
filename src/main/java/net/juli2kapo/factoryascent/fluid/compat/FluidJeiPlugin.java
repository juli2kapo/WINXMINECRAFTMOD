package net.juli2kapo.factoryascent.fluid.compat;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.fluid.CellFluidHandler;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidsConfig;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.machine.RefineryBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.SteamTurbineBlockEntity;
import net.juli2kapo.factoryascent.fusion.ElectrolyzerBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * JEI: one "Fluid Machines" category listing what every fluid machine (and every machine that
 * gained fluid ports) takes and makes, with real fluid ingredients, so "U" on Crude Oil or
 * Steam shows where it goes and "R" shows where it comes from.
 */
@JeiPlugin
public final class FluidJeiPlugin implements IModPlugin {
    public record FluidIn(Fluid fluid, int amount) {}

    /** One line of the category: a machine, its inputs and outputs, and a note (FE, per tick...). */
    public record Process(ItemLike machine, List<ItemStack> itemsIn, List<FluidIn> fluidsIn, List<ItemStack> itemsOut,
                          List<FluidIn> fluidsOut, Component note) {}

    static final IRecipeType<Process> TYPE = IRecipeType.create(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "fluid_processes"), Process.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "jei_fluids");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    private static Component perTick(String fe) {
        return Component.literal(fe);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Process> list = new ArrayList<>();
        Fluid steam = ModFluids.STEAM.source(), water = Fluids.WATER;
        int fePerMb = FluidsConfig.get(FluidsConfig.STEAM_FE_PER_MB);
        int boil = FluidsConfig.get(FluidsConfig.BOILER_STEAM);
        list.add(new Process(FluidContent.machine(FluidMachine.BOILER).get(), List.of(new ItemStack(Items.COAL)), List.of(new FluidIn(water, boil)),
                List.of(), List.of(new FluidIn(steam, boil)), Component.translatable("jei.factoryascent.fluid_note.boiler")));
        int engine = PowerConfig.get(PowerConfig.STEAM_ENGINE_OUTPUT);
        list.add(new Process(PowerContent.generator(Generator.STEAM_ENGINE).get(), List.of(), List.of(new FluidIn(steam, engine / fePerMb)),
                List.of(), List.of(), perTick(engine + " FE/t")));
        int t = SteamTurbineBlockEntity.maxSteam();
        list.add(new Process(FluidContent.machine(FluidMachine.STEAM_TURBINE).get(), List.of(), List.of(new FluidIn(steam, t)),
                List.of(), List.of(), perTick(t * fePerMb + " FE/t")));
        list.add(new Process(PowerContent.REACTOR_COOLANT_PORT.get(), List.of(), List.of(new FluidIn(water, 250)), List.of(),
                List.of(new FluidIn(steam, 1000)), perTick("1 heat → " + FluidsConfig.get(FluidsConfig.REACTOR_STEAM_PER_HEAT) + " mB")));
        list.add(new Process(PowerContent.REACTOR_COOLANT_PORT.get(), List.of(), List.of(new FluidIn(ModFluids.COOLANT.source(), 250)), List.of(),
                List.of(new FluidIn(steam, 4000)), perTick("×4")));
        for (Fluid f : List.of(water, Fluids.LAVA, ModFluids.CRUDE_OIL.source())) {
            list.add(new Process(FluidContent.machine(FluidMachine.PUMP).get(), List.of(), List.of(), List.of(), List.of(new FluidIn(f, 1000)),
                    perTick(FluidsConfig.get(FluidsConfig.PUMP_ENERGY) + " FE")));
        }
        list.add(new Process(FluidContent.machine(FluidMachine.BIOGAS_DIGESTER).get(),
                List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.PUMPKIN)), List.of(), List.of(),
                List.of(new FluidIn(ModFluids.BIOGAS.source(), 340)), Component.translatable("jei.factoryascent.fluid_note.per_item")));
        list.add(new Process(PowerContent.generator(Generator.BIOGAS_GENERATOR).get(), List.of(), List.of(new FluidIn(ModFluids.BIOGAS.source(), 2)),
                List.of(), List.of(), perTick(PowerConfig.get(PowerConfig.BIOGAS_OUTPUT) + " FE/t")));
        list.add(new Process(PowerContent.generator(Generator.MAGMATIC_GENERATOR).get(), List.of(), List.of(new FluidIn(Fluids.LAVA, 1)),
                List.of(), List.of(), perTick(PowerConfig.get(PowerConfig.MAGMATIC_OUTPUT) + " FE/t")));
        list.add(new Process(FluidContent.machine(FluidMachine.DIESEL_GENERATOR).get(), List.of(),
                List.of(new FluidIn(ModFluids.DIESEL.source(), FluidsConfig.get(FluidsConfig.DIESEL_PER_TICK))), List.of(), List.of(),
                perTick(FluidsConfig.get(FluidsConfig.DIESEL_OUTPUT) + " FE/t")));
        list.add(new Process(FluidContent.machine(FluidMachine.OIL_DERRICK).get(), List.of(), List.of(), List.of(),
                List.of(new FluidIn(ModFluids.CRUDE_OIL.source(), 1000)), perTick(FluidsConfig.get(FluidsConfig.DERRICK_ENERGY) + " FE/t")));
        list.add(new Process(FluidContent.machine(FluidMachine.REFINERY).get(), List.of(),
                List.of(new FluidIn(ModFluids.CRUDE_OIL.source(), RefineryBlockEntity.BATCH)),
                List.of(new ItemStack(FluidContent.PLASTIC.get(), RefineryBlockEntity.PLASTIC), new ItemStack(FluidContent.TAR.get(), RefineryBlockEntity.TAR)),
                List.of(new FluidIn(ModFluids.DIESEL.source(), RefineryBlockEntity.DIESEL), new FluidIn(ModFluids.ROCKET_FUEL.source(), RefineryBlockEntity.ROCKET_FUEL)),
                perTick(FluidsConfig.get(FluidsConfig.REFINERY_ENERGY) + " FE/t")));
        list.add(new Process(ModBlocks.machine(MachineType.ELECTROLYZER).get(), List.of(), List.of(new FluidIn(water, ElectrolyzerBlockEntity.RATE)),
                List.of(), List.of(new FluidIn(ModFluids.DEUTERIUM.source(), ElectrolyzerBlockEntity.RATE)),
                Component.translatable("jei.factoryascent.fluid_note.per_tick")));
        list.add(new Process(ModBlocks.machine(MachineType.ELECTROLYZER).get(), List.of(),
                List.of(new FluidIn(ModFluids.DEUTERIUM.source(), ElectrolyzerBlockEntity.RATE / 4 * 4)), List.of(),
                List.of(new FluidIn(ModFluids.TRITIUM.source(), ElectrolyzerBlockEntity.RATE / 4)),
                Component.translatable("jei.factoryascent.fluid_note.per_tick")));
        list.add(new Process(PowerContent.TOKAMAK_CORE.get(), List.of(),
                List.of(new FluidIn(ModFluids.DEUTERIUM.source(), 1000), new FluidIn(ModFluids.HELIUM_3.source(), 1000)), List.of(), List.of(),
                perTick(PowerConfig.get(PowerConfig.FUSION_OUTPUT) + " FE/t")));
        list.add(new Process(FluidContent.machine(FluidMachine.FUELLING_PORT).get(), List.of(),
                List.of(new FluidIn(ModFluids.ROCKET_FUEL.source(), CellFluidHandler.ROCKET_FUEL_ITEM)),
                List.of(new ItemStack(OrbitalContent.ROCKET_FUEL.get())), List.of(), Component.empty()));
        registration.addRecipes(TYPE, list);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (FluidMachine m : FluidMachine.VALUES) registration.addCraftingStation(TYPE, FluidContent.machine(m).get());
        registration.addCraftingStation(TYPE, PowerContent.generator(Generator.STEAM_ENGINE).get(), PowerContent.generator(Generator.BIOGAS_GENERATOR).get(),
                PowerContent.REACTOR_COOLANT_PORT.get(), ModBlocks.machine(MachineType.ELECTROLYZER).get(), PowerContent.TOKAMAK_CORE.get());
    }

    static final class Category extends AbstractRecipeCategory<Process> {
        static final int W = 160, H = 46;

        Category(IGuiHelper gui) {
            super(TYPE, Component.translatable("jei.factoryascent.fluid_processes"),
                    gui.createDrawableItemLike(FluidContent.machine(FluidMachine.REFINERY).get()), W, H);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Process p, IFocusGroup focuses) {
            builder.addInputSlot(1, 1).setStandardSlotBackground().add(new ItemStack(p.machine()));
            int x = 22;
            if (!p.itemsIn().isEmpty()) {
                builder.addInputSlot(x + 1, 13).setStandardSlotBackground().addItemStacks(p.itemsIn());
                x += 18;
            }
            for (FluidIn f : p.fluidsIn()) {
                builder.addInputSlot(x + 1, 5).setFluidRenderer(Math.max(1, f.amount()), false, 16, 30).addFluidStack(f.fluid(), f.amount());
                x += 18;
            }
            int ox = Math.max(x, 58) + 28;
            for (FluidIn f : p.fluidsOut()) {
                builder.addOutputSlot(ox + 1, 5).setFluidRenderer(Math.max(1, f.amount()), false, 16, 30).addFluidStack(f.fluid(), f.amount());
                ox += 18;
            }
            for (ItemStack s : p.itemsOut()) {
                builder.addOutputSlot(ox + 1, 13).setOutputSlotBackground().add(s);
                ox += 20;
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Process p, IFocusGroup focuses) {
            int x = 22 + (p.itemsIn().isEmpty() ? 0 : 18) + 18 * p.fluidsIn().size();
            builder.addRecipeArrow().setPosition(Math.max(x, 58) + 3, 12);
            builder.addText(p.note(), W, 9).setPosition(0, 37).setColor(0xFF606060);
        }
    }
}
