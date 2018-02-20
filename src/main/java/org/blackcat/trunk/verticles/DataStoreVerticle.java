package org.blackcat.trunk.verticles;

import de.braintags.io.vertx.pojomapper.dataaccess.query.IQuery;
import de.braintags.io.vertx.pojomapper.dataaccess.query.IQueryResult;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWrite;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWriteEntry;
import de.braintags.io.vertx.pojomapper.dataaccess.write.IWriteResult;
import de.braintags.io.vertx.pojomapper.mongo.MongoDataStore;
import io.vertx.core.*;
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

final public class DataStoreVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(DataStoreVerticle.class);
    private MongoDataStore mongoDataStore;

    public static final int ERR_COULD_NOT_CREATE_USER  = 1;
    public static final int ERR_COULD_NOT_FIND_SHARE   = 2;
    public static final int ERR_COULD_NOT_UPDATE_SHARE = 3;
    public static final int ERR_UNSUPPORTED_QUERY_TYPE = 4;

    @Override
    public void start(Future<Void> startFuture) {
        Context context = vertx.getOrCreateContext();

        Configuration configuration = new Configuration(context.config());
        String connectionString = String.format("%s://%s:%s",
            configuration.getDatabaseType(),
            configuration.getDatabaseHost(),
            configuration.getDatabasePort());

        logger.info("DB connection string: {}", connectionString);
        JsonObject mongoConfig = new JsonObject()
                                     .put("connection_string", connectionString)
                                     .put("db_name", configuration.getDatabaseName());

        MongoClient mongoClient = MongoClient.createShared(vertx, mongoConfig);
        mongoDataStore = new MongoDataStore(vertx, mongoClient, mongoConfig);

        /* determine if mongo is alive and well... */
        mongoClient.getCollections(ar -> {
            if (ar.succeeded()) {
                setupMessageHandlers();
                startFuture.complete();
            }
        });
    }

    private void setupMessageHandlers() {
        vertx.eventBus()
            .consumer("data-store", msg -> {
                JsonObject obj = (JsonObject) msg.body();
                String queryType = obj.getString("type");
                JsonObject params = obj.getJsonObject("params");

                /* msg dispatch */
                if (queryType.equals(QueryType.FIND_CREATE_USER.getTag())) {
                    findCreateUser(params, userMapperAsyncResult -> {
                        if (userMapperAsyncResult.failed())
                            msg.fail(ERR_COULD_NOT_CREATE_USER, "Could not create user");
                        else {
                            UserMapper userMapper = userMapperAsyncResult.result();
                            msg.reply(JsonObject.mapFrom(userMapper));
                        }
                    });
                } else if (queryType.equals(QueryType.FIND_SHARE.getTag())) {
                    findShare(params, shareMapperAsyncResult -> {
                        if (shareMapperAsyncResult.failed())
                            msg.fail(ERR_COULD_NOT_FIND_SHARE, "Could not find share");
                        else {
                            ShareMapper shareMapper = shareMapperAsyncResult.result();
                            msg.reply(JsonObject.mapFrom(shareMapper));
                        }
                    });
                } else if (queryType.equals(QueryType.FIND_UPDATE_SHARE.getTag())) {
                    findUpdateShare(params, shareMapperAsyncResult -> {
                        if (shareMapperAsyncResult.failed())
                            msg.fail(ERR_COULD_NOT_UPDATE_SHARE, "Could not update share");
                        else {
                            ShareMapper shareMapper = shareMapperAsyncResult.result();
                            msg.reply(JsonObject.mapFrom(shareMapper));
                        }
                    });
                } else {
                    logger.error("Unsupported query type: {}", queryType);
                    msg.fail(ERR_UNSUPPORTED_QUERY_TYPE, "Unsupported query type");
                }
            });
    }

    private void findCreateUser(JsonObject params, Handler<AsyncResult<UserMapper>> handler) {
        String email = params.getString("email");

        IQuery<UserMapper> query = mongoDataStore.createQuery(UserMapper.class);
        query.field("email").is(email);
        query.execute(queryAsyncResult -> {
            if (queryAsyncResult.failed()) {
                Throwable cause = queryAsyncResult.cause();
                logger.error(cause.toString());
                handler.handle(Future.failedFuture(cause));
            } else {
                IQueryResult<UserMapper> queryResult = queryAsyncResult.result();
                if (!queryResult.isEmpty()) {
                    queryResult.iterator().next(nextAsyncResult -> {
                        if (nextAsyncResult.failed()) {
                            Throwable cause = nextAsyncResult.cause();
                            logger.error(cause.toString());
                            handler.handle(Future.failedFuture(cause));
                        } else {
                            UserMapper userMapper = nextAsyncResult.result();
                            logger.debug("Found matching user for {}: {}", email, userMapper);
                            handler.handle(Future.succeededFuture(userMapper));
                        }
                    });
                } else {
                    UserMapper userMapper = new UserMapper();
                    userMapper.setEmail(email);
                    userMapper.setUuid(UUID.randomUUID().toString());

                    IWrite<UserMapper> write = mongoDataStore.createWrite(UserMapper.class);
                    write.add(userMapper);

                    write.save(result -> {
                        if (result.failed()) {
                            Throwable cause = result.cause();
                            logger.error(cause.toString());
                            handler.handle(Future.failedFuture(cause));
                        } else {
                            IWriteResult writeResult = result.result();
                            IWriteEntry entry = writeResult.iterator().next();
                            logger.trace("Created new userMapper for {}: {}", email, entry.getStoreObject());
                            handler.handle(Future.succeededFuture(userMapper));
                        }
                    });
                }
            }
        });
    }

    private void findShare(JsonObject params, Handler<AsyncResult<ShareMapper>> handler) {
        String collectionPath = params.getString("collectionPath");

        IQuery<ShareMapper> shareQuery = mongoDataStore.createQuery(ShareMapper.class);
        shareQuery.field("collectionPath").is(collectionPath);
        shareQuery.execute(shareQueryAsyncResult -> {
            if (shareQueryAsyncResult.failed()) {
                Throwable cause = shareQueryAsyncResult.cause();
                logger.error(cause.toString());
                handler.handle(Future.failedFuture(cause));
            } else {
                IQueryResult<ShareMapper> shareQueryResult = shareQueryAsyncResult.result();
                if (!shareQueryResult.isEmpty()) {
                    shareQueryResult.iterator().next(shareNextAsyncResult -> {
                        if (shareNextAsyncResult.failed()) {
                            Throwable cause = shareNextAsyncResult.cause();
                            logger.error(cause);
                            handler.handle(Future.failedFuture(cause));
                        } else {
                            ShareMapper shareMapper = shareNextAsyncResult.result();
                            logger.trace("Found sharing info {} for {}", shareMapper, collectionPath);
                            handler.handle(Future.succeededFuture(shareMapper));
                        }
                    });
                } else {
                    logger.trace("No sharing info found for {}", collectionPath);
                    ShareMapper shareMapper = new ShareMapper();
                    shareMapper.setCollectionPath(collectionPath);
                    handler.handle(Future.succeededFuture(shareMapper));
                }
            }
        });
    }

    private void findUpdateShare(JsonObject params, Handler<AsyncResult<ShareMapper>> handler) {
        UserMapper owner = params.getJsonObject("owner").mapTo(UserMapper.class);
        String collectionPath = params.getString("collectionPath");
        List<String> authorizedUsers = params.getJsonArray("authorizedUsers").getList();

        findShare(new JsonObject()
                      .put("collectionPath", collectionPath), shareMapperAsyncResult -> {

            if (shareMapperAsyncResult.failed()) {
                Throwable cause = shareMapperAsyncResult.cause();
                logger.error(cause.toString());
                handler.handle(Future.failedFuture(cause));
            } else {
                ShareMapper shareMapper = shareMapperAsyncResult.result();
                IWrite<ShareMapper> shareWrite = mongoDataStore.createWrite(ShareMapper.class);

                shareMapper.setOwner(owner);
                shareMapper.setAuthorizedUsers(authorizedUsers);

                shareWrite.add(shareMapper);
                shareWrite.save(shareWriteAsyncResult -> {

                    if (shareWriteAsyncResult.failed()) {
                        Throwable cause = shareWriteAsyncResult.cause();
                        logger.error(cause);
                        handler.handle(Future.failedFuture(cause));
                    } else {
                        IWriteResult shareWriteResult = shareWriteAsyncResult.result();
                        IWriteEntry shareWriteEntry = shareWriteResult.iterator().next();

                        logger.trace("{} {}", shareWriteEntry.getAction(), shareWriteEntry.getStoreObject());
                        handler.handle(Future.succeededFuture(shareMapper));
                    }
                });
            }
        });
    }
}
