package org.blackcat.trunk.conf;

import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

import static org.blackcat.trunk.conf.Keys.*;
import static org.junit.Assert.assertEquals;

public class ConfigurationTest {

    private JsonObject json;

    @Before
    public void setUp() {
        json = new JsonObject();
    }

    @Test
    public void emptyFileYieldsCorrectServerDefaults() throws Exception {
        Configuration configuration = new Configuration(json);
        assertEquals( DEFAULT_SERVER_HTTP_PORT, configuration.getHttpPort());
        assertEquals( DEFAULT_SERVER_USE_SSL, configuration.isSSLEnabled());
        assertEquals( DEFAULT_SERVER_START_TIMEOUT, configuration.getStartTimeout());
    }

    @Test
    public void emptyConfigStringRepresentation() throws Exception {
        Configuration configuration = new Configuration(json);
        String expected = "Configuration{startTimeout=30,storageRoot='.',httpPort=8080,ssl: disabled,dbType='mongodb'" +
                              ",dbHost='localhost',dbPort=27027,dbName='data', oauth2: disabled}";
        assertEquals(expected, configuration.toString());
    }

    @Test
    public void httpPortOnlyYieldsCorrectServerDefaults() throws Exception {
        json.put(SERVER_SECTION,
            new JsonObject().put( SERVER_HTTP_PORT, 4444));

        Configuration configuration = new Configuration(json);
        assertEquals(4444, configuration.getHttpPort());
        assertEquals( DEFAULT_SERVER_USE_SSL, configuration.isSSLEnabled());
        assertEquals( DEFAULT_SERVER_START_TIMEOUT, configuration.getStartTimeout());
    }

    @Test
    public void startTimeoutOnlyYieldsCorrectServerDefaults() throws Exception {
        json.put(SERVER_SECTION,
            new JsonObject().put(SERVER_START_TIMEOUT, 60));

        Configuration configuration = new Configuration(json);
        assertEquals( DEFAULT_SERVER_HTTP_PORT, configuration.getHttpPort());
        assertEquals( DEFAULT_SERVER_USE_SSL, configuration.isSSLEnabled());
        assertEquals( 60, configuration.getStartTimeout());
    }

    @Test
    public void useSSLOnlyYieldsCorrectServerDefaults() throws Exception {
        json.put(SERVER_SECTION,
            new JsonObject().put(SERVER_USE_SSL, true));

        Configuration configuration = new Configuration(json);
        assertEquals( true, configuration.isSSLEnabled());
    }
}