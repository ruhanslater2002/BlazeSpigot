package dev.ruhan.blazespigot;

import com.destroystokyo.paper.PaperConfig;
import com.destroystokyo.paper.PaperWorldConfig;
import net.minecraft.server.World;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

public class BlazeWorldConfig {

    private final String worldName;
    private final YamlConfiguration config;
    private boolean verbose;

    public BlazeWorldConfig(String worldName) {
        this.worldName = worldName;
        this.config = BlazeConfig.config;
        this.init();
    }

    public void init()
    {
        this.verbose = getBoolean( "verbose", true );

        log( "-------- World Settings For [" + worldName + "] --------" );
        BlazeConfig.readConfig(PaperWorldConfig.class, this);
    }

    private void set(String path, Object val) {
        config.set( "world-settings.default." + path, val );
    }

    private boolean getBoolean(String path, boolean def) {
        config.addDefault( "world-settings.default." + path, def );
        return config.getBoolean( "world-settings." + worldName + "." + path, config.getBoolean( "world-settings.default." + path ) );
    }

    private void log(String s) {
        if (verbose) {
            Bukkit.getLogger().info(s);
        }
    }

    // Ion Spigot
    public boolean constantExplosions;
    private void setConstantExplosions() {
        this.constantExplosions = this.getBoolean("explosions.constant-radius", false);
    }

    public boolean explosionProtectedRegions;
    private void setExplosionProtectedRegions() {
        explosionProtectedRegions = getBoolean("explosions.protected-regions", true);
    }
    // Ion Spigot

    public boolean optimizeExplosions;
    private void setOptimizeExplosions() {
        optimizeExplosions = getBoolean("explosions.optimizeExplosions", true);
    }
}
