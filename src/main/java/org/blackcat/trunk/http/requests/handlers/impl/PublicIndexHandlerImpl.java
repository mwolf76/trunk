package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.handlers.PublicIndexHandler;

final public class PublicIndexHandlerImpl extends BaseUserRequestHandler implements PublicIndexHandler {

    final private Logger logger = LoggerFactory.getLogger(PublicIndexHandlerImpl.class);

    @Override
    public void handle(RoutingContext ctx) {
        super.handle(ctx);
        templateEngine.render(ctx, "templates/index", ar -> {
            if (ar.failed()) {
                ctx.fail(ar.cause());
            } else {
                Buffer result = ar.result();
                ctx.response()
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                    .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
                    .end(result);
            }
        });
    }
}
