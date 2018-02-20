package org.blackcat.trunk.conf;

import io.vertx.core.json.JsonObject;
import org.blackcat.trunk.conf.exceptions.ConfigurationException;
import org.junit.Test;

import static org.blackcat.trunk.conf.Keys.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConfigurationTest {

    @Test
    public void minimalGoogleConfigurationYieldsCorrectServerDefaults() {
        JsonObject json = minimalGoogleConfiguration();

        Configuration configuration = new Configuration(json);
        assertEquals( DEFAULT_SERVER_HTTP_PORT, configuration.getHttpPort());
        assertEquals( DEFAULT_SERVER_USE_SSL, configuration.isSSLEnabled());
        assertEquals( DEFAULT_SERVER_START_TIMEOUT, configuration.getStartTimeout());

        String defaultDomain = "http://" + DEFAULT_DATABASE_HOST + ":" + DEFAULT_SERVER_HTTP_PORT;
        assertEquals( defaultDomain, configuration.getDomain());
    }

    @Test
    public void minimalKeycloakConfigurationYieldsCorrectServerDefaults() {
        JsonObject json = minimalKeycloakConfiguration();

        Configuration configuration = new Configuration(json);
        assertEquals( DEFAULT_SERVER_HTTP_PORT, configuration.getHttpPort());
        assertEquals( DEFAULT_SERVER_USE_SSL, configuration.isSSLEnabled());
        assertEquals( DEFAULT_SERVER_START_TIMEOUT, configuration.getStartTimeout());

        String defaultDomain = "http://" + DEFAULT_DATABASE_HOST + ":" + DEFAULT_SERVER_HTTP_PORT;
        assertEquals( defaultDomain, configuration.getDomain());
    }

    @Test
    public void httpHostOverridesServerDefault() {
        String myFancyHost = "azazel";
        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.SERVER_SECTION, new JsonObject()
                                                            .put(Keys.SERVER_HTTP_HOST, myFancyHost));

        Configuration configuration = new Configuration(json);
        assertEquals( myFancyHost, configuration.getHttpHost());
    }

    @Test
    public void httpPortOverridesServerDefault() {
        int myFancyPort = 8086;
        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.SERVER_SECTION, new JsonObject()
                                                            .put(Keys.SERVER_HTTP_PORT, myFancyPort));

        Configuration configuration = new Configuration(json);
        assertEquals( myFancyPort, configuration.getHttpPort());
    }

    @Test
    public void useSSLOverridesServerDefault() {
        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.SERVER_SECTION, new JsonObject()
                                                            .put(Keys.SERVER_USE_SSL, true));

        Configuration configuration = new Configuration(json);
        assertTrue( configuration.isSSLEnabled());
        assertEquals( Keys.DEFAULT_SERVER_KEYSTORE_FILENAME, configuration.getKeystoreFilename());
        assertEquals( Keys.DEFAULT_SERVER_KEYSTORE_PASSWORD, configuration.getKeystorePassword());
    }

    @Test
    public void useSSLKeystoreConfigurationOverridesServerDefault() throws Exception {
        String myFancyKeystoreFilename = "/tmp/somekeystore.jks";
        String myFancyKeystorePassword = "password";

        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.SERVER_SECTION, new JsonObject()
                                                            .put(Keys.SERVER_USE_SSL, true)
                                                            .put(Keys.SERVER_KEYSTORE_FILENAME, myFancyKeystoreFilename)
                                                            .put(Keys.SERVER_KEYSTORE_PASSWORD, myFancyKeystorePassword));

        Configuration configuration = new Configuration(json);
        assertEquals( myFancyKeystoreFilename, configuration.getKeystoreFilename());
        assertEquals( myFancyKeystorePassword, configuration.getKeystorePassword());
    }

    @Test
    public void timeOutOverridesServerDefault() {
        int myFancyTimeout = 60;
        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.SERVER_SECTION, new JsonObject()
                                                            .put(Keys.SERVER_START_TIMEOUT, myFancyTimeout));

        Configuration configuration = new Configuration(json);
        assertEquals( myFancyTimeout, configuration.getStartTimeout());
    }

    @Test
    public void domainOverridesServerDefault() {
        String myFancyDomain = "https://my.fancydomain.com";

        JsonObject json = minimalGoogleConfiguration()
            .put(Keys.SERVER_SECTION, new JsonObject()
            .put(Keys.SERVER_DOMAIN, myFancyDomain));

        Configuration configuration = new Configuration(json);
        assertEquals( DEFAULT_SERVER_HTTP_PORT, configuration.getHttpPort());
        assertEquals( DEFAULT_SERVER_USE_SSL, configuration.isSSLEnabled());
        assertEquals( DEFAULT_SERVER_START_TIMEOUT, configuration.getStartTimeout());

        assertEquals( myFancyDomain, configuration.getDomain());
    }

    @Test
    public void emptyFileYieldsCorrectDatabaseDefaults() {
        JsonObject json = minimalGoogleConfiguration();
        Configuration configuration = new Configuration(json);

        assertEquals( DATABASE_TYPE_MONGODB, configuration.getDatabaseType());
        assertEquals( DEFAULT_DATABASE_HOST, configuration.getDatabaseHost());
        assertEquals( DEFAULT_DATABASE_PORT, configuration.getDatabasePort());
        assertEquals( DEFAULT_DATABASE_NAME, configuration.getDatabaseName());
    }

    @Test
    public void databaseHostOverridesDatabaseDefault() throws Exception {
        String myFancyHost = "behemoth";
        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.DATABASE_SECTION, new JsonObject()
                                                              .put(Keys.DATABASE_HOST, myFancyHost));
        Configuration configuration = new Configuration(json);
        assertEquals( myFancyHost, configuration.getDatabaseHost());
    }

    @Test
    public void databasePortOverridesDatabaseDefault() {
        int myFancyPort = 9099;
        JsonObject json = minimalGoogleConfiguration()
                              .put(Keys.DATABASE_SECTION, new JsonObject()
                                                              .put(Keys.DATABASE_PORT, myFancyPort));

        Configuration configuration = new Configuration(json);
        assertEquals( myFancyPort, configuration.getDatabasePort());
    }

    @Test(expected = ConfigurationException.class)
    public void oauth2GoogleWrongSecret() {
        String myFancyWrongSecret = "01234567-9ABC-DEF0-1234-56789ABCDEF";
        JsonObject json = new JsonObject()
                              .put(Keys.OAUTH2_SECTION, new JsonObject()
                                                            .put(Keys.OAUTH2_PROVIDER, Keys.OAUTH2_PROVIDER_GOOGLE)
                                                            .put(Keys.OAUTH2_CLIENT_SECRET, myFancyWrongSecret));

        new Configuration(json);
    }

    @Test
    public void emptyFileYieldsCorrectStorageDefaults() {
        JsonObject json = minimalGoogleConfiguration();
        Configuration configuration = new Configuration(json);

        assertEquals(Keys.DEFAULT_STORAGE_ROOT, configuration.getStorageRoot());
    }

    @Test
    public void storageRootOverridesStorageDefaults() {
        String myFancyRoot = "/tmp/trunk";
        JsonObject json = minimalGoogleConfiguration()
            .put(Keys.STORAGE_SECTION, new JsonObject()
                                           .put(Keys.STORAGE_ROOT, myFancyRoot));
        Configuration configuration = new Configuration(json);
        assertEquals(myFancyRoot, configuration.getStorageRoot());
    }

    private JsonObject minimalKeycloakConfiguration() {
        String myFancyClientID =
            "myFancyClient";
        String myFancySecret =
            "01234567-9ABC-DEF0-1234-56789ABCDEF0";

        return new JsonObject()
                   .put(Keys.OAUTH2_SECTION, new JsonObject()
                                                 .put(Keys.OAUTH2_PROVIDER, Keys.OAUTH2_PROVIDER_KEYCLOAK)
                                                 .put(Keys.OAUTH2_CLIENT_ID, myFancyClientID)
                                                 .put(Keys.OAUTH2_CLIENT_SECRET, myFancySecret));
    }

    private JsonObject minimalGoogleConfiguration() {
        String myFancyClientID =
            "01234567-9ABC-DEF0-1234-56789ABCDEF0.apps.googleusercontent.com";
        String myFancySecret =
            "Fi2TyjQJAYeuaJfxV2XIWLXP";

        return new JsonObject()
                   .put(Keys.OAUTH2_SECTION, new JsonObject()
                                                 .put(Keys.OAUTH2_PROVIDER, Keys.OAUTH2_PROVIDER_GOOGLE)
                                                 .put(Keys.OAUTH2_CLIENT_ID, myFancyClientID)
                                                 .put(Keys.OAUTH2_CLIENT_SECRET, myFancySecret));
    }

}