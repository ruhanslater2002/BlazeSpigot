package net.minecraft.server;

public class BlockTNT extends Block {

    // A boolean BlockState that determines whether the TNT will explode or not.
    public static final BlockStateBoolean EXPLODE = BlockStateBoolean.of("explode");

    // Constructor for the TNT block, initializing with TNT material and default block state (not exploded).
    public BlockTNT() {
        super(Material.TNT); // This block is made of TNT material.
        this.j(this.blockStateList.getBlockData().set(BlockTNT.EXPLODE, Boolean.valueOf(false))); // Set the default state to not explode.
        this.a(CreativeModeTab.d); // Add the TNT block to a creative mode tab (for players to use).
    }

    // This method is called when the TNT block is placed in the world.
    public void onPlace(World world, BlockPosition blockposition, IBlockData iblockdata) {
        super.onPlace(world, blockposition, iblockdata); // Calls the superclass method.

        // If the block is powered (e.g., by redstone), trigger the explosion.
        if (world.isBlockIndirectlyPowered(blockposition)) {
            this.postBreak(world, blockposition, iblockdata.set(BlockTNT.EXPLODE, Boolean.valueOf(true))); // Mark it for explosion.
            world.setAir(blockposition); // Remove the block after triggering.
        }
    }

    // This method is called when the block receives a redstone signal.
    public void doPhysics(World world, BlockPosition blockposition, IBlockData iblockdata, Block block) {
        if (world.isBlockIndirectlyPowered(blockposition)) { // If powered by redstone.
            this.postBreak(world, blockposition, iblockdata.set(BlockTNT.EXPLODE, Boolean.valueOf(true))); // Mark for explosion.
            world.setAir(blockposition); // Remove the block.
        }
    }

    // This method is called when the TNT block is destroyed by another explosion.
    public void wasExploded(World world, BlockPosition blockposition, Explosion explosion) {

        // Check if the current world is not the client side (meaning this is server-side execution).
        if (!world.isClientSide) {

            // Determine the source location of the explosion. If the explosion's source is another primed TNT entity,
            // use its source location. Otherwise, use the coordinates of the TNT block itself.
            org.bukkit.Location loc = explosion.source instanceof EntityTNTPrimed
                ? ((EntityTNTPrimed) explosion.source).sourceLoc  // Get the source location from the primed TNT.
                : new org.bukkit.Location(world.getWorld(), blockposition.getX(), blockposition.getY(), blockposition.getZ()); // Use the block's coordinates as fallback.

            // PaperSpigot: Fix cannon explosions. Adjust the Y-coordinate of the explosion's position if needed.
            double y = blockposition.getY(); // Get the Y-coordinate of the block position.

            // If the PaperSpigot configuration for cannon explosions is not enabled, increase the Y-coordinate by 0.5.
            // This ensures that explosions happen slightly above the block for better realism.
            if (!world.paperSpigotConfig.fixCannons) y += 0.5;

            // Create a new TNT entity (`EntityTNTPrimed`), which is a primed and ready-to-explode TNT block.
            // The TNT is positioned slightly above the center of the block (x + 0.5F, z + 0.5F for centering and adjusted y-coordinate).
            // The source of the explosion (player, mob, or other entity) is passed as the last argument.
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(
                loc,          // Location of the explosion.
                world,        // The world where the explosion occurs.
                (double) ((float) blockposition.getX() + 0.5F), // X-coordinate (centered on the block).
                y,            // Adjusted Y-coordinate (may be raised depending on cannon fix config).
                (double) ((float) blockposition.getZ() + 0.5F), // Z-coordinate (centered on the block).
                explosion.getSource() // The source of the explosion (could be another entity or cause).
            );

            // Set the fuse time (how long the TNT takes to explode after being primed).
            // This fuse is randomized slightly, with a value between 1/8 and 1/4 of its total fuse time.
            entitytntprimed.fuseTicks = world.random.nextInt(entitytntprimed.fuseTicks / 4) + entitytntprimed.fuseTicks / 8;

            // Add the primed TNT entity to the world, making it an active entity that will explode after its fuse runs out.
            world.addEntity(entitytntprimed);
        }
    }


    // Helper method to trigger an explosion after the block is broken or primed.
    public void postBreak(World world, BlockPosition blockposition, IBlockData iblockdata) {
        this.a(world, blockposition, iblockdata, (EntityLiving) null); // Calls the method to prime the TNT.
    }

    // Called to handle priming the TNT (e.g., lighting it on fire or breaking it).
    public void a(World world, BlockPosition blockposition, IBlockData iblockdata, EntityLiving entityliving) {
        if (!world.isClientSide) { // Only on server side.
            if (((Boolean) iblockdata.get(BlockTNT.EXPLODE)).booleanValue()) { // If the TNT is marked to explode.
                org.bukkit.Location loc = new org.bukkit.Location(world.getWorld(), blockposition.getX(), blockposition.getY(), blockposition.getZ());

                // Adjust Y position for cannons (PaperSpigot fix).
                double y = blockposition.getY();
                if (!world.paperSpigotConfig.fixCannons) y += 0.5;

                // Create a primed TNT entity and set its location and fuse time.
                EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(loc, world, (double) ((float) blockposition.getX() + 0.5F), y, (double) ((float) blockposition.getZ() + 0.5F), entityliving);

                world.addEntity(entitytntprimed); // Add the primed TNT to the world.
                world.makeSound(entitytntprimed, "game.tnt.primed", 1.0F, 1.0F); // Play a sound for priming TNT.
            }
        }
    }

    // Handles player interaction with the TNT block (e.g., lighting it with flint and steel).
    public boolean interact(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman, EnumDirection enumdirection, float f, float f1, float f2) {
        if (entityhuman.bZ() != null) { // If the player is holding an item.
            Item item = entityhuman.bZ().getItem(); // Get the item.

            // If the item is Flint and Steel or Fire Charge, ignite the TNT.
            if (item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE) {
                this.a(world, blockposition, iblockdata.set(BlockTNT.EXPLODE, Boolean.valueOf(true)), (EntityLiving) entityhuman); // Prime TNT.
                world.setAir(blockposition); // Remove the TNT block after igniting.

                // Handle item durability or usage.
                if (item == Items.FLINT_AND_STEEL) {
                    entityhuman.bZ().damage(1, entityhuman); // Reduce durability for Flint and Steel.
                } else if (!entityhuman.abilities.canInstantlyBuild) {
                    --entityhuman.bZ().count; // Reduce item count for Fire Charge.
                }

                return true; // Interaction was successful.
            }
        }

        return super.interact(world, blockposition, iblockdata, entityhuman, enumdirection, f, f1, f2); // Call parent method for other interactions.
    }

    // Called when an entity collides with the TNT block (like an arrow).
    public void a(World world, BlockPosition blockposition, IBlockData iblockdata, Entity entity) {
        if (!world.isClientSide && entity instanceof EntityArrow) { // If the entity is an arrow and server side.
            EntityArrow entityarrow = (EntityArrow) entity;

            if (entityarrow.isBurning()) { // If the arrow is on fire, ignite the TNT.
                // Prevents the event if it's cancelled by plugins (CraftBukkit event system).
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entityarrow, blockposition.getX(), blockposition.getY(), blockposition.getZ(), Blocks.AIR, 0).isCancelled()) {
                    return; // Do nothing if the event is cancelled.
                }

                // Prime the TNT and set it to explode.
                this.a(world, blockposition, world.getType(blockposition).set(BlockTNT.EXPLODE, Boolean.valueOf(true)), entityarrow.shooter instanceof EntityLiving ? (EntityLiving) entityarrow.shooter : null);
                world.setAir(blockposition); // Remove the block.
            }
        }
    }

    // Determines whether the block should drop anything when destroyed by an explosion (returns false to prevent drops).
    public boolean a(Explosion explosion) {
        return false;
    }

    // Converts block state data from an integer format (used for saving/loading the world).
    public IBlockData fromLegacyData(int i) {
        return this.getBlockData().set(BlockTNT.EXPLODE, Boolean.valueOf((i & 1) > 0)); // Check if the explosion bit is set in the integer data.
    }

    // Converts block state data to an integer format for saving/loading the world.
    public int toLegacyData(IBlockData iblockdata) {
        return ((Boolean) iblockdata.get(BlockTNT.EXPLODE)).booleanValue() ? 1 : 0; // Set the explosion bit in the integer data if true.
    }

    // Gets the list of possible block states (in this case, only the EXPLODE state).
    protected BlockStateList getStateList() {
        return new BlockStateList(this, new IBlockState[] { BlockTNT.EXPLODE }); // Register the explode state.
    }
}
