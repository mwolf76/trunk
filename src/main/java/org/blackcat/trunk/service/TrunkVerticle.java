package org.blackcat.trunk.service;

/**
 * TrunkVerticle - provides a REST interface to a disk storage.
 * @author (c) 2017 marco DOT pensallorto AT gmail DOT com
 */

import de.braintags.io.vertx.pojomapper.mongo.MongoDataStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.templ.PebbleTemplateEngine;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.http.RequestHandler;
import org.blackcat.trunk.storage.Storage;
import org.blackcat.trunk.storage.impl.FileSystemStorage;

import java.nio.file.Paths;

public class TrunkVerticle extends AbstractVerticle {

    private Logger logger;

    @Override
    public void start(Future<Void> startFuture) {

        logger = LoggerFactory.getLogger(TrunkVerticle.class);

        /* retrieve configuration object from vert.x ctx */
        final Configuration configuration = new Configuration(vertx.getOrCreateContext().config());
        logger.info("Configuration: {}", configuration.toString());

        /* configure Pebble template engine */
        final PebbleTemplateEngine pebbleEngine = PebbleTemplateEngine.create(vertx);

        /* connect to mongo data store */
        String connectionString = String.format("%s://%s:%s",
                configuration.getDatabaseType(),
                configuration.getDatabaseHost(),
                configuration.getDatabasePort());
        logger.info("DB connection string: {}, name: {}", connectionString);

        JsonObject mongoConfig = new JsonObject()
                .put("connection_string", connectionString)
                .put("db_name", configuration.getDatabaseName());

        MongoClient mongoClient = MongoClient.createNonShared(vertx, mongoConfig);
        MongoDataStore mongoDataStore = new MongoDataStore(vertx, mongoClient, mongoConfig);

        Storage storage = new FileSystemStorage(vertx, logger,
                Paths.get(configuration.getStorageRoot()));

        /* configure request handler */
        Handler<HttpServerRequest> handler =
                new RequestHandler(vertx, pebbleEngine, logger, configuration, mongoDataStore, storage);

        HttpServerOptions httpServerOptions = new HttpServerOptions()
                // in Vert.x 2x 100-continues was activated per default, in vert.x 3x it is off per default.
                .setHandle100ContinueAutomatically(true);

        if (configuration.sslEnabled()) {
                httpServerOptions
                        .setSsl(true)
                        .setKeyStoreOptions(
                                new JksOptions()
                                        .setPath(configuration.getKeystoreFilename())
                                        .setPassword(configuration.getKeystorePassword()));
        }

        vertx.createHttpServer(httpServerOptions)
                .requestHandler(handler)
                .listen(configuration.getHttpPort(), result -> {

                    if (result.succeeded()) {
                        logger.info("Ready to accept requests on port {}.",
                                String.valueOf(configuration.getHttpPort()));

                        startFuture.complete();
                    } else {
                        startFuture.fail(result.cause());
                    }
                });
    }
}
