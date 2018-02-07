package org.blackcat.trunk.http.requests;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.http.requests.impl.MainHandlerImpl;
import org.blackcat.trunk.storage.Storage;

public interface MainHandler extends Handler<HttpServerRequest> {
    /**
     * Create a new handler
     *
     * @return  the handler
     */
    static MainHandler create(Vertx vertx, Configuration configuration, Storage storage) {
        return new MainHandlerImpl(vertx, configuration, storage);
    }
}
