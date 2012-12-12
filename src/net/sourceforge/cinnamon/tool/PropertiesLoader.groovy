package net.sourceforge.cinnamon.tool

/**
 * Load properties from a properties file. Check for essential properties (if assertions are enabled)
 */
class PropertiesLoader {

    static Properties load(File dataDir) {
        File configProperties = new File(dataDir, 'config.properties')
        assert configProperties.exists(): "config.properties was not found.";
        return loadProperties(configProperties)


    }

    static Properties loadProperties(File propertiesFile){
        Properties config = new Properties()
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(propertiesFile));
        config.load(bis);
        bis.close();

        def propNames = ["server.url",
                "server.username",
                "server.password",
                "default_repository"];
        propNames.each {prop ->
            assert config.getProperty(prop) != null: String.format(
                    "Property %s is not set.", prop);
        }
        return config
    }

    static Properties load(String filename){
        return loadProperties(new File(filename))
    }

}
