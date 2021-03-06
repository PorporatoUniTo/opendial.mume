package opendial.modules.mumedefault.config;

import java.io.File;

public class Config {
    private static final String s = File.separator;

    public static final String CORRECTION_CONFIG = "configs" + s + "correction-config.properties";

    public static final String TINT_CONFIG = "configs" + s + "default-config.properties";
    public static final String LOG4J_CONFIG = "configs" + s + "log4j.conf";
    public static final String HEIDELTIME_CONFIG = "configs" + s + "config.props";

    // CAUTION
    public static final String GOOGLE_MAPS_API_CONFIG = "configs" + s + "google-api.properties";

    public static final String EXAMPLE_LOG_FILE_PREFIX = "examples" + s + "example_log_";
}
