package net.minecraft.server;

import dev.cobblesword.nachospigot.commons.Constants;
import org.bukkit.event.entity.ExplosionPrimeEvent; // CraftBukkit

// The EntityTNTPrimed class represents a primed (lit) TNT entity.
public class EntityTNTPrimed extends Entity {

    // Fuse time for the TNT before it explodes.
    public int fuseTicks;

    // The entity (like a player or skeleton) that triggered the TNT.
    private EntityLiving source;

    // The explosion power (radius), set to 4 by default.
    public float yield = 4; // CraftBukkit - added field

    // Determines whether the explosion will cause fire.
    public boolean isIncendiary = false; // CraftBukkit - added field

    // Location of the TNT's source (used by PaperSpigot for cannons).
    public org.bukkit.Location sourceLoc; // PaperSpigot

    // Default constructor for TNT with no specific location.
    public EntityTNTPrimed(World world) {
        this(null, world); // Calls the overloaded constructor.
    }

    // Constructor for TNT with a specific location.
    public EntityTNTPrimed(org.bukkit.Location loc, World world) {
        super(world); // Calls the Entity constructor.
        sourceLoc = loc; // Store the location from which the TNT was spawned.
        this.k = true; // Ensures the TNT entity is marked as active.
        this.setSize(0.98F, 0.98F); // Sets the size of the TNT entity slightly smaller than a full block (1.0F).
        this.loadChunks = world.paperSpigotConfig.loadUnloadedTNTEntities; // PaperSpigot - Load chunks where TNT is active.
    }

    // Constructor for TNT with location, position, and a source entity.
    public EntityTNTPrimed(org.bukkit.Location loc, World world, double d0, double d1, double d2, EntityLiving entityliving) {
        this(loc, world); // Calls the constructor with location and world.
        this.setPosition(d0, d1, d2); // Set TNT position (coordinates).

        // Randomize the motion of the TNT for realism. BlazeSpigot - Commented out
        // PaperSpigot - Fix cannons by disabling motion along X and Z axes.
        // BlazeSpigot
        if (world.paperSpigotConfig.fixCannons) {
            this.motX = this.motZ = 0.0F;
        }
        else {
            float f = (float) (Math.random() * Math.PI * 2.0D); // Random angle in radians.
            this.motX = (double) (-Math.sin(f) * 0.02F); // Random X motion.
            this.motZ = (double) (-Math.cos(f) * 0.02F); // Random Z motion.
        }

        this.motY = 0.20000000298023224D; // Initial upward motion.
        // BlazeSpigot

        // Set the default fuse time to 80 ticks (~4 seconds).
        this.fuseTicks = 80;

        // Store the position and entity responsible for triggering the TNT.
        this.lastX = d0;
        this.lastY = d1;
        this.lastZ = d2;
        this.source = entityliving;
    }

    // Initializes the entity's data. Empty because TNT doesn't need specific attributes.
    protected void h() {}

    // Prevents the TNT from taking any damage.
    protected boolean s_() {
        return false;
    }

    // Returns whether the TNT is still alive (hasn't exploded yet).
    public boolean ad() {
        return !this.dead;
    }

    // The main update function called every tick.
    public void t_() {
        // Spigot - Limits the number of TNT entities processed per tick.
        if (world.spigotConfig.maxTntTicksPerTick > -1 && world.spigotConfig.currentPrimedTnt++ > world.spigotConfig.maxTntTicksPerTick) {
            return; // Skip this TNT if the limit is reached.
        }

        // Update the previous position of the TNT.
        this.lastX = this.locX;
        this.lastY = this.locY;
        this.lastZ = this.locZ;

        // Apply gravity to the TNT.
        this.motY -= 0.03999999910593033D;

        // Move the TNT based on its motion values.
        this.move(this.motX, this.motY, this.motZ);

        // PaperSpigot - Remove TNT entities if they exceed a specified height.
        if (this.world.paperSpigotConfig.tntEntityHeightNerf != 0 && this.locY > this.world.paperSpigotConfig.tntEntityHeightNerf) {
            this.die(); // TNT is too high, so it's removed.
        }

        // PaperSpigot - Remove TNT entities in unloaded chunks.
        if (this.inUnloadedChunk && world.paperSpigotConfig.removeUnloadedTNTEntities) {
            this.die(); // TNT is in an unloaded chunk, so it's removed.
            this.fuseTicks = 2; // Shortens the fuse time to 2 ticks.
        }

        // Reduce motion over time due to air resistance.
        this.motX *= 0.9800000190734863D;
        this.motY *= 0.9800000190734863D;
        this.motZ *= 0.9800000190734863D;

        // Reduce motion when TNT hits the ground and make it bounce slightly.
        if (this.onGround) {
            this.motX *= 0.699999988079071D;
            this.motZ *= 0.699999988079071D;
            this.motY *= -0.5D;
        }

        // Decrease fuse time and check if it reaches zero (time to explode).
        if (this.fuseTicks-- <= 0) {
            // CraftBukkit - Explode before marking TNT as dead to have a valid location for the event.
            if (!this.world.isClientSide) {
                this.explode(); // Trigger the explosion.
            }
            this.die(); // Remove TNT entity after the explosion.
        } else {
            // Update the entity (collision checks, etc.) and spawn smoke particles.
            this.W();
//            this.world.addParticle(EnumParticle.SMOKE_NORMAL, this.locX, this.locY + 0.5D, this.locZ, 0.0D, 0.0D, 0.0D, Constants.EMPTY_ARRAY);
        }
    }

    // Handles the TNT explosion when the fuse reaches zero.
    private void explode() {
        // PaperSpigot - Force chunk loading during TNT explosions to prevent issues.
        ChunkProviderServer chunkProviderServer = ((ChunkProviderServer) world.chunkProvider);
        boolean forceChunkLoad = chunkProviderServer.forceChunkLoad;

        if (world.paperSpigotConfig.loadUnloadedTNTEntities) {
            chunkProviderServer.forceChunkLoad = true; // Enable force loading.
        }

        // Trigger the ExplosionPrimeEvent to allow plugins to modify or cancel the explosion.
        org.bukkit.craftbukkit.CraftServer server = this.world.getServer();
        ExplosionPrimeEvent event = new ExplosionPrimeEvent((org.bukkit.entity.Explosive) org.bukkit.craftbukkit.entity.CraftEntity.getEntity(server, this));
        server.getPluginManager().callEvent(event);

        // If the event wasn't cancelled, create the explosion.
        if (!event.isCancelled()) {
            this.world.createExplosion(this, this.locX, this.locY + (this.length / 2.0F), this.locZ, event.getRadius(), event.getFire(), true);
        }

        // PaperSpigot - Restore chunk loading to its original state.
        if (world.paperSpigotConfig.loadUnloadedTNTEntities) {
            chunkProviderServer.forceChunkLoad = forceChunkLoad;
        }
    }

    // Save the TNT's data to NBT (used for saving/loading entities).
    protected void b(NBTTagCompound nbttagcompound) {
        nbttagcompound.setByte("Fuse", (byte) this.fuseTicks);

        // PaperSpigot - Save the TNT's source location.
        if (sourceLoc != null) {
            nbttagcompound.setInt("SourceLoc_x", sourceLoc.getBlockX());
            nbttagcompound.setInt("SourceLoc_y", sourceLoc.getBlockY());
            nbttagcompound.setInt("SourceLoc_z", sourceLoc.getBlockZ());
        }
    }

    // Load the TNT's data from NBT.
    protected void a(NBTTagCompound nbttagcompound) {
        this.fuseTicks = nbttagcompound.getByte("Fuse");

        // PaperSpigot - Load the TNT's source location.
        if (nbttagcompound.hasKey("SourceLoc_x")) {
            int srcX = nbttagcompound.getInt("SourceLoc_x");
            int srcY = nbttagcompound.getInt("SourceLoc_y");
            int srcZ = nbttagcompound.getInt("SourceLoc_z");
            sourceLoc = new org.bukkit.Location(world.getWorld(), srcX, srcY, srcZ);
        }
    }

    // Returns the entity that triggered the TNT (e.g., player, skeleton).
    public EntityLiving getSource() {
        return this.source;
    }

    // PaperSpigot - Fix cannon TNT explosions by modifying distance calculation.
    @Override
    public double f(double d0, double d1, double d2) {
        if (!world.paperSpigotConfig.fixCannons) return super.f(d0, d1, d2);

        double d3 = this.locX - d0;
        double d4 = this.locY + this.getHeadHeight() - d1;
        double d5 = this.locZ - d2;

        return MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5); // Use adjusted head height for distance.
    }

    // Returns whether the TNT is alive (overrides default behavior for cannons).
    @Override
    public boolean aL() {
        return !world.paperSpigotConfig.fixCannons && super.aL();
    }

    // Returns the head height for cannons (half the entity's length).
    @Override
    public float getHeadHeight() {
        return world.paperSpigotConfig.fixCannons ? this.length / 2 : 0.0F;
    }

    // PaperSpigot - Optimize TNT ticking (especially in water).
    @Override
    public boolean W() {
        if (!world.paperSpigotConfig.fixCannons) return super.W();

        // Preserve the current motion of the TNT while calling the parent method.
        double oldMotX = this.motX;
        double oldMotY = this.motY;
        double oldMotZ = this.motZ;

        super.W(); // Call the parent method.

        // Restore the motion values.
        this.motX = oldMotX;
        this.motY = oldMotY;
        this.motZ = oldMotZ;

        // If the TNT is in water, sync its position and velocity with nearby players.
        if (this.inWater) {
            EntityTrackerEntry ete = ((WorldServer) this.getWorld()).getTracker().trackedEntities.get(this.getId());
            if (ete != null) {
                PacketPlayOutEntityVelocity velocityPacket = new PacketPlayOutEntityVelocity(this);
                PacketPlayOutEntityTeleport positionPacket = new PacketPlayOutEntityTeleport(this);

                // Send position and velocity updates to players near the TNT.
                for (EntityPlayer viewer : ete.trackedPlayers) {
                    if ((viewer.locX - this.locX) * (viewer.locY - this.locY) * (viewer.locZ - this.locZ) < 16 * 16) {
                        viewer.playerConnection.sendPacket(velocityPacket);
                        viewer.playerConnection.sendPacket(positionPacket);
                    }
                }
            }
        }

        return this.inWater; // Return whether the TNT is in water.
    }
}
