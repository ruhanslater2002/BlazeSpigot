package dev.ruhan.blazespigot;

import com.destroystokyo.paper.PaperConfig;
import com.google.common.base.Throwables;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.logging.Level;

public class BlazeConfig {

    public static File CONFIG_FILE;               // Holds the config file reference
    public static final String header = "BlazeSpigot Configuration File\n"
                                   + "------------------------------\n"
                                   + "This is the default configuration file for BlazeSpigot.\n"
                                   + "Modify the settings below to adjust how the spigot behaves."; // Config header text.

    /*========================================================================*/
    public static YamlConfiguration config; // Holds the loaded config instance
    static int version; // Version of config.
    static Map<String, Command> commands;
    /*========================================================================*/

    // Method to initialize and load the configuration file
    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        config = new YamlConfiguration();

        try {
            System.out.println("Loading Blaze config from " + CONFIG_FILE.getName()); // Load the configuration from the file
            config.load(CONFIG_FILE);
        }

        catch (IOException ex) {
            // Handle file-related errors
            Bukkit.getLogger().log(Level.SEVERE, "Could not create blaze.yml file", ex);
        }

        catch (InvalidConfigurationException ex) {
            // Handle syntax errors in the YAML file
            Bukkit.getLogger().log(Level.SEVERE, "Could not load blaze.yml, please correct your syntax errors", ex);
        }

        config.options().header("--- BlazeSpigot Config File ---\nThis is the default configuration.");
        config.options().copyDefaults(true);

        version = getInt( "config-version", 1);
        set( "config-version", 1);
        readConfig(BlazeConfig.class, null);
    }


    static void readConfig(Class<?> clazz, Object instance) {
        for ( Method method : clazz.getDeclaredMethods() )
        {
            if ( Modifier.isPrivate( method.getModifiers() ) )
            {
                if ( method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE )
                {
                    try
                    {
                        method.setAccessible( true );
                        method.invoke( instance );
                    } catch ( InvocationTargetException ex )
                    {
                        throw Throwables.propagate( ex.getCause() );
                    } catch ( Exception ex )
                    {
                        Bukkit.getLogger().log( Level.SEVERE, "Error invoking " + method, ex );
                    }
                }
            }
        }

        try
        {
            config.save( CONFIG_FILE );
        } catch ( IOException ex )
        {
            Bukkit.getLogger().log( Level.SEVERE, "Could not save " + CONFIG_FILE, ex );
        }
    }

    private static void set(String path, Object value) {
        config.set(path, value);
    }

    private static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, config.getInt(path) );
    }

    private static boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, config.getBoolean(path));
    }

}
