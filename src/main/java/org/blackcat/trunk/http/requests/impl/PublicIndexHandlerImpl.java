package org.blackcat.trunk.http.requests.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.PublicIndexHandler;

public class PublicIndexHandlerImpl extends BaseUserRequestHandler implements PublicIndexHandler {
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
