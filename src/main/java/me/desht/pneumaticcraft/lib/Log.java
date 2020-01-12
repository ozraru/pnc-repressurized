package me.desht.pneumaticcraft.lib;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log {
    private static final Logger logger = LogManager.getLogger();

    public static void info(String message, Object... params) {
        logger.log(Level.INFO, message, params);
    }

    public static void error(String message, Object... params) {
        logger.log(Level.ERROR, message, params);
    }

    public static void warning(String message, Object... params) {
        logger.log(Level.WARN, message, params);
    }
}
