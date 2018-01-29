package org.blackcat.trunk.conf;

final public class Keys {
    /* SERVER */
    static final String SERVER_SECTION = "server";

    static final String SERVER_HTTP_PORT = "port";
    static final int DEFAULT_SERVER_HTTP_PORT = 8080;

    static final String SERVER_USE_SSL = "useSSL";
    static final boolean DEFAULT_SERVER_USE_SSL = false;

    static final String SERVER_KEYSTORE_FILENAME = "keystoreFilename";
    static final String DEFAULT_SERVER_KEYSTORE_FILENAME = "server-keystore.jks";

    static final String SERVER_KEYSTORE_PASSWORD = "keystorePassword";
    static final String DEFAULT_SERVER_KEYSTORE_PASSWORD = "password";

    static final String SERVER_START_TIMEOUT = "timeout";
    static final int DEFAULT_SERVER_START_TIMEOUT = 30;

    /* STORAGE */
    static final String STORAGE_SECTION = "storage";
    static final String STORAGE_ROOT = "root";

    /* DATABASE */
    static final String DATABASE_SECTION = "database";

    static final String DATABASE_TYPE = "type";
    static final String DATABASE_TYPE_MONGODB = "mongodb";

    static final String DATABASE_HOST = "host";
    static final String DEFAULT_DATABASE_HOST = "localhost";

    static final String DATABASE_PORT = "port";
    static final int DEFAULT_DATABASE_PORT = 27027;

    static final String DATABASE_NAME = "name";
    static final String DEFAULT_DATABASE_NAME = "data";

    /* OAUTH2 */
    static final String OAUTH2_SECTION = "oauth2";

    static final String OAUTH2_ENABLED = "enabled";
    static final String OAUTH2_PROVIDER = "provider";
    static final String OAUTH2_PROVIDER_GOOGLE = "google"; // // TODO: 1/25/18 add more providers
    static final String OAUTH2_CLIENT_ID = "clientID";
    static final String OAUTH2_CLIENT_SECRET = "clientSecret";
    static final String OAUTH2_DOMAIN = "domain";

    private Keys()
    {}
}
