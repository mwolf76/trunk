package org.blackcat.trunk.verticles;

import de.braintags.io.vertx.pojomapper.dataaccess.query.IQuery;
import de.braintags.io.vertx.pojomapper.dataaccess.query.IQueryResult;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWrite;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWriteEntry;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWriteResult;
import de.braintags.io.vertx.pojomapper.mongo.MongoDataStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.eventbus.data.QueryType;
import org.blackcat.trunk.mappers.ShareMapper;
import org.blackcat.trunk.mappers.UserMapper;

import java.util.List;
import java.util.UUID;

public class DataStoreVerticle extends AbstractVerticle {

    private Logger logger;
    private MongoDataStore mongoDataStore;

    @Override
    public void start(Future<Void> startFuture) {

        logger = LoggerFactory.getLogger(DataStoreVerticle.class);
        vertx.executeBlocking(future -> {

            /* retrieve configuration object from vert.x ctx */
            final Configuration configuration = new Configuration(vertx.getOrCreateContext().config());
            logger.info("Configuration: {}", configuration.toString());

            /* connect to mongo data store */
            String connectionString = String.format("%s://%s:%s",
                    configuration.getDatabaseType(),
                    configuration.getDatabaseHost(),
                    configuration.getDatabasePort());

            JsonObject mongoConfig = new JsonObject()
                    .put("connection_string", connectionString)
                    .put("db_name", configuration.getDatabaseName());

            MongoClient mongoClient = MongoClient.createShared(vertx, mongoConfig);
            mongoDataStore = new MongoDataStore(vertx, mongoClient, mongoConfig);

            vertx.eventBus()
                    .consumer("data-store", msg -> {
                        JsonObject obj = (JsonObject) msg.body();
                        String queryType = obj.getString("type");
                        JsonObject params = obj.getJsonObject("params");

                        /* msg dispatch */
                        if (queryType.equals(QueryType.FIND_CREATE_USER.getTag())) {
                            findCreateUser(params, user -> {
                                msg.reply(JsonObject.mapFrom(user));
                            });
                        } else if (queryType.equals(QueryType.FIND_SHARE.getTag())) {
                            findShare(params, share -> {
                               msg.reply(JsonObject.mapFrom(share));
                            });
                        } else if (queryType.equals(QueryType.FIND_UPDATE_SHARE.getTag())) {
                            findUpdateShare(params, share -> {
                                msg.reply(JsonObject.mapFrom(share));
                            });
                        } else {
                            logger.error("Unsupported query type: {}", queryType);
                        }
                    });

            future.complete();
        }, res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                Throwable cause = res.cause();
                startFuture.fail(cause);
            }
        });
    }

    private void findCreateUser(JsonObject params, Handler<UserMapper> handler) {

        /* fetch params */
        String email = params.getString("email");

        IQuery<UserMapper> query = mongoDataStore.createQuery(UserMapper.class);
        query.field("email").is(email);
        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<UserMapper> queryResult = queryAsyncResult.result();
                if (!queryResult.isEmpty()) {
                    queryResult.iterator().next(nextAsyncResult -> {
                        if (nextAsyncResult.failed()) {
                            Throwable cause = nextAsyncResult.cause();

                            logger.error(cause);
                            throw new RuntimeException(cause);
                        } else {
                            UserMapper userMapper = nextAsyncResult.result();

                            logger.trace("Found matching user for {}: {}", email, userMapper);
                            handler.handle(userMapper);
                        }
                    });
                } else {
                    /* User does not exist. create it */
                    UserMapper userMapper = new UserMapper();
                    userMapper.setEmail(email);
                    userMapper.setUuid(UUID.randomUUID().toString());

                    IWrite<UserMapper> write = mongoDataStore.createWrite(UserMapper.class);
                    write.add(userMapper);

                    write.save(result -> {
                        if (result.failed()) {
                            Throwable cause = result.cause();

                            logger.error(cause.toString());
                            throw new RuntimeException(cause);
                        } else {
                            IWriteResult writeResult = result.result();
                            IWriteEntry entry = writeResult.iterator().next();

                            logger.trace("Created new userMapper for {}: {}", email, entry.getStoreObject());
                            handler.handle(userMapper);
                        }
                    });
                }
            }
        });
    }

    private void findShare(JsonObject params, Handler<ShareMapper> handler) {

        /* fetch params */
        String collectionPath = params.getString("collectionPath");

        IQuery<ShareMapper> shareQuery = mongoDataStore.createQuery(ShareMapper.class);
        shareQuery.field("collectionPath").is(collectionPath);
        shareQuery.execute(shareQueryAsyncResult -> {

            if (shareQueryAsyncResult.failed()) {
                Throwable cause = shareQueryAsyncResult.cause();

                logger.error(cause);
                throw new RuntimeException(cause);
            } else {
                IQueryResult<ShareMapper> shareQueryResult = shareQueryAsyncResult.result();
                if (! shareQueryResult.isEmpty()) {
                    shareQueryResult.iterator().next(shareNextAsyncResult -> {
                        if (shareNextAsyncResult.failed()) {
                            Throwable cause = shareNextAsyncResult.cause();

                            logger.error(cause);
                            throw new RuntimeException(cause);
                        } else {
                            ShareMapper shareMapper = shareNextAsyncResult.result();

                            /* found share record */
                            logger.trace("Found sharing info {} for {}", shareMapper, collectionPath);
                            handler.handle(shareMapper);
                        }
                    });
                }
                else {
                    logger.trace("No sharing info found for {}", collectionPath);
                    ShareMapper shareMapper = new ShareMapper();
                    shareMapper.setCollectionPath(collectionPath);
                    handler.handle(shareMapper);
                }
            }
        });
    }

    private void findUpdateShare(JsonObject params, Handler<ShareMapper> handler) {

        /* fetch params */
        UserMapper owner = params.getJsonObject("owner").mapTo(UserMapper.class);
        String collectionPath = params.getString("collectionPath");
        List<String> authorizedUsers = params.getJsonArray("authorizedUsers").getList();

        findShare(new JsonObject()
                .put("collectionPath", collectionPath), shareMapper -> {

            IWrite<ShareMapper> shareWrite = mongoDataStore.createWrite(ShareMapper.class);

            shareMapper.setOwner(owner);
            shareMapper.setAuthorizedUsers(authorizedUsers);

            shareWrite.add(shareMapper);
            shareWrite.save(shareWriteAsyncResult -> {

                if (shareWriteAsyncResult.failed()) {
                    Throwable cause = shareWriteAsyncResult.cause();

                    logger.error(cause);
                    throw new RuntimeException(cause);
                } else {
                    IWriteResult shareWriteResult = shareWriteAsyncResult.result();
                    IWriteEntry shareWriteEntry = shareWriteResult.iterator().next();

                    logger.trace("{} {}",
                            shareWriteEntry.getAction(),
                            shareWriteEntry.getStoreObject());

                    handler.handle(shareMapper);
                }
            });
        });
    }
};
