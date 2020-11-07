package carpet_autocraftingtable;

import carpet_autocraftingtable.mixins.CraftingInventoryMixin;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.block.entity.Hopper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.RecipeInputProvider;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.RecipeUnlocker;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.logging.LogManager;




public class CraftingTableBlockEntity extends LockableContainerBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider
{
    public static final BlockEntityType<CraftingTableBlockEntity> TYPE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            "carpet:crafting_table",
            BlockEntityType.Builder.create(CraftingTableBlockEntity::new, Blocks.CRAFTING_TABLE).build(null)
    );
    private static final int[] OUTPUT_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int[] INPUT_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    public DefaultedList<ItemStack> inventory;
    public ItemStack output = ItemStack.EMPTY;
    private Recipe<?> lastRecipe;
    private List<AutoCraftingTableContainer> openContainers = new ArrayList<>();

    public CraftingTableBlockEntity() {  //this(BlockEntityType.BARREL);
        this(TYPE);
    }

    private CraftingInventory craftingInventory = new CraftingInventory(null, 3, 3);

    private CraftingTableBlockEntity(BlockEntityType<?> type) {
        super(type);
        this.inventory = DefaultedList.ofSize(9, ItemStack.EMPTY);
        ((CraftingInventoryMixin) craftingInventory).setInventory(this.inventory);
    }

    public static void init() { } // registers BE type

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        super.toTag(tag);
        Inventories.toTag(tag, inventory);
        tag.put("Output", output.toTag(new CompoundTag()));
        return tag;
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        super.fromTag(state, tag);
        Inventories.fromTag(tag, inventory);
        this.output = ItemStack.fromTag(tag.getCompound("Output"));
    }

    @Override
    protected Text getContainerName() {
        return new TranslatableText("container.crafting");
    }

    @Override
    protected ScreenHandler createScreenHandler(int id, PlayerInventory playerInventory) {
        AutoCraftingTableContainer container = new AutoCraftingTableContainer(id, playerInventory, this);
        this.openContainers.add(container);
        return container;
    }

    @Override
    public int[] getAvailableSlots(Direction dir) {
        if (dir == Direction.DOWN && (!output.isEmpty() || getCurrentRecipe().isPresent())) return OUTPUT_SLOTS;
        return INPUT_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, Direction dir) {
        return slot > 0 && getStack(slot).isEmpty();
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        if (slot == 0) {
            if (this.world == null) return false;

            if(!output.isEmpty() || getCurrentRecipe().isPresent()) {
                Item item = null;

                if( ! output.isEmpty()) {
                    item = output.getItem();
                }
                else {
                    item = getCurrentRecipe().get().getOutput().getItem();
                }

                double x = (double)this.pos.getX() + 0.5D;
                double y = (double)this.pos.getY() - 0.5D;
                double z = (double)this.pos.getZ() + 0.5D;

                BlockPos blockPos = new BlockPos(x, y, z);
                BlockState blockState = world.getBlockState(blockPos);

                if (blockState.isOf(Blocks.HOPPER)) {
                    Block block = blockState.getBlock();
                    BlockEntity blockEntity = world.getBlockEntity(blockPos);

                    Inventory hopperInventory = null;

                    if (blockEntity instanceof Inventory) {
                        hopperInventory = (Inventory)blockEntity;
                    }

                    if(hopperInventory == null) return false;

                    for(int i = 0; i < hopperInventory.size(); ++i) {
                        Logger.getLogger("autocraft").info(String.format("Try slot %d", i));
                        ItemStack itemStack = hopperInventory.getStack(i);
                        int count = itemStack.getCount();
                        if (
                            itemStack.isEmpty() || (
                                item == itemStack.getItem() 
                                && count > 0
                                && count < itemStack.getMaxCount()
                            )
                        ) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }

        return true;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot != 0;
    }

    @Override
    public int size() {
        return 10;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.inventory) {
            if (!stack.isEmpty()) return false;
        }
        return output.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        if (slot > 0) return this.inventory.get(slot - 1);
        if (!output.isEmpty()) return output;
        Optional<CraftingRecipe> recipe = getCurrentRecipe();
        return recipe.map(craftingRecipe -> craftingRecipe.craft(craftingInventory)).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        if (slot == 0) {
            if (output.isEmpty()) {
                output = craft();
            }
            return output.split(amount);
        }
        return Inventories.splitStack(this.inventory, slot - 1, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        if (slot == 0) {
            ItemStack output = this.output;
            this.output = ItemStack.EMPTY;
            return output;
        }
        return Inventories.removeStack(this.inventory, slot - 1);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot == 0) {
            output = stack;
            return;
        }
        inventory.set(slot - 1, stack);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        for (AutoCraftingTableContainer c : openContainers) c.onContentChanged(this);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity var1) {
        return true;
    }

    @Override
    public void provideRecipeInputs(RecipeFinder finder) {
        for (ItemStack stack : this.inventory) {
            finder.addItem(stack);
        }
    }

    @Override
    public void setLastRecipe(Recipe<?> var1) {
        lastRecipe = var1;
    }

    @Override
    public Recipe<?> getLastRecipe() {
        return lastRecipe;
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    private Optional<CraftingRecipe> getCurrentRecipe() {
        if (this.world == null) return Optional.empty();
        Optional<CraftingRecipe> optionalRecipe = this.world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, craftingInventory, world);
        optionalRecipe.ifPresent(this::setLastRecipe);
        return optionalRecipe;
    }

    private ItemStack craft() {
        if (this.world == null) return ItemStack.EMPTY;
        Optional<CraftingRecipe> optionalRecipe = getCurrentRecipe();
        if (!optionalRecipe.isPresent()) return ItemStack.EMPTY;
        CraftingRecipe recipe = optionalRecipe.get();
        ItemStack result = recipe.craft(craftingInventory);
        DefaultedList<ItemStack> remaining = world.getRecipeManager().getRemainingStacks(RecipeType.CRAFTING, craftingInventory, world);
        for (int i = 0; i < 9; i++) {
            ItemStack current = inventory.get(i);
            ItemStack remainingStack = remaining.get(i);
            if (!current.isEmpty()) {
                current.decrement(1);
            }
            if (!remainingStack.isEmpty()) {
                if (current.isEmpty()) {
                    inventory.set(i, remainingStack);
                } else if (ItemStack.areItemsEqualIgnoreDamage(current, remainingStack) && ItemStack.areTagsEqual(current, remainingStack)) {
                    current.increment(remainingStack.getCount());
                } else {
                    ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), remainingStack);
                }
            }
        }
        markDirty();
        return result;
    }

    public void onContainerClose(AutoCraftingTableContainer container) {
        this.openContainers.remove(container);
    }
}
