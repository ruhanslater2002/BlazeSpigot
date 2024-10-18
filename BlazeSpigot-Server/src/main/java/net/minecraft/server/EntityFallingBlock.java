package net.minecraft.server;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class EntityFallingBlock extends Entity {

    // Represents the block data associated with this falling block.
    // IBlockData holds information about the type of block and its properties.
    private IBlockData block;

    // Keeps track of the number of ticks the falling block has lived since it was created.
    // This can be used to determine the block's behavior over time, such as how long it has been falling.
    public int ticksLived;

    // Indicates whether the falling block should drop as an item when it is removed from the world.
    // Defaults to true, meaning it will drop unless specified otherwise.
    public boolean dropItem = true;

    // An internal state flag that may represent a specific condition for the block's behavior.
    // Its purpose is not clear without additional context from the surrounding code or documentation.
    private boolean e;

    // A flag indicating whether this falling block can inflict damage to entities upon impact.
    // This allows for the block to behave like a falling object that can hurt players or mobs.
    public boolean hurtEntities; // Changed visibility from private to public to allow access from other classes

    // The maximum amount of damage that can be inflicted by this falling block.
    // This can be used in calculations to determine damage to entities it collides with.
    private int fallHurtMax = 40;

    // The amount of damage inflicted by the falling block upon impact.
    // This value determines how much damage is dealt when the block falls and hits something.
    private float fallHurtAmount = 2.0F;

    // Data for any tile entity associated with this falling block, such as a chest or other container.
    // This allows for the preservation of data related to blocks that have additional functionalities.
    public NBTTagCompound tileEntityData;

    // Represents the source location from which this falling block originated.
    // This can be used for tracking, debugging, or spawning effects related to the block's origin.
    public org.bukkit.Location sourceLoc; // Location of the falling block for integration with Bukkit API (PaperSpigot)


    // PaperSpigot start - Add FallingBlock source location API
    public EntityFallingBlock(World world) {
        // Constructor to create a falling block with no initial location, only the world context.
        this(null, world);
    }

    public EntityFallingBlock(org.bukkit.Location loc, World world) {
        // Constructor to create a falling block at a specific location in the specified world.
        super(world);  // Call the superclass constructor to initialize the entity with the world.
        sourceLoc = loc; // Store the source location of the falling block.
        // Load configuration setting to determine whether to load unloaded chunks for falling blocks.
        this.loadChunks = world.paperSpigotConfig.loadUnloadedFallingBlocks; // PaperSpigot
    }

    public EntityFallingBlock(World world, double d0, double d1, double d2, IBlockData iblockdata) {
        // Constructor to create a falling block at specified coordinates with given block data.
        this(null, world, d0, d1, d2, iblockdata);
    }

    public EntityFallingBlock(org.bukkit.Location loc, World world, double d0, double d1, double d2, IBlockData iblockdata) {
        // Constructor to create a falling block at specific coordinates and location with given block data.
        super(world); // Call the superclass constructor to initialize the entity with the world.
        sourceLoc = loc; // Store the source location of the falling block.
        // PaperSpigot end
        this.block = iblockdata; // Assign the block data to the falling block.
        this.k = true; // Internal flag for entity behavior (potentially related to physics).
        this.setSize(0.98F, 0.98F); // Set the size of the falling block entity.
        this.setPosition(d0, d1, d2); // Set the initial position of the falling block.
        this.motX = 0.0D; // Initialize horizontal motion in the X direction.
        this.motY = 0.0D; // Initialize vertical motion in the Y direction.
        this.motZ = 0.0D; // Initialize horizontal motion in the Z direction.
        this.lastX = d0; // Store the last known X position.
        this.lastY = d1; // Store the last known Y position.
        this.lastZ = d2; // Store the last known Z position.
        // Load configuration setting for falling blocks.
        this.loadChunks = world.paperSpigotConfig.loadUnloadedFallingBlocks; // PaperSpigot
    }

    // Method to determine if the entity is valid; it will always return false for this class.
    protected boolean s_() {
        return false;
    }

    // Method stub for potential additional functionality; currently does nothing.
    protected void h() {
    }

    // Returns true if the entity is not dead, allowing for checks on its status.
    public boolean ad() {
        return !this.dead; // Check if the entity is alive.
    }

    // Main tick method that updates the state of the falling block.
    public void t_() {
        Block block = this.block.getBlock(); // Get the underlying block representation.

        // Check if the block is AIR, if so, remove the falling block entity.
        if (block.getMaterial() == Material.AIR) {
            this.die(); // Remove the entity if it has no valid block type.
        } else {
            // Store the last known position of the falling block.
            this.lastX = this.locX;
            this.lastY = this.locY;
            this.lastZ = this.locZ;
            BlockPosition blockposition;

            // On the first tick, check if the block can be placed and perform actions accordingly.
            if (this.ticksLived++ == 0) {
                blockposition = new BlockPosition(this); // Get the current position of the falling block.
                // Check if the block at the current position matches the falling block's block type.
                if (this.world.getType(blockposition).getBlock() == block && !CraftEventFactory.callEntityChangeBlockEvent(this, blockposition.getX(), blockposition.getY(), blockposition.getZ(), Blocks.AIR, 0).isCancelled()) {

                    this.world.setAir(blockposition); // Set the block at the position to AIR.
                    // Update nearby blocks for anti-xray (to prevent block visibility exploits).
                    world.spigotConfig.antiXrayInstance.updateNearbyBlocks(world, blockposition); // Spigot
                } else if (!this.world.isClientSide) {
                    this.die(); // If not valid, remove the falling block entity.
                    return; // Exit the method.
                }
            }

            // Apply gravity to the falling block.
            this.motY -= 0.0399999910593033D;
            this.move(this.motX, this.motY, this.motZ); // Move the block based on its motion vectors.

            // PaperSpigot start - Remove entities in unloaded chunks
            if (this.inUnloadedChunk && world.paperSpigotConfig.removeUnloadedFallingBlocks) {
                this.die(); // Remove the block if it's in an unloaded chunk and the config allows it.
            }
            // PaperSpigot end

            // PaperSpigot start - Drop falling blocks above the specified height
            // Check if the block's Y position exceeds the configured height limit.
            if (this.world.paperSpigotConfig.fallingBlockHeightNerf != 0 && this.locY > this.world.paperSpigotConfig.fallingBlockHeightNerf) {
                if (this.dropItem) {
                    // Drop the block as an item if specified.
                    this.a(new ItemStack(block, 1, block.getDropData(this.block)), 0.0F);
                }
                this.die(); // Remove the falling block entity.
            }
            // PaperSpigot end

            // Apply friction to the motion of the block.
            this.motX *= 0.9800000190734863D; // Dampen X motion.
            this.motY *= 0.9800000190734863D; // Dampen Y motion.
            this.motZ *= 0.9800000190734863D; // Dampen Z motion.

            if (!this.world.isClientSide) {
                blockposition = new BlockPosition(this); // Get the current block position.
                if (this.onGround) { // If the block is on the ground:
                    this.motX *= 0.699999988079071D; // Reduce horizontal motion upon landing.
                    this.motZ *= 0.699999988079071D; // Reduce horizontal motion upon landing.
                    this.motY *= -0.5D; // Apply a bounce effect upwards.
                    // Check if the block is not a piston extension.
                    if (this.world.getType(blockposition).getBlock() != Blocks.PISTON_EXTENSION) {
                        this.die(); // Remove the falling block entity upon landing.
                        if (!this.e) { // If the internal flag is not set:
                            // Check if the block can be placed at the current position.
                            if (this.world.a(block, blockposition, true, EnumDirection.UP, (Entity) null, (ItemStack) null) && !BlockFalling.canFall(this.world, blockposition.down()) /* mimic the false conditions of setTypeIdAndData */ && blockposition.getX() >= -30000000 && blockposition.getZ() >= -30000000 && blockposition.getX() < 30000000 && blockposition.getZ() < 30000000 && blockposition.getY() >= 0 && blockposition.getY() < (this.world.tacoSpigotConfig.disableFallingBlockStackingAt256 ? 255 : 256) && this.world.getType(blockposition) != this.block) {

                                // Trigger an event for block change, and check if it's cancelled.
                                if (CraftEventFactory.callEntityChangeBlockEvent(this, blockposition.getX(), blockposition.getY(), blockposition.getZ(), this.block.getBlock(), this.block.getBlock().toLegacyData(this.block)).isCancelled()) {
                                    return; // Exit if the event is cancelled.
                                }

                                // Set the block at the current position to the falling block's block type.
                                this.world.setTypeAndData(blockposition, this.block, 3);
                                // Update nearby blocks for anti-xray.
                                world.spigotConfig.antiXrayInstance.updateNearbyBlocks(world, blockposition); // Spigot

                                // If the block is a falling block, trigger additional behavior.
                                if (block instanceof BlockFalling) {
                                    ((BlockFalling) block).a_(this.world, blockposition);
                                }

                                // If the falling block has associated tile entity data.
                                if (this.tileEntityData != null && block instanceof IContainer) {
                                    TileEntity tileentity = this.world.getTileEntity(blockposition);

                                    if (tileentity != null) { // If a tile entity exists at the position.
                                        NBTTagCompound nbttagcompound = new NBTTagCompound(); // Create a new NBT compound.
                                        tileentity.b(nbttagcompound); // Save current tile entity data.

                                        // Iterate through the stored tile entity data.
                                        Iterator iterator = this.tileEntityData.c().iterator();
                                        while (iterator.hasNext()) {
                                            String s = (String) iterator.next();
                                            NBTBase nbtbase = this.tileEntityData.get(s);

                                            // Avoid overwriting position data in the NBT.
                                            if (!s.equals("x") && !s.equals("y") && !s.equals("z")) {
                                                nbttagcompound.set(s, nbtbase.clone()); // Copy the NBT data.
                                            }
                                        }

                                        tileentity.a(nbttagcompound); // Apply the updated NBT data to the tile entity.
                                        tileentity.update(); // Update the tile entity.
                                    }
                                }
                            } else if (this.dropItem && this.world.getGameRules().getBoolean("doEntityDrops")) {
                                // If the block can't be placed, drop it as an item if allowed by game rules.
                                this.a(new ItemStack(block, 1, block.getDropData(this.block)), 0.0F);
                            }
                        }
                    }
                } else if (this.ticksLived > 100 && !this.world.isClientSide && (blockposition.getY() < 1 || blockposition.getY() > 256) || this.ticksLived > 600) {
                    // If the block has lived too long or is out of bounds, drop it as an item if allowed.
                    if (this.dropItem && this.world.getGameRules().getBoolean("doEntityDrops")) {
                        this.a(new ItemStack(block, 1, block.getDropData(this.block)), 0.0F);
                    }
                    this.die(); // Remove the falling block entity.
                }
            }
        }
    }


    // Method to handle damage to entities that the falling block collides with.
    // f: fall distance; f1: an unused parameter.
    public void e(float f, float f1) {
        Block block = this.block.getBlock(); // Retrieve the block associated with this falling entity.

        // Check if the falling block can hurt entities upon impact.
        if (this.hurtEntities) {
            int i = MathHelper.f(f - 1.0F); // Calculate the fall distance, adjusting by 1.0F.

            if (i > 0) { // Only proceed if the block has fallen a distance greater than 0.
                ArrayList arraylist = Lists.newArrayList(this.world.getEntities(this, this.getBoundingBox())); // Get a list of entities within the bounding box of the falling block.
                boolean flag = block == Blocks.ANVIL; // Check if the block is an anvil.
                DamageSource damagesource = flag ? DamageSource.ANVIL : DamageSource.FALLING_BLOCK; // Set the appropriate damage source based on the block type.
                Iterator iterator = arraylist.iterator(); // Iterate through the list of entities.

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next(); // Get the current entity from the iterator.

                    // Set the current entity causing damage for CraftBukkit events.
                    CraftEventFactory.entityDamage = this; // CraftBukkit
                    entity.damageEntity(damagesource, (float) Math.min(MathHelper.d((float) i * this.fallHurtAmount), this.fallHurtMax)); // Apply damage to the entity based on fall distance and configured values.
                    CraftEventFactory.entityDamage = null; // Reset the damage source for CraftBukkit.
                }

                // If the falling block is an anvil, there is a chance it may take damage and break.
                if (flag && (double) this.random.nextFloat() < 0.05000000074505806D + (double) i * 0.05D) {
                    int j = ((Integer) this.block.get(BlockAnvil.DAMAGE)).intValue(); // Retrieve the current damage level of the anvil.

                    ++j; // Increment the damage level.
                    if (j > 2) {
                        this.e = true; // If damage exceeds threshold, mark the anvil for breaking.
                    } else {
                        this.block = this.block.set(BlockAnvil.DAMAGE, Integer.valueOf(j)); // Update the block's damage level.
                    }
                }
            }
        }
    }

    // Method to save the state of the falling block to an NBTTagCompound.
    // nbttagcompound: the NBT data structure to save the block state.
    protected void b(NBTTagCompound nbttagcompound) {
        Block block = this.block != null ? this.block.getBlock() : Blocks.AIR; // Get the current block or default to AIR if null.
        MinecraftKey minecraftkey = (MinecraftKey) Block.REGISTRY.c(block); // Get the Minecraft key for the block.

        // Store block data in the NBT compound.
        nbttagcompound.setString("Block", minecraftkey == null ? "" : minecraftkey.toString()); // Store block type as a string.
        nbttagcompound.setByte("Data", (byte) block.toLegacyData(this.block)); // Store block data.
        nbttagcompound.setByte("Time", (byte) this.ticksLived); // Store the time the block has existed.
        nbttagcompound.setBoolean("DropItem", this.dropItem); // Store whether the block should drop as an item.
        nbttagcompound.setBoolean("HurtEntities", this.hurtEntities); // Store whether the block can hurt entities.
        nbttagcompound.setFloat("FallHurtAmount", this.fallHurtAmount); // Store the amount of damage caused by falling.
        nbttagcompound.setInt("FallHurtMax", this.fallHurtMax); // Store the maximum fall damage.

        // If there is tile entity data, store it in the NBT compound.
        if (this.tileEntityData != null) {
            nbttagcompound.set("TileEntityData", this.tileEntityData); // Store tile entity data.
        }

        // PaperSpigot start - Add FallingBlock source location API
        if (sourceLoc != null) {
            // Store the source location of the falling block if it exists.
            nbttagcompound.setInt("SourceLoc_x", sourceLoc.getBlockX());
            nbttagcompound.setInt("SourceLoc_y", sourceLoc.getBlockY());
            nbttagcompound.setInt("SourceLoc_z", sourceLoc.getBlockZ());
        }
        // PaperSpigot end
    }

    // Method to read the state of the falling block from an NBTTagCompound.
    // nbttagcompound: the NBT data structure to read the block state from.
    protected void a(NBTTagCompound nbttagcompound) {
        int i = nbttagcompound.getByte("Data") & 255; // Get the block data from NBT and ensure it's within valid range.

        // Check for block data based on different keys and retrieve the corresponding block.
        if (nbttagcompound.hasKeyOfType("Block", 8)) {
            this.block = Block.getByName(nbttagcompound.getString("Block")).fromLegacyData(i); // Get block by name and data.
        } else if (nbttagcompound.hasKeyOfType("TileID", 99)) {
            this.block = Block.getById(nbttagcompound.getInt("TileID")).fromLegacyData(i); // Get block by Tile ID and data.
        } else {
            this.block = Block.getById(nbttagcompound.getByte("Tile") & 255).fromLegacyData(i); // Fallback to get block by legacy tile data.
        }

        this.ticksLived = nbttagcompound.getByte("Time") & 255; // Retrieve how long the block has existed.
        Block block = this.block.getBlock(); // Get the underlying block representation.

        // If NBT data specifies whether the block can hurt entities, read that value.
        if (nbttagcompound.hasKeyOfType("HurtEntities", 99)) {
            this.hurtEntities = nbttagcompound.getBoolean("HurtEntities"); // Read if the block can hurt entities.
            this.fallHurtAmount = nbttagcompound.getFloat("FallHurtAmount"); // Read the amount of damage.
            this.fallHurtMax = nbttagcompound.getInt("FallHurtMax"); // Read the maximum damage.
        } else if (block == Blocks.ANVIL) {
            this.hurtEntities = true; // Default to true if the block is an anvil.
        }

        // Check if NBT data specifies whether the block should drop as an item.
        if (nbttagcompound.hasKeyOfType("DropItem", 99)) {
            this.dropItem = nbttagcompound.getBoolean("DropItem"); // Read drop item flag.
        }

        // If there is tile entity data, read it from the NBT compound.
        if (nbttagcompound.hasKeyOfType("TileEntityData", 10)) {
            this.tileEntityData = nbttagcompound.getCompound("TileEntityData"); // Load tile entity data.
        }

        // If the block is null or AIR, default to SAND.
        if (block == null || block.getMaterial() == Material.AIR) {
            this.block = Blocks.SAND.getBlockData(); // Set to a default block (SAND).
        }

        // PaperSpigot start - Add FallingBlock source location API
        if (nbttagcompound.hasKey("SourceLoc_x")) {
            // If the source location data exists, retrieve and set it.
            int srcX = nbttagcompound.getInt("SourceLoc_x");
            int srcY = nbttagcompound.getInt("SourceLoc_y");
            int srcZ = nbttagcompound.getInt("SourceLoc_z");
            sourceLoc = new org.bukkit.Location(world.getWorld(), srcX, srcY, srcZ); // Create the source location.
        }
        // PaperSpigot end
    }


    // Method to set whether the falling block can hurt entities.
    // flag: true if the block should hurt entities, false otherwise.
    public void a(boolean flag) {
        this.hurtEntities = flag; // Set the hurtEntities property to the provided flag value.
    }

    // Method to append crash report details for debugging purposes.
    // crashreportsystemdetails: the crash report to which details will be added.
    public void appendEntityCrashDetails(CrashReportSystemDetails crashreportsystemdetails) {
        super.appendEntityCrashDetails(crashreportsystemdetails); // Call the superclass method to include base details.

        // Check if the block associated with this falling entity is not null.
        if (this.block != null) {
            Block block = this.block.getBlock(); // Get the underlying block.

            // Add details about the block to the crash report for debugging.
            crashreportsystemdetails.a("Immitating block ID", (Object) Integer.valueOf(Block.getId(block))); // Add block ID.
            crashreportsystemdetails.a("Immitating block data", (Object) Integer.valueOf(block.toLegacyData(this.block))); // Add block data (legacy format).
        }
    }

    // Method to retrieve the block associated with this falling block entity.
    // Returns the IBlockData of the falling block.
    public IBlockData getBlock() {
        return this.block; // Return the block data of the falling entity.
    }

    // PaperSpigot start - Fix cannons related functionality
    @Override
    // Method to calculate the distance from the falling block to a specified point in space.
    // d0, d1, d2: the coordinates of the point to calculate the distance to.
    public double f(double d0, double d1, double d2) {
        // Check if the fixCannons configuration is enabled; if not, use the default method.
        if (!world.paperSpigotConfig.fixCannons)
            return super.f(d0, d1, d2);

        // Calculate the differences in coordinates between the falling block and the specified point.
        double d3 = this.locX - d0; // Difference in X-coordinate.
        double d4 = this.locY + this.getHeadHeight() - d1; // Difference in Y-coordinate adjusted by head height.
        double d5 = this.locZ - d2; // Difference in Z-coordinate.

        // Calculate and return the Euclidean distance from the falling block to the specified point.
        return (double) MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);
    }

    // Method to get the head height of the falling block.
    // Returns the height of the head for the falling block entity.
    @Override
    public float getHeadHeight() {
        // If fixCannons is enabled, return half the length of the falling block; otherwise, use the superclass method.
        return world.paperSpigotConfig.fixCannons ? this.length / 2 : super.getHeadHeight();
    }
}
    // PaperSpigot end

