package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

// Nacho start
import dev.cobblesword.nachospigot.commons.Constants;
import dev.cobblesword.nachospigot.commons.minecraft.MCUtils;
import me.elier.nachospigot.config.NachoConfig;
import net.jafama.FastMath;
// Nacho end
// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import xyz.sculas.nacho.async.AsyncExplosions;
// CraftBukkit end

import java.util.*;
import java.util.concurrent.CompletableFuture;



public class Explosion {

    // A cached instance of Random to be used for generating random numbers.
    // This is a static final field, meaning it is shared across all instances
    // of this class and will not change once initialized.
    public static final Random CACHED_RANDOM = new Random();

    // A boolean flag, `a`, whose purpose is likely to toggle some feature
    // or behavior in the explosion logic. Its exact usage would depend
    // on the broader context of the class.
    private final boolean a;

    // Another boolean flag, `b`, similar to `a`, likely used for toggling
    // a different feature or behavior in the explosion logic.
    private final boolean b;

    // A Random instance, `c`, initialized to the shared CACHED_RANDOM instance.
    // This allows for consistent random number generation across instances
    // without creating a new Random object for each explosion.
    private final Random c = CACHED_RANDOM;

    // A reference to the World object where the explosion occurs.
    // This is crucial for interacting with the game world (e.g., getting
    // block information, handling entities).
    private final World world;

    // The x-coordinate of the explosion's position in the world.
    // This is essential for determining the location of the explosion
    // and its effects on surrounding blocks and entities.
    private final double posX;

    // The y-coordinate of the explosion's position in the world.
    // Similar to posX, this helps identify the explosion's exact location
    // within the three-dimensional space of the game world.
    private final double posY;

    // The z-coordinate of the explosion's position in the world.
    // This, combined with posX and posY, specifies the precise position
    // of the explosion in the world.
    private final double posZ;

    // A reference to the entity that caused the explosion. This could be
    // a player, a TNT block, or any other entity that can trigger explosions.
    // It may be used to apply damage, grant rewards, or control the explosion's effects.
    public final Entity source;

    // The size of the explosion, likely affecting its blast radius and damage.
    // This float value helps determine how far the explosion's effects will reach.
    private final float size;

    // A list of BlockPosition objects representing the blocks that are affected
    // by the explosion. This allows for efficient tracking of which blocks
    // should be destroyed or modified during the explosion process.
    private final List<BlockPosition> blocks = Lists.newArrayList();

    // A map that associates EntityHuman (players) with Vec3D positions.
    // This could be used to track the locations of players in relation to
    // the explosion, potentially for applying damage or visual effects.
    private final Map<EntityHuman, Vec3D> k = Maps.newHashMap();

    // A boolean flag indicating whether the explosion has been canceled.
    // This is useful for scenarios where explosions may be prevented
    // (e.g., by plugins or other game mechanics). If set to true,
    // the explosion will not occur or will be aborted.
    public boolean wasCanceled = false; // CraftBukkit - add field


    // Constructor
    /*
World world: This is the world or environment in which the explosion happens. It might be an instance of the game world or a specific section of it. The explosion will affect the world (by destroying blocks, damaging entities, etc.).

Entity entity: This represents the entity that caused the explosion. It could be a player, a mob like a creeper, or a piece of TNT. Knowing which entity caused the explosion might be useful for tracking damage or applying game logic (like dropping items or assigning blame).

double d0, d1, d2: These are the coordinates (x, y, z) of the explosion's center. These values determine where in the world the explosion originates.

float f: This is likely the strength or power of the explosion, affecting how far-reaching its effects are. A higher value might indicate a bigger explosion, causing more destruction over a larger area.

boolean flag: This might represent whether or not the explosion causes fire. For example, some explosions could ignite nearby blocks (like TNT), while others might just cause a blast without fire.

boolean flag1: This could represent whether the explosion should destroy blocks in the world. Some explosions might only damage entities without affecting the environment, while others can break blocks and leave craters.
     */
    public Explosion(World world, Entity entity, double d0, double d1, double d2, float f, boolean flag, boolean flag1) {
        // Assign the world in which the explosion occurs
        this.world = world;

        // Assign the entity that caused the explosion (could be null if there's no source, like in the case of a natural explosion)
        this.source = entity;

        // Set the explosion size, ensuring the value is clamped to be at least 0.0 to avoid invalid or negative sizes.
        this.size = (float) Math.max(f, 0.0); // CraftBukkit - clamp bad values

        // Set the explosion's position (X, Y, Z) using the provided coordinates (d0, d1, d2).
        this.posX = d0;
        this.posY = d1;
        this.posZ = d2;

        // Boolean flag indicating if the explosion causes fires (flag is true if the explosion can set blocks on fire).
        this.a = flag;

        // Boolean flag indicating whether block damage should occur (flag1 is true if the explosion destroys blocks).
        this.b = flag1;
    }


    public void a() {
        // CraftBukkit start: Early return if the explosion size is too small to have any impact.
        if (this.size < 0.1F) {
            return;
        }
        // CraftBukkit end

        // Variables for chunk/block positions.
        int i;
        int j;

        // IonSpigot start - Block Searching Improvements
        // Create a BlockPosition object using the current position of the explosion (posX, posY, posZ).
        BlockPosition pos = new BlockPosition(posX, posY, posZ);

        // Get the chunk where the explosion is happening.
        Chunk chunk = world.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4); // Dividing by 16 to get chunk coordinates.

        // Get the block at the current explosion position.
        Block b = chunk.getBlockData(pos).getBlock(); // TacoSpigot - get block of the explosion

        // Check if the world configuration allows skipping liquid explosions and if the block is liquid (e.g., water or lava).
        if (!this.world.tacoSpigotConfig.optimizeLiquidExplosions || !b.getMaterial().isLiquid()) {
            // Use a fastutil set to store block positions to be affected by the explosion.
            it.unimi.dsi.fastutil.longs.LongSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

            // Search for blocks in the current chunk that will be affected by the explosion.
            searchForBlocks(set, chunk);

            // Iterate over the set of found blocks and add their positions to the explosion's block list.
            for (it.unimi.dsi.fastutil.longs.LongIterator iterator = set.iterator(); iterator.hasNext(); ) {
                this.blocks.add(BlockPosition.fromLong(iterator.nextLong()));
            }
        }

        // this.blocks.addAll(hashset);  // Old method of adding blocks (commented out)

        // Calculate the explosion radius by multiplying the explosion size by 2.
        float f3 = this.size * 2.0F;

        // IonSpigot start - Faster Entity Iteration
        // Calculate the chunk range affected by the explosion in the X, Y, and Z dimensions.
        i = MathHelper.floor(this.posX - (double) f3 - 1.0D) >> 4;  // Start X chunk (left boundary)
        j = MathHelper.floor(this.posX + (double) f3 + 1.0D) >> 4;  // End X chunk (right boundary)

        // Clamp Y chunk range between 0 and 15 to avoid going out of the world’s Y range.
        int l = MathHelper.clamp(MathHelper.floor(this.posY - (double) f3 - 1.0D) >> 4, 0, 15);  // Start Y chunk
        int i1 = MathHelper.clamp(MathHelper.floor(this.posY + (double) f3 + 1.0D) >> 4, 0, 15);  // End Y chunk

        int j1 = MathHelper.floor(this.posZ - (double) f3 - 1.0D) >> 4;  // Start Z chunk
        int k1 = MathHelper.floor(this.posZ + (double) f3 + 1.0D) >> 4;  // End Z chunk

        // PaperSpigot start - Fix lag from explosions processing dead entities
        // The code below was removed (commented out) to prevent checking entities that are dead or shouldn't be processed during explosions.
        // List<Entity> list = this.world.a(this.source, new AxisAlignedBB(i, l, j1, j, i1, k1), entity -> IEntitySelector.d.apply(entity) && !entity.dead);
        // PaperSpigot end

        // Create a vector representing the center of the explosion.
        Vec3D vec3d = new Vec3D(this.posX, this.posY, this.posZ);

        // Loop over all chunks in the X and Z range (horizontally) affected by the explosion.
        for (int chunkX = i; chunkX <= j; ++chunkX) {
            for (int chunkZ = j1; chunkZ <= k1; ++chunkZ) {
                // Retrieve the chunk if it is already loaded. If not loaded, skip it to avoid loading unnecessary chunks.
                chunk = world.getChunkIfLoaded(chunkX, chunkZ);
                if (chunk == null) {
                    continue;  // Skip unloaded chunks.
                }

                // Loop through the Y chunk range (vertically) affected by the explosion.
                for (int chunkY = l; chunkY <= i1; ++chunkY) {
                    // Process and affect all entities in the current Y slice of the chunk.
                    affectEntities(chunk.entitySlices[chunkY], vec3d, f3);
                }
            }
        }
    }


    public void affectEntities(List<Entity> entities, Vec3D explosionPos, float explosionRadius) {
        // Precompute explosion radius squared to avoid multiple divisions.
        double explosionRadiusSquared = explosionRadius * explosionRadius;

        // Iterate over each entity in the list.
        for (Entity entity : entities) {
            // Skip non-interactive or dead entities early to save processing.
            if (entity.aW() || entity.dead) continue;

            // Calculate the distance between the entity and the explosion in a single step.
            double dX = entity.locX - this.posX;
            double dY = entity.locY + entity.getHeadHeight() - this.posY;
            double dZ = entity.locZ - this.posZ;
            double distanceSquared = dX * dX + dY * dY + dZ * dZ;

            // Skip entities outside the explosion radius (8 blocks or 64.0 units squared).
            if (distanceSquared > 64.0 || distanceSquared == 0.0) continue;

            // Precompute the actual distance and normalize the direction vectors.
            double distance = MathHelper.sqrt(distanceSquared);
            double normalizedDistance = distance / explosionRadius;
            double invDistance = 1.0 / distance; // Inverse to avoid repetitive division.

            dX *= invDistance;
            dY *= invDistance;
            dZ *= invDistance;

            // Paper optimization: Calculate the block density asynchronously.
            final double scaledDX = dX * 1.15;
            final double scaledDY = dY * 1.15;
            final double scaledDZ = dZ * 1.15;

            // Start block density calculation.
            this.getBlockDensity(explosionPos, entity.getBoundingBox()).thenAccept((blockDensity) -> {
                // Switch to main thread for entity updates.
                MCUtils.ensureMain(() -> {
                    // Apply the explosion impact based on distance and block density.
                    double explosionImpact = (1.0 - normalizedDistance) * blockDensity;

                    // Special case: Cannoning entities get custom knockback.
                    if (entity.isCannoningEntity) {
                        entity.g(scaledDX * explosionImpact, scaledDY * explosionImpact, scaledDZ * explosionImpact);
                        return;
                    }

                    // Damage handling: Calculate damage based on the explosion impact.
                    CraftEventFactory.entityDamage = source;
                    entity.forceExplosionKnockback = false;

                    // Calculate damage (precompute as integer to save casting).
                    int calculatedDamage = (int) ((explosionImpact * explosionImpact + explosionImpact) / 2.0 * 8.0 * explosionRadius + 1.0);
                    boolean wasDamaged = entity.damageEntity(DamageSource.explosion(this), (float) calculatedDamage);

                    // Reset the damage source.
                    CraftEventFactory.entityDamage = null;

                    // Skip further processing for entities that weren't damaged, except specific cases.
                    if (!wasDamaged && !(entity instanceof EntityTNTPrimed || entity instanceof EntityFallingBlock) && !entity.forceExplosionKnockback) {
                        return;
                    }

                    // Apply knockback with enchantment protection considered (PaperSpigot optimization).
                    double knockbackFactor = entity instanceof EntityHuman && world.paperSpigotConfig.disableExplosionKnockback ? 0 : EnchantmentProtection.a(entity, explosionImpact);
                    entity.g(scaledDX * knockbackFactor, scaledDY * knockbackFactor, scaledDZ * knockbackFactor);

                    // Handle knockback for human players (non-invulnerable).
                    if (entity instanceof EntityHuman && !((EntityHuman) entity).abilities.isInvulnerable && !world.paperSpigotConfig.disableExplosionKnockback) {
                        // Store the knockback vector.
                        this.k.put((EntityHuman) entity, new Vec3D(scaledDX * explosionImpact, scaledDY * explosionImpact, scaledDZ * explosionImpact));
                    }
                });
            });
        }
    }


    public void a(boolean flag) {
        // PaperSpigot start - Configurable TNT explosion volume based on source type.
        float volume = source instanceof EntityTNTPrimed ? world.paperSpigotConfig.tntExplosionVolume : 4.0F;
        // Play explosion sound at the explosion's coordinates with specified volume and pitch variation.
        this.world.makeSound(this.posX, this.posY, this.posZ, "random.explode", volume, (1.0F + (this.world.random.nextFloat() - this.world.random.nextFloat()) * 0.2F) * 0.7F);
        // PaperSpigot end

        // If explosion size is larger than or equal to 2.0 and flag b is true, spawn a huge explosion particle.
        if (this.size >= 2.0F && this.b) {
            this.world.addParticle(EnumParticle.EXPLOSION_HUGE, this.posX, this.posY, this.posZ, 1.0D, 0.0D, 0.0D, Constants.EMPTY_ARRAY);
        } else {
            // Otherwise, spawn a large explosion particle.
            this.world.addParticle(EnumParticle.EXPLOSION_LARGE, this.posX, this.posY, this.posZ, 1.0D, 0.0D, 0.0D, Constants.EMPTY_ARRAY);
        }

        Iterator iterator;
        BlockPosition blockposition;

        // If flag b is true, we will handle block and entity explosion events.
        if (this.b) {
            // CraftBukkit start - Converting to Bukkit's explosion event system.
            org.bukkit.World bworld = this.world.getWorld();  // Get Bukkit world reference.
            org.bukkit.entity.Entity explode = this.source == null ? null : this.source.getBukkitEntity();  // Get the entity causing the explosion (if any).
            Location location = new Location(bworld, this.posX, this.posY, this.posZ);  // Create a Bukkit location for explosion.

            // Create a list to hold blocks that will be affected by the explosion.
            List<org.bukkit.block.Block> blockList = Lists.newArrayList();
            // Iterate over all the blocks affected by the explosion and add them to the blockList.
            for (int i1 = this.blocks.size() - 1; i1 >= 0; i1--) {
                BlockPosition cpos = this.blocks.get(i1);
                org.bukkit.block.Block bblock = bworld.getBlockAt(cpos.getX(), cpos.getY(), cpos.getZ());
                if (bblock.getType() != org.bukkit.Material.AIR) {
                    blockList.add(bblock);  // Only add non-air blocks.
                }
            }

            // Default yield value (explosion strength, usually determines drop chances).
            float yield = 0.3F;
            boolean cancelled = false;  // Track whether the explosion event is cancelled.

            // Handle explosion event based on whether an entity caused the explosion.
            if (explode != null) {
                if (NachoConfig.fireEntityExplodeEvent) {  // If entity explosion event is enabled.
                    EntityExplodeEvent event = new EntityExplodeEvent(explode, location, blockList, yield);
                    this.world.getServer().getPluginManager().callEvent(event);  // Call the event.
                    cancelled = event.isCancelled();  // Check if the event was cancelled.
                    blockList = event.blockList();  // Get the updated block list.
                    yield = event.getYield();  // Get the updated yield value.
                }
            } else {
                // Handle block explosion event (without an entity).
                BlockExplodeEvent event = new BlockExplodeEvent(location.getBlock(), blockList, yield);
                this.world.getServer().getPluginManager().callEvent(event);
                cancelled = event.isCancelled();
                blockList = event.blockList();
                yield = event.getYield();
            }

            // Clear the blocks list and repopulate it with the modified list from the event.
            this.blocks.clear();
            for (org.bukkit.block.Block bblock : blockList) {
                BlockPosition coords = new BlockPosition(bblock.getX(), bblock.getY(), bblock.getZ());
                blocks.add(coords);
            }

            // If the explosion was cancelled, stop further execution.
            if (cancelled) {
                this.wasCanceled = true;
                return;
            }
            // CraftBukkit end

            // Iterate through the list of affected blocks and process them.
            iterator = this.blocks.iterator();
            while (iterator.hasNext()) {
                blockposition = (BlockPosition) iterator.next();
                Block block = this.world.getType(blockposition).getBlock();

                // Spigot - Update nearby blocks for anti-xray system.
                world.spigotConfig.antiXrayInstance.updateNearbyBlocks(world, blockposition);

                // IonSpigot optimization (commented out) - previously this calculated explosion particles.
                /*
                if (flag) {
                    // Code to create visual particle effects for the explosion.
                    // This calculates random offsets and directions for particle creation.
                }
                */

                // Check if the block is not air, and if so, process the explosion's impact on it.
                if (block.getMaterial() != Material.AIR) {
                    // If the block is affected by the explosion, drop it naturally based on the yield.
                    if (block.a(this)) {
                        block.dropNaturally(this.world, blockposition, this.world.getType(blockposition), yield, 0);
                    }

                    // Set the block to air (destroy the block) and mark it as exploded.
                    this.world.setTypeAndData(blockposition, Blocks.AIR.getBlockData(), 3);
                    block.wasExploded(this.world, blockposition, this);
                }
            }
        }

        // If flag a is true, handle igniting blocks.
        if (this.a) {
            iterator = this.blocks.iterator();

            // Iterate through the remaining blocks and set them on fire if certain conditions are met.
            while (iterator.hasNext()) {
                blockposition = (BlockPosition) iterator.next();
                // Check if the block is air, and if the block below it is solid and randomly choose some to ignite.
                if (this.world.getType(blockposition).getBlock().getMaterial() == Material.AIR && this.world.getType(blockposition.down()).getBlock().o() && this.c.nextInt(3) == 0) {

                    // CraftBukkit - Call the block ignite event and only set fire if not cancelled.
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.world, blockposition.getX(), blockposition.getY(), blockposition.getZ(), this).isCancelled()) {
                        this.world.setTypeUpdate(blockposition, Blocks.FIRE.getBlockData());  // Set the block on fire.
                    }
                }
            }
        }
    }


    public Map<EntityHuman, Vec3D> b() {
        return this.k;  // Returns a map of human entities and their corresponding 3D vector positions.
    }

    public EntityLiving getSource() {
        // CraftBukkit start - obtain Fireball shooter for explosion tracking
        return this.source == null ? null : (this.source instanceof EntityTNTPrimed ? ((EntityTNTPrimed) this.source).getSource() : (this.source instanceof EntityLiving ? (EntityLiving) this.source : (this.source instanceof EntityFireball ? ((EntityFireball) this.source).shooter : null)));
        // CraftBukkit end
    }

    public void clearBlocks() {
        this.blocks.clear();  // Clears the list of blocks that are affected by the explosion.
    }

    public List<BlockPosition> getBlocks() {
        return this.blocks;  // Returns the list of blocks affected by the explosion.
    }

    // IonSpigot start - Block Searching Improvements
    private final static List<double[]> VECTORS = Lists.newArrayListWithCapacity(1352);

    static {
        // Generate vectors for the edges of a 16x16x16 cube
        for (int k = 0; k < 16; ++k) {
            for (int i = 0; i < 16; ++i) {
                for (int j = 0; j < 16; ++j) {
                    // Only consider the outer edges of the cube
                    if (k == 0 || k == 15 || i == 0 || i == 15 || j == 0 || j == 15) {
                        // Normalize the vector coordinates to range [-1, 1]
                        double d0 = (float) k / 15.0F * 2.0F - 1.0F;
                        double d1 = (float) i / 15.0F * 2.0F - 1.0F;
                        double d2 = (float) j / 15.0F * 2.0F - 1.0F;
                        // Calculate the magnitude of the vector
                        double d3 = (NachoConfig.enableFastMath ? FastMath.sqrt(d0 * d0 + d1 * d1 + d2 * d2) : Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2));

                        // Normalize the vector and scale it down
                        d0 = (d0 / d3) * 0.30000001192092896D;
                        d1 = (d1 / d3) * 0.30000001192092896D;
                        d2 = (d2 / d3) * 0.30000001192092896D;
                        // Add the normalized vector to the list
                        VECTORS.add(new double[]{d0, d1, d2});
                    }
                }
            }
        }
    }

    // Method to search for blocks affected by the explosion
    private void searchForBlocks(it.unimi.dsi.fastutil.longs.LongSet set, Chunk chunk) {
        BlockPosition.MutableBlockPosition position = new BlockPosition.MutableBlockPosition();  // Create a mutable block position for tracking

        // Iterate through each pre-computed direction vector
        for (double[] vector : VECTORS) {
            double d0 = vector[0];
            double d1 = vector[1];
            double d2 = vector[2];

            float f = this.size * (0.7F + (world.nachoSpigotConfig.constantExplosions ? 0.7F : this.world.random.nextFloat()) * 0.6F); // Explosion radius
            float resistance = 0;  // Initialize block resistance

            // Starting point for the search
            double stepX = this.posX;
            double stepY = this.posY;
            double stepZ = this.posZ;

            // Loop to determine how far the explosion can affect blocks
            for (; f > 0.0F; f -= 0.22500001F) {
                // Get the block coordinates of the current step
                int floorX = (NachoConfig.enableFastMath ? FastMath.floorToInt((Double.doubleToRawLongBits(stepX) >>> 63)) : org.bukkit.util.NumberConversions.floor(stepX));
                int floorY = (NachoConfig.enableFastMath ? FastMath.floorToInt((Double.doubleToRawLongBits(stepY) >>> 63)) : org.bukkit.util.NumberConversions.floor(stepY));
                int floorZ = (NachoConfig.enableFastMath ? FastMath.floorToInt((Double.doubleToRawLongBits(stepZ) >>> 63)) : org.bukkit.util.NumberConversions.floor(stepZ));

                // Check if the position has changed
                if (position.getX() != floorX || position.getY() != floorY || position.getZ() != floorZ) {
                    position.setValues(floorX, floorY, floorZ);  // Update the position

                    int chunkX = floorX >> 4;  // Calculate chunk coordinates
                    int chunkZ = floorZ >> 4;

                    // Load the chunk if necessary
                    if (chunk == null || !chunk.o() || chunk.locX != chunkX || chunk.locZ != chunkZ) {
                        chunk = world.getChunkAt(chunkX, chunkZ);
                    }

                    IBlockData iblockdata = chunk.getBlockData(position);  // Get the block data at the current position
                    Block block = iblockdata.getBlock();  // Get the block instance

                    // If the block is not air
                    if (block != Blocks.AIR) {
                        float blockResistance = block.durability / 5.0F;  // Calculate block resistance
                        resistance = (blockResistance + 0.3F) * 0.3F;  // Update resistance based on block properties
                        f -= resistance;  // Decrease explosion power based on resistance

                        // Check if the explosion can affect this block
                        if (f > 0.0F && (this.source == null || this.source.a(this, this.world, position, iblockdata, f)) && position.getY() < 256 && position.getY() >= 0) { // Ensure position is within valid world bounds
                            set.add(position.asLong());  // Add position to the set of affected blocks
                        }
                    }
                } else {
                    f -= resistance;  // If position is unchanged, just reduce explosion power by resistance
                }

                // Move to the next step in the direction of the vector
                stepX += d0;
                stepY += d1;
                stepZ += d2;
            }
        }
    }

    // IonSpigot end

    // Paper start - Optimize explosions

    // Blaze Spigot - Start
    private CompletableFuture<Float> getBlockDensity(Vec3D explosionPos, AxisAlignedBB boundingBox) {
        // Precompute the key once for caching purposes
        final int cacheKey = createKey(this, boundingBox);

        // Use supplyAsync to perform the task in the background without blocking the main thread
        return CompletableFuture.supplyAsync(() -> {
            // Retrieve cached density if available, default to -1.0f if not cached
            Float cachedDensity = this.world.explosionDensityCache.getOrDefault(cacheKey, -1.0f);

            // If cached value exists, return it immediately to save computation time
            if (cachedDensity != -1.0f) {
                return cachedDensity;
            }

            // Calculate density if not found in the cache
            float newDensity = calculateDensity(explosionPos, boundingBox);

            // Store the newly calculated density in the cache for future reference
            this.world.explosionDensityCache.putIfAbsent(cacheKey, newDensity);

            return newDensity;  // Return the newly calculated density
        }, AsyncExplosions.EXECUTOR);  // Run the task using a predefined executor for asynchronous execution
    }
    // Blaze Spigot - end

    // Method to calculate the density based on whether reduced rays are enabled
    private float calculateDensity(Vec3D vec3d, AxisAlignedBB aabb) {
        // Check the config to decide which density calculation method to use
        if (world.nachoSpigotConfig.reducedDensityRays) {
            return calculateDensityReducedRays(vec3d, aabb);  // Call method for reduced ray tracing
        } else {
            return this.world.a(vec3d, aabb);  // Default density calculation method
        }
    }

    // Method to calculate density using reduced ray tracing
    private float calculateDensityReducedRays(Vec3D vec3d, AxisAlignedBB aabb) {
        int arrived = 0;  // Count of rays that hit a block
        int rays = 0;     // Total number of rays cast

        // Iterate through vectors calculated from the bounding box
        for (Vec3D vector : calculateVectors(aabb)) {
            // If rays from the corners don't hit a block, return maximum density
            if (rays == 8 && arrived == 8) {
                return 1.0F;  // All rays arrived without hitting anything, return 1.0 for max density
            }

            // Check if the ray from this vector to the explosion position hits a block
            if (world.rayTrace(vector, vec3d) == null) {
                ++arrived;  // Increment count of rays that arrived without hitting a block
            }

            ++rays;  // Increment total ray count
        }

        // Calculate density as the ratio of arrived rays to total rays
        return (float) arrived / (float) rays;
    }

    // Method to calculate ray vectors from the given bounding box
    private List<Vec3D> calculateVectors(AxisAlignedBB aabb) {
        // Calculate step sizes based on the dimensions of the bounding box
        double d0 = 1.0D / ((aabb.d - aabb.a) * 2.0D + 1.0D);
        double d1 = 1.0D / ((aabb.e - aabb.b) * 2.0D + 1.0D);
        double d2 = 1.0D / ((aabb.f - aabb.c) * 2.0D + 1.0D);
        double d3 = (1.0D - ((NachoConfig.enableFastMath ? FastMath.floor(1.0D / d0) : Math.floor(1.0D / d0)) * d0)) / 2.0D; // Calculate offset for x-axis
        double d4 = (1.0D - ((NachoConfig.enableFastMath ? FastMath.floor(1.0D / d2) : Math.floor(1.0D / d2)) * d2)) / 2.0D; // Calculate offset for z-axis

        // Check for invalid dimensions
        if (d0 < 0.0 || d1 < 0.0 || d2 < 0.0) {
            return Collections.emptyList();  // Return an empty list if dimensions are invalid
        }

        List<Vec3D> vectors = new LinkedList<>();  // List to hold calculated vectors

        // Loop to create vectors within the bounding box
        for (float f = 0.0F; f <= 1.0F; f = (float) ((double) f + d0)) {
            for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float) ((double) f1 + d1)) {
                for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float) ((double) f2 + d2)) {
                    // Calculate the position of each vector based on the bounding box corners
                    double d5 = aabb.a + (aabb.d - aabb.a) * (double) f;   // x coordinate
                    double d6 = aabb.b + (aabb.e - aabb.b) * (double) f1; // y coordinate
                    double d7 = aabb.c + (aabb.f - aabb.c) * (double) f2; // z coordinate
                    Vec3D vector = new Vec3D(d5 + d3, d6, d7 + d4); // Create new vector with calculated offsets

                    // Add the vector to the list; priority given to corner vectors
                    if ((f == 0 || f + d0 > 1.0F) && (f1 == 0 || f1 + d1 > 1.0F) && (f2 == 0 || f2 + d2 > 1.0F)) {
                        vectors.add(0, vector); // Add to the front of the list
                    } else {
                        vectors.add(vector); // Add to the end of the list
                    }
                }
            }
        }

        return vectors;  // Return the list of calculated vectors
    }

    // Method to create a unique key for caching explosion density calculations
    static int createKey(Explosion explosion, AxisAlignedBB aabb) {
        int result;
        long temp;
        result = explosion.world.hashCode(); // Start with the world's hash code
        // Use double values to create a unique key based on position and bounding box dimensions
        temp = Double.doubleToLongBits(explosion.posX);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(explosion.posY);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(explosion.posZ);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(aabb.a);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(aabb.b);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(aabb.c);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(aabb.d);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(aabb.e);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(aabb.f);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result; // Return the unique cache key
    }

    // IonSpigot - comment this out
    static class CacheKey {
        private final World world; // Reference to the world
        private final double posX, posY, posZ; // Position coordinates of the explosion
        private final double minX, minY, minZ; // Minimum coordinates of the bounding box
        private final double maxX, maxY, maxZ; // Maximum coordinates of the bounding box

        // Constructor to initialize the cache key based on explosion and bounding box
        public CacheKey(Explosion explosion, AxisAlignedBB aabb) {
            this.world = explosion.world; // Set world from explosion
            this.posX = explosion.posX;   // Set position x from explosion
            this.posY = explosion.posY;   // Set position y from explosion
            this.posZ = explosion.posZ;   // Set position z from explosion
            this.minX = aabb.getMinX();   // Set min x from bounding box
            this.minY = aabb.getMinY();   // Set min y from bounding box
            this.minZ = aabb.getMinZ();   // Set min z from bounding box
            this.maxX = aabb.getMaxX();   // Set max x from bounding box
            this.maxY = aabb.getMaxY();   // Set max y from bounding box
            this.maxZ = aabb.getMaxZ();   // Set max z from bounding box
        }

        @Override
        public boolean equals(Object o) {
            // Check if the object is the same instance
            if (this == o)
                return true;
            // Check if the object is null or not the same class
            if (o == null || getClass() != o.getClass())
                return false;

            CacheKey cacheKey = (CacheKey) o; // Cast the object to CacheKey

            // Compare all relevant fields for equality
            if (Double.compare(cacheKey.posX, posX) != 0)
                return false;
            if (Double.compare(cacheKey.posY, posY) != 0)
                return false;
            if (Double.compare(cacheKey.posZ, posZ) != 0)
                return false;
            if (Double.compare(cacheKey.minX, minX) != 0)
                return false;
            if (Double.compare(cacheKey.minY, minY) != 0)
                return false;
            if (Double.compare(cacheKey.minZ, minZ) != 0)
                return false;
            if (Double.compare(cacheKey.maxX, maxX) != 0)
                return false;
            if (Double.compare(cacheKey.maxY, maxY) != 0)
                return false;
            if (Double.compare(cacheKey.maxZ, maxZ) != 0)
                return false;
            return world.equals(cacheKey.world); // Ensure world references are equal
        }

        @Override
        public int hashCode() {
            int result;  // Variable to hold the final hash code
            long temp;   // Temporary variable for double to long conversion
            result = world.hashCode(); // Start with the world's hash code
            // Create unique hash based on position and bounding box dimensions
            temp = Double.doubleToLongBits(posX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(posZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(minZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxX);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxY);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(maxZ);
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result; // Return the final hash code
        }
    }
}
    // Paper end
    // IonSpigot end
