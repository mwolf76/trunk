package org.blackcat.trunk.datastore;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.blackcat.trunk.mappers.ShareMapper;
import org.blackcat.trunk.mappers.UserMapper;
import org.blackcat.trunk.queries.Queries;
import org.blackcat.trunk.verticles.DataStoreVerticle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;


@RunWith(VertxUnitRunner.class)
public class DataStoreTest {
    Logger logger = LoggerFactory.getLogger(DataStoreTest.class);

    @Rule
    public final RunTestOnContext rule = new RunTestOnContext(Vertx::vertx);

    private final String testEmail = "admin@host.co";
    private final String anotherEmail = "guest@evil.co";

    private static JsonObject getConfiguration() {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get("conf/config.json"));
            return new JsonObject(new String(bytes));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Test(timeout = 5000)
    public void addNewUser(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            logger.info("Deployed verticle");
            Queries.findCreateUserEntityByEmail(rule.vertx(), testEmail, ar -> {
                if (ar.failed())
                    context.fail(ar.cause());
                else {
                    UserMapper userMapper = ar.result();
                    context.assertEquals(userMapper.getEmail(), testEmail);
                    async.complete();
                }
            });
        });
    }

    @Test(timeout = 5000)
    public void findExistingUser(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            Queries.findCreateUserEntityByEmail(rule.vertx(), testEmail, ar -> {
                if (ar.failed())
                    context.fail(ar.cause());
                else {
                    UserMapper userMapper = ar.result();
                    context.assertEquals(userMapper.getEmail(), testEmail);

                    Queries.findCreateUserEntityByEmail(rule.vertx(), testEmail, ar2 -> {
                        if (ar2.failed())
                            context.fail(ar2.cause());
                        else {
                            UserMapper mapper = ar2.result();
                            context.assertEquals(mapper.getEmail(), testEmail);
                            async.complete();
                        }
                    });
                }
            });
        });
    }

    @Test(timeout = 5000)
    public void findBlankShare(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            Queries.findShareEntity(rule.vertx(), Paths.get("test"), ar -> {
                if (ar.failed())
                    context.fail(ar.cause());
                else {
                    ShareMapper shareMapper = ar.result();
                    context.assertTrue(isBlank(shareMapper));
                    async.complete();
                }
            });
        });
    }

    private boolean isBlank(ShareMapper shareMapper) {
        return shareMapper.getId() == null
                   && shareMapper.getOwner() == null
                   && shareMapper.getAuthorizedUsers().isEmpty();
    }

    @Test(timeout = 5000)
    public void updateBlankShare(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            Queries.findCreateUserEntityByEmail(rule.vertx(), testEmail, ar -> {
                if (ar.failed())
                    context.fail(ar.cause());
                else {
                    UserMapper userMapper = ar.result();
                    Queries.findUpdateShareEntity(rule.vertx(), userMapper,
                        Paths.get("test"), Arrays.asList(), ar1 -> {
                            if (ar1.failed())
                                context.fail(ar1.cause());
                            else {
                                Queries.findShareEntity(rule.vertx(), Paths.get("test"), ar2 -> {
                                    if (ar2.failed())
                                        context.fail(ar2.cause());
                                    else {
                                        ShareMapper shareMapper = ar2.result();
                                        context.assertEquals(userMapper, shareMapper.getOwner());
                                        async.complete();
                                    }
                                });
                            }
                        });
                }
            });
        });
    }

    @Test(timeout = 5000)
    public void findExistingShare(TestContext context) {
        Async async = context.async();
        deployForTesting(rule.vertx(), done -> {
            Queries.findShareEntity(rule.vertx(), Paths.get("test"), ar -> {
                if (ar.failed())
                    context.fail(ar.cause());
                else {
                    ShareMapper shareMapper = ar.result();
                    context.assertTrue(isBlank(shareMapper));
                    async.complete();
                }
            });
        });
    }

    private void deployForTesting(Vertx vertx, Handler<AsyncResult<String>> handler) {
        String uri = "mongodb://localhost";
        String db = "trunk";

        JsonObject mongoconfig = new JsonObject()
                                     .put("connection_string", uri)
                                     .put("db_name", db);

        MongoClient mongoClient = MongoClient.createShared(rule.vertx(), mongoconfig);
        mongoClient.dropCollection("UserMapper", _1 -> {
            mongoClient.dropCollection("ShareMapper", _2 -> {
                DeploymentOptions deploymentOptions = new DeploymentOptions();
                deploymentOptions.setConfig(getConfiguration());
                vertx.deployVerticle(DataStoreVerticle.class.getName(), deploymentOptions, ar -> {
                    if (ar.failed())
                        logger.error(ar.cause());
                    else {
                        logger.info("Done deploying verticles. Now starting test ... ");
                        handler.handle(ar);
                    }
                });
            });
        });
    }

}

