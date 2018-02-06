package org.blackcat.trunk.conf;

final public class Keys {
    /* SERVER */
    public static final String SERVER_SECTION = "web";

    public static final String SERVER_HTTP_HOST = "host";
    public static final String DEFAULT_SERVER_HTTP_HOST = "localhost";

    public static final String SERVER_HTTP_PORT = "port";
    public static final int DEFAULT_SERVER_HTTP_PORT = 8080;

    public static final String SERVER_USE_SSL = "useSSL";
    public static final boolean DEFAULT_SERVER_USE_SSL = false;

    public static final String SERVER_KEYSTORE_FILENAME = "keystoreFilename";
    public static final String DEFAULT_SERVER_KEYSTORE_FILENAME = "server-keystore.jks";

    public static final String SERVER_KEYSTORE_PASSWORD = "keystorePassword";
    public static final String DEFAULT_SERVER_KEYSTORE_PASSWORD = "password";

    public static final String SERVER_START_TIMEOUT = "timeout";
    public static final int DEFAULT_SERVER_START_TIMEOUT = 30;

    /* STORAGE */
    public static final String STORAGE_SECTION = "storage";
    public static final String STORAGE_ROOT = "root";

    /* DATABASE */
    public static final String DATABASE_SECTION = "database";

    public static final String DATABASE_TYPE = "type";
    public static final String DATABASE_TYPE_MONGODB = "mongodb";

    public static final String DATABASE_HOST = "host";
    public static final String DEFAULT_DATABASE_HOST = "localhost";

    public static final String DATABASE_PORT = "port";
    public static final int DEFAULT_DATABASE_PORT = 27027;

    public static final String DATABASE_NAME = "name";
    public static final String DEFAULT_DATABASE_NAME = "data";

    /* OAUTH2 */
    public static final String OAUTH2_SECTION = "oauth2";

    public static final String OAUTH2_PROVIDER = "provider";
    public static final String OAUTH2_PROVIDER_GOOGLE = "google";
    public static final String OAUTH2_PROVIDER_KEYCLOAK = "keycloak";
    public static final String OAUTH2_CLIENT_ID = "clientID";
    public static final String OAUTH2_CLIENT_SECRET = "clientSecret";

    public static final String OAUTH2_AUTH_SERVER_URL = "authServerURL";
    public static final String DEFAULT_OAUTH2_AUTH_SERVER_URL = "http://localhost:9000";

    public static final String OAUTH2_AUTH_SERVER_REALM = "authServerRealm";
    public static final String DEFAULT_OAUTH2_AUTH_SERVER_REALM = "master";

    public static final String OAUTH2_AUTH_SERVER_PUBLIC_KEY = "authServerPublicKey";
    public static final String DEFAULT_OAUTH2_AUTH_SERVER_PUBLIC_KEY = null;

    private Keys()
    {}
}
