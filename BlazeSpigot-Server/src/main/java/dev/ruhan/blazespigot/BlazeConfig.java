package dev.ruhan.blazespigot;

import com.google.common.base.Throwables;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.sugarcanemc.sugarcane.util.yaml.YamlCommenter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;


public class BlazeConfig {

    private static final Logger LOGGER = LogManager.getLogger(BlazeConfig.class);

    public static File CONFIG_FILE;               // Holds the config file reference
    public static final String header = "BlazeSpigot Configuration File\n"
                                   + "------------------------------\n"
                                   + "This is the default configuration file for BlazeSpigot.\n"
                                   + "Modify the settings below to adjust how the spigot behaves."; // Config header text.

    /*========================================================================*/
    public static YamlConfiguration config; // Holds the loaded config instance
    static int version; // Version of config.
    /*========================================================================*/


    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        config = new YamlConfiguration();
        try {
            BlazeConfig.LOGGER.info("Loading BlazeSpigot config from " + configFile.getName());
            config.load(CONFIG_FILE);
        } catch (IOException ignored) {
        } catch (InvalidConfigurationException ex) {
            LOGGER.log(Level.ERROR, "Could not load blaze.yml, please correct your syntax errors", ex);
            throw Throwables.propagate(ex);
        }
        config.options().copyDefaults(true);

        int configVersion = 1; // Update this every new configuration update
        version = getInt("config-version", configVersion);
        set("config-version", configVersion);
        config.options().header(header);
        readConfig(BlazeConfig.class, null);
    }


    static void readConfig(Class<?> clazz, Object instance) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    try {
                        method.setAccessible(true);
                        method.invoke(instance);
                    } catch (InvocationTargetException ex) {
                        throw Throwables.propagate(ex.getCause());
                    } catch (Exception ex) {
                        LOGGER.log(Level.ERROR, "Error invoking " + method, ex);
                    }
                }
            }
        }

        try {
            config.save(CONFIG_FILE);
        } catch (IOException ex) {
            LOGGER.log(Level.ERROR, "Could not save " + CONFIG_FILE, ex);
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
