package org.blackcat.trunk.integration;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.conf.Keys;
import org.blackcat.trunk.verticles.MainVerticle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.UUID;

@RunWith(VertxUnitRunner.class)
public class APITest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Rule
    public final RunTestOnContext rule = new RunTestOnContext();

    @Test(timeout = 10000)
    public void getUserProtectedRootWithNoAccessToken(TestContext context) {
        Async async = context.async();
        Vertx vertx = rule.vertx();

        JsonObject jsonConfig = getJsonConfiguration();
        Configuration configuration = new Configuration(jsonConfig);

        deployForTesting(context, vertx, done -> {
            WebClient webClient = makeWebClient(vertx, configuration, false);
            webClient
                .get("/protected/root")
                .putHeader("Accept", "application/json")
                .send(responseAsyncResult -> {
                    if (responseAsyncResult.failed()) {
                        Throwable cause = responseAsyncResult.cause();
                        context.fail(cause);
                    } else {
                        /* expect Keycloak redirection */
                        HttpResponse<Buffer> response = responseAsyncResult.result();
                        context.assertEquals(302, response.statusCode());
                        logger.info("Got redirect. Test passed");
                        async.complete();
                    }
                });
        });
    }

    @Test(timeout = 10000)
    public void putUserProtectedRootSharingWithValidCredentials(TestContext context) {
        Async async = context.async();
        Vertx vertx = rule.vertx();

        JsonObject jsonConfig = getJsonConfiguration();
        Configuration configuration = new Configuration(jsonConfig);

        JsonObject userCredentials = new JsonObject()
                                         .put("username", "admin")
                                         .put("password", "admin");

        WebClient webClient = makeWebClient(vertx, configuration, true);
        OAuth2Auth oauth2 = KeycloakAuth.create(vertx,
            OAuth2FlowType.PASSWORD, configuration.buildKeyCloakConfiguration());

        deployForTesting(context, vertx, done -> {
            logger.debug("Authenticating...");
            oauth2.authenticate(userCredentials, (AsyncResult<User> userAsyncResult) -> {
                if (userAsyncResult.failed()) {
                    context.fail(userAsyncResult.cause());
                } else {
                    User user = userAsyncResult.result();
                    String access_token = user.principal().getString("access_token");

                    makeJsonRequest(webClient.get("/protected/root"), access_token)
                        .send(responseAsyncResult -> {
                            if (responseAsyncResult.failed())
                                context.fail(responseAsyncResult.cause());
                            else {
                                HttpResponse<Buffer> response = responseAsyncResult.result();
                                context.assertEquals(200, response.statusCode());

                                JsonObject jsonObject = response.bodyAsJsonObject();
                                context.assertEquals("admin@myhost.co", jsonObject.getString("email"));

                                String root = jsonObject.getString("root");

                                /* ensure root exists */
                                makeHtmlRequest(webClient.get("/protected/main"), access_token)
                                    .send(protectedIndexAsyncResult -> {
                                        context.assertTrue(protectedIndexAsyncResult.succeeded());
                                        JsonObject payload = new JsonObject()
                                                                 .put("authorizedUsers",
                                                                     new JsonArray()
                                                                         .add("john.doe@someco.co")
                                                                         .add("jane.doe@someco.co"));

                                        makeJsonRequest(webClient.put("/share/" + root), access_token)
                                            .sendJsonObject(payload, putResponseAsyncResult -> {
                                                if (putResponseAsyncResult.failed()) {
                                                    Throwable cause = putResponseAsyncResult.cause();
                                                    context.fail(cause);
                                                } else {
                                                    HttpResponse<Buffer> putResponse = putResponseAsyncResult.result();
                                                    context.assertEquals(200, putResponse.statusCode());
                                                    logger.info("PUT request accepted. Test passed");
                                                    async.complete(); /* ok */
                                                }
                                            });
                                    });
                            }
                        });
                }
            });
        });
    }

    @Test(timeout = 10000)
    public void getUserProtectedRootWithValidCredentials(TestContext context) {
        Async async = context.async();
        Vertx vertx = rule.vertx();

        JsonObject userCredentials = new JsonObject()
                                         .put("username", "admin")
                                         .put("password", "admin");

        deployForTesting(context, vertx, done -> {
            getUserProtectedRootWithCredentials(vertx, userCredentials, responseAsyncResult -> {
                if (responseAsyncResult.failed()) {
                    context.fail(responseAsyncResult.cause());
                } else {
                    HttpResponse<Buffer> response = responseAsyncResult.result();
                    context.assertEquals(200, response.statusCode());

                    JsonObject jsonObject = response.bodyAsJsonObject();
                    context.assertEquals("admin@myhost.co", jsonObject.getString("email"));

                    String root = jsonObject.getString("root");
                    try {
                        UUID uuid = UUID.fromString(root);
                        logger.info("User root check ok: " + uuid);
                        async.complete();
                    } catch (Exception e) {
                        context.fail(e);
                    }
                }
            });
        });
    }

    @Test(timeout = 10000)
    public void getUserProtectedRootWithWrongCredentials(TestContext context) {
        Async async = context.async();
        Vertx vertx = rule.vertx();
        JsonObject userCredentials = new JsonObject()
                                         .put("username", "admin")
                                         .put("password", "password");

        deployForTesting(context, vertx, done -> {
            getUserProtectedRootWithCredentials(vertx, userCredentials, responseAsyncResult -> {
                context.assertTrue(responseAsyncResult.failed());
                async.complete();
            });
        });
    }

    private void getUserProtectedRootWithCredentials(Vertx vertx,
                                                    JsonObject userCredentials,
                                                    Handler<AsyncResult<HttpResponse<Buffer>>> handler) {

        JsonObject jsonConfig = getJsonConfiguration();
        Configuration configuration = new Configuration(jsonConfig);

        OAuth2Auth oauth2 = KeycloakAuth.create(vertx,
            OAuth2FlowType.PASSWORD, configuration.buildKeyCloakConfiguration());

        oauth2.authenticate(userCredentials, userAsyncResult -> {
            if (userAsyncResult.failed()) {
                handler.handle(Future.failedFuture(userAsyncResult.cause()));
            } else {
                User user = userAsyncResult.result();
                String access_token = user.principal().getString("access_token");

                WebClient client = makeWebClient(vertx, configuration, false);
                makeJsonRequest(client.get("/protected/root"), access_token).send(handler);
            }
        });
    }

    private HttpRequest<Buffer> makeJsonRequest(HttpRequest<Buffer> request, String access_token) {
        return request
                   .putHeader("Accept", "application/json")
                   .putHeader("Authorization", "Bearer " + access_token);
    }

    private HttpRequest<Buffer> makeHtmlRequest(HttpRequest<Buffer> request, String access_token) {
        return request
                   .putHeader("Accept", "text/html")
                   .putHeader("Authorization", "Bearer " + access_token);
    }

    private WebClient makeWebClient(Vertx vertx, Configuration configuration, boolean followRedirects) {
        WebClientOptions webClientOptions = new WebClientOptions();
        webClientOptions
            .setDefaultHost(configuration.getHttpHost())
            .setDefaultPort(configuration.getHttpPort());

        if (!followRedirects)
            webClientOptions.setFollowRedirects(false);

        return WebClient.create(vertx, webClientOptions);
    }

    private static JsonObject getJsonConfiguration() {
        String clientID = System.getProperty("clientID");
        Objects.requireNonNull(clientID);

        String clientSecret = System.getProperty("clientSecret");
        Objects.requireNonNull(clientSecret);

        String realmPublicKey = System.getProperty("realmPublicKey");
        Objects.requireNonNull(realmPublicKey);

        return
            new JsonObject()
                .put(Keys.OAUTH2_SECTION, new JsonObject()
                                              .put(Keys.OAUTH2_PROVIDER, Keys.OAUTH2_PROVIDER_KEYCLOAK)
                                              .put(Keys.OAUTH2_CLIENT_ID, clientID)
                                              .put(Keys.OAUTH2_CLIENT_SECRET, clientSecret)
                                              .put(Keys.OAUTH2_PROVIDER, Keys.OAUTH2_PROVIDER_KEYCLOAK)
                                              .put(Keys.OAUTH2_KEYCLOAK_SECTION, new JsonObject()
                                                                                     .put(Keys.OAUTH2_KEYCLOAK_AUTH_SERVER_PUBLIC_KEY,
                                                                                         realmPublicKey)))
                .put(Keys.STORAGE_SECTION, new JsonObject()
                                               .put(Keys.STORAGE_ROOT, "/tmp/trunk"));
    }

    private void deployForTesting(TestContext context, Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
        JsonObject jsonConfig = getJsonConfiguration();
        Configuration configuration = new Configuration(jsonConfig);

        String rootPath = configuration.getStorageRoot();

        FileSystem fileSystem = vertx.fileSystem();
        logger.warn("Erasing root path {}", rootPath);
        fileSystem.deleteRecursive(rootPath, true, _1 -> {
            logger.warn("Recreating root path {}", rootPath);
            fileSystem.mkdir(rootPath, _2 -> {
                DeploymentOptions deploymentOptions =
                    new DeploymentOptions()
                        .setConfig(jsonConfig);

                logger.info("Deploying...", rootPath);
                vertx.deployVerticle(MainVerticle.class.getName(),
                    deploymentOptions, deploymentAsyncResult -> {
                        context.assertTrue(deploymentAsyncResult.succeeded());

                        logger.info("Deployment completed. Starting the test...");
                        completionHandler.handle(Future.succeededFuture());
                    });
            });
        });
    }
}
