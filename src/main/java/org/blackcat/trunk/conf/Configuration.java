package org.blackcat.trunk.conf;

import io.vertx.core.json.JsonObject;
import org.blackcat.trunk.conf.exceptions.ConfigurationException;

import java.text.MessageFormat;
import java.util.Objects;

import static org.blackcat.trunk.conf.Keys.*;

/**
 * Configuration parser
 */
final public class Configuration {

    /* http section */
    private String domain;
    private int startTimeout;

    /* HTTP(S) server conf */
    private String httpHost;
    private int httpPort;
    private boolean useSSL;
    private String keystoreFilename;
    private String keystorePassword;

    /* database section */
    private String dbType;
    private String dbHost;
    private int dbPort;
    private String dbName;

    /* oauth2 section */

    private String oauth2Provider;
    private String oauth2ClientID;
    private String oauth2ClientSecret;

    /* keycloak only */
    private String oauth2AuthServerURL;
    private String oauth2AuthServerRealm;
    private String oauth2AuthServerPublicKey;

    /* storage section */
    private String storageRoot;

    public String getDomain() {
        return domain;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public boolean isSSLEnabled() {
        return useSSL;
    }

    public String getKeystoreFilename() {
        return keystoreFilename;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public int getStartTimeout() {
        return startTimeout;
    }

    void parseServerSection(JsonObject jsonObject) {
        JsonObject serverSection = jsonObject.getJsonObject(SERVER_SECTION, new JsonObject());

        this.domain = serverSection.getString(SERVER_DOMAIN);
        if (domain == null) {
            throw new ConfigurationException("No domain specified.");
        }

        this.startTimeout = serverSection.getInteger(SERVER_START_TIMEOUT, DEFAULT_SERVER_START_TIMEOUT);
        this.httpHost = serverSection.getString(SERVER_HTTP_HOST, DEFAULT_SERVER_HTTP_HOST);
        this.httpPort = serverSection.getInteger(SERVER_HTTP_PORT, DEFAULT_SERVER_HTTP_PORT);
        this.useSSL = serverSection.getBoolean(SERVER_USE_SSL, DEFAULT_SERVER_USE_SSL);
        if (useSSL) {
            this.keystoreFilename = serverSection.getString(SERVER_KEYSTORE_FILENAME, DEFAULT_SERVER_KEYSTORE_FILENAME);
            this.keystorePassword = serverSection.getString(SERVER_KEYSTORE_PASSWORD, DEFAULT_SERVER_KEYSTORE_PASSWORD);
        }
    }

    public String getOauth2Provider() {
        return oauth2Provider;
    }

    public String getOauth2ClientID() {
        return oauth2ClientID;
    }

    public String getOauth2ClientSecret() {
        return oauth2ClientSecret;
    }

    public String getOauth2AuthServerURL() {
        return oauth2AuthServerURL;
    }

    public String getOauth2AuthServerRealm() {
        return oauth2AuthServerRealm;
    }

    public String getOauth2AuthServerPublicKey() {
        return oauth2AuthServerPublicKey;
    }

    public String getDatabaseType() {
        return dbType;
    }

    public String getDatabaseHost() {
        return dbHost;
    }

    public int getDatabasePort() {
        return dbPort;
    }

    public String getDatabaseName() {
        return dbName;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    void parseDatabaseSection(JsonObject jsonObject) {
        JsonObject databaseSection = jsonObject.getJsonObject(DATABASE_SECTION, new JsonObject());

        // TODO: 1/25/18 Support more databases
        this.dbType = databaseSection.getString(DATABASE_TYPE, DATABASE_TYPE_MONGODB);
        if (! dbType.equals(DATABASE_TYPE_MONGODB)) {
            throw new ConfigurationException( MessageFormat.format(
                    "Unsupported database: {0}", dbType));
        }

        // db-independent configuration
        this.dbHost = databaseSection.getString(DATABASE_HOST, DEFAULT_DATABASE_HOST);
        this.dbPort = databaseSection.getInteger(DATABASE_PORT, DEFAULT_DATABASE_PORT);
        this.dbName = databaseSection.getString(DATABASE_NAME, DEFAULT_DATABASE_NAME);
    }

    void parseOAuth2Section(JsonObject jsonObject) {
        JsonObject oauth2Section = jsonObject.getJsonObject(OAUTH2_SECTION, new JsonObject());

        // TODO: 1/25/18 Support more oauth2 providers
        this.oauth2Provider = oauth2Section.getString(OAUTH2_PROVIDER);
        if (oauth2Provider == null) {
            throw new ConfigurationException("No oauth2 provider specified");
        } else if (
            !oauth2Provider.equals(OAUTH2_PROVIDER_GOOGLE) &&
                !oauth2Provider.equals(OAUTH2_PROVIDER_KEYCLOAK)) {
            throw new ConfigurationException(MessageFormat.format(
                "Unsupported oauth2 provider: {0}", oauth2Provider));
        }

        // provider-independent configuration
        this.oauth2ClientID = oauth2Section.getString(OAUTH2_CLIENT_ID);
        if (oauth2ClientID == null) {
            throw new ConfigurationException("No oauth2 client ID specified");
        }

        this.oauth2ClientSecret = oauth2Section.getString(OAUTH2_CLIENT_SECRET);
        if (oauth2ClientSecret == null) {
            throw new ConfigurationException("No oauth2 client secret specified");
        }

        if (oauth2Provider.equals(OAUTH2_PROVIDER_KEYCLOAK)) {
            parseOAuth2KeyCloakSection(oauth2Section);
        }
    }

    private void parseOAuth2KeyCloakSection(JsonObject jsonObject) {
        JsonObject oauth2KeycloakSection = jsonObject.getJsonObject(OAUTH2_KEYCLOAK_SECTION, new JsonObject());
        this.oauth2AuthServerURL = oauth2KeycloakSection.getString(OAUTH2_KEYCLOAK_AUTH_SERVER_URL,
            DEFAULT_OAUTH2_KEYCLOAK_AUTH_SERVER_URL);
        this.oauth2AuthServerRealm = oauth2KeycloakSection.getString(OAUTH2_KEYCLOAK_AUTH_SERVER_REALM,
            DEFAULT_KEYCLOAK_OAUTH2_AUTH_SERVER_REALM);
        this.oauth2AuthServerPublicKey = oauth2KeycloakSection.getString(OAUTH2_KEYCLOAK_AUTH_SERVER_PUBLIC_KEY,
            DEFAULT_KEYCLOAK_OAUTH2_AUTH_SERVER_PUBLIC_KEY);
    }

    void parseStorageSection(JsonObject jsonObject) {
        JsonObject storageSection = jsonObject.getJsonObject(STORAGE_SECTION, new JsonObject());
        this.storageRoot = storageSection.getString(STORAGE_ROOT, ".");
    }

    public Configuration(JsonObject jsonObject) {
        parseServerSection(jsonObject);
        parseDatabaseSection(jsonObject);
        parseOAuth2Section(jsonObject);
        parseStorageSection(jsonObject);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Configuration{");
        sb.append(String.format("domain='%s'", domain));
        sb.append(String.format(",startTimeout=%d", startTimeout));
        sb.append(String.format(",httpHost='%s'", httpHost));
        sb.append(String.format(",httpPort=%s", httpPort));
        if (useSSL) {
            sb.append(String.format(",keystoreFilename='%s'", keystoreFilename));
            sb.append(String.format(",keystorePassword=<hidden>"));
        }

        sb.append(String.format(",storageRoot='%s'", storageRoot));
        sb.append(String.format(",dbType='%s'", dbType));
        sb.append(String.format(",dbHost='%s'", dbHost));
        sb.append(String.format(",dbPort=%d", dbPort));
        sb.append(String.format(",dbName='%s'", dbName));

        sb.append(String.format(",oauth2Provider='%s'", oauth2Provider));
        sb.append(String.format(",oauth2ClientID='%s'", oauth2ClientID));

        if (oauth2Provider.equals("keycloak")) {
            sb.append(String.format(",oauth2ClientSecret='%s'", oauth2ClientSecret));
            sb.append(String.format(",oauth2ServerRealm='%s'", oauth2AuthServerRealm));
            sb.append(String.format(",oauth2ServerPublicKey='%s'", oauth2AuthServerPublicKey));
        }
        sb.append("}");

        return sb.toString();
    }

    public static Configuration create(JsonObject config) {
        return new Configuration(config);
    }

    /* keycloak helper method */
    public JsonObject buildKeyCloakConfiguration() {
        return new JsonObject()
                   .put("realm", getOauth2AuthServerRealm())
                   .put("realm-public-key", getOauth2AuthServerPublicKey())
                   .put("auth-server-url", getOauth2AuthServerURL())
                   .put("ssl-required", "external")
                   .put("resource", getOauth2ClientID())
                   .put("credentials",
                       new JsonObject()
                           .put("secret", getOauth2ClientSecret()));
    }
}