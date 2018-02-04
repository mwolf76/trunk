package org.blackcat.trunk.verticles;

/**
 * WebServerVerticle - provides a REST interface to a disk storage.
 *
 * @author (c) 2017 marco DOT pensallorto AT gmail DOT com
 */

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.templ.PebbleTemplateEngine;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.http.RequestHandler;
import org.blackcat.trunk.storage.Storage;
import org.blackcat.trunk.storage.impl.FileSystemStorage;

import java.nio.file.Paths;

public class WebServerVerticle extends AbstractVerticle {

    private Logger logger = LoggerFactory.getLogger(WebServerVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        /* retrieve configuration object from vertx ctx */
        Configuration configuration = Configuration.create(vertx.getOrCreateContext().config());

        /* configure disk storage */
        Storage storage = FileSystemStorage.create(vertx, Paths.get(configuration.getStorageRoot()));

        /* configure request handler */
        Handler<HttpServerRequest> handler = RequestHandler.create(vertx, configuration, storage);

        HttpServerOptions httpServerOptions =
            new HttpServerOptions()
                // in vertx 2x 100-continues was activated per default, in vertx 3x it is off per default.
                .setHandle100ContinueAutomatically(true);

        if (configuration.isSSLEnabled()) {
            String keystoreFilename = configuration.getKeystoreFilename();
            String keystorePassword = configuration.getKeystorePassword();

            httpServerOptions
                .setSsl(true)
                .setKeyStoreOptions(
                    new JksOptions()
                        .setPath(keystoreFilename)
                        .setPassword(keystorePassword));
        }

        int httpPort = configuration.getHttpPort();
        vertx.createHttpServer(httpServerOptions)
            .requestHandler(handler)
            .listen(httpPort, result -> {
                if (result.succeeded()) {
                    logger.info("Web server is now ready to accept requests on port {}.", httpPort);
                    startFuture.complete();
                }
            });
    }
}
