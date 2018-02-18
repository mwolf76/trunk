package org.blackcat.trunk.integration;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
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
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.verticles.MainVerticle;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@RunWith(VertxUnitRunner.class)
public class APITest {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Rule
    public final RunTestOnContext rule = new RunTestOnContext(Vertx::vertx);

    @Before
    public void setUp(TestContext context) {
        Async async = context.async();
        JsonObject jsonConfig = getJsonConfiguration();
        Configuration configuration = new Configuration(jsonConfig);
        context.put("configuration", configuration);

        Vertx vertx = rule.vertx();
        String rootPath = configuration.getStorageRoot();
        vertx.fileSystem().deleteRecursive(rootPath, true, _1 -> {
            vertx.fileSystem().mkdir(rootPath, _2 -> {
                DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(jsonConfig);
                vertx.deployVerticle(MainVerticle.class.getName(), deploymentOptions, r -> {
                    logger.info("Application is now ready");
                    async.complete();
                });
            });
        });
    }

    @Test
    public void getUserProtectedRootWithNoAccessToken(TestContext context) {
        Async async = context.async();
        Configuration configuration = context.get("configuration");

        // Make the real request to the protected resource
        WebClientOptions webClientOptions = new WebClientOptions();
        webClientOptions.setFollowRedirects(false);
        WebClient client = WebClient.create(rule.vertx(), webClientOptions);

        client
            .get(configuration.getHttpPort(), configuration.getHttpHost(), "/protected/root")
            .putHeader("Accept", "application/json")
            .send(responseAsyncResult -> {
                if (responseAsyncResult.failed()) {
                    Throwable cause = responseAsyncResult.cause();
                    context.fail(cause);
                } else {
                    /* expect Keycloak redirection */
                    HttpResponse<Buffer> response = responseAsyncResult.result();
                    context.assertEquals(302, response.statusCode());
                    async.complete();
                }
            });
    }

    @Test
    public void getUserProtectedRootWithValidCredentials(TestContext context) {
        Async async = context.async();
        JsonObject userCredentials = new JsonObject()
                                         .put("username", "admin")
                                         .put("password", "admin");

        getUserProtectedRootWithCredentials(context, userCredentials, responseAsyncResult -> {
            if (responseAsyncResult.failed()) {
                Throwable cause = responseAsyncResult.cause();
                context.fail(cause);
            } else {
                HttpResponse<Buffer> response = responseAsyncResult.result();
                context.assertEquals(200, response.statusCode());

                JsonObject jsonObject = response.bodyAsJsonObject();
                context.assertEquals("admin@myhost.co", jsonObject.getString("email"));

                String root = jsonObject.getString("root");
                checkIsValidUUID(context, root, async);
            }
        });
    }

    @Test
    public void getUserProtectedRootWithWrongCredentials(TestContext context) {
        JsonObject userCredentials = new JsonObject()
                                         .put("username", "admin")
                                         .put("password", "password");

        getUserProtectedRootWithCredentials(context, userCredentials, context.asyncAssertFailure());
    }

    private void getUserProtectedRootWithCredentials(TestContext context,
                                                     JsonObject userCredentials,
                                                     Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        Configuration configuration = context.get("configuration");

        OAuth2Auth oauth2 = KeycloakAuth.create(rule.vertx(),
            OAuth2FlowType.PASSWORD, configuration.buildKeyCloakConfiguration());

        oauth2.authenticate(userCredentials, userAsyncResult -> {
            if (userAsyncResult.failed()) {
                handler.handle(Future.failedFuture(userAsyncResult.cause()));
            } else {
                User user = userAsyncResult.result();
                String access_token = user.principal().getString("access_token");

                // Make the real request to the protected resource
                WebClient client = WebClient.create(rule.vertx());
                client
                    .get(configuration.getHttpPort(), configuration.getHttpHost(), "/protected/root")
                    .putHeader("Accept", "application/json")
                    .putHeader("Authorization", "Bearer " + access_token)
                    .send(handler);
            }
        });
    }

    private void checkIsValidUUID(TestContext context, String root, Async async) {
        try {
            UUID uuid = UUID.fromString(root);
            logger.info("User root check ok: " + uuid);
            async.complete();
        } catch (Exception e) {
            context.fail(e);
        }
    }

    private static JsonObject getJsonConfiguration() {
        final String jsonConfFile = "conf/config.json";
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(jsonConfFile));
            return new JsonObject(new String(bytes));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load " + jsonConfFile);
        }
    }

}
