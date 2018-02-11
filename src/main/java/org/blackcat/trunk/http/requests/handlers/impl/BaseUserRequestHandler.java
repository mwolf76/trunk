package org.blackcat.trunk.http.requests.handlers.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.response.impl.HtmlResponseBuilderImpl;
import org.blackcat.trunk.http.requests.response.impl.JsonResponseBuilderImpl;
import org.blackcat.trunk.storage.Storage;

import java.text.MessageFormat;

abstract public class BaseUserRequestHandler implements Handler<RoutingContext> {

    /* general refs */
    protected Vertx vertx;
    protected Storage storage;
    protected Configuration configuration;
    protected JsonResponseBuilderImpl jsonResponseBuilder;
    protected HtmlResponseBuilderImpl htmlResponseBuilder;

    final private Logger logger = LoggerFactory.getLogger(BaseUserRequestHandler.class);

    private void preprocess(RoutingContext ctx) {

        vertx = ctx.get("vertx");
        if (vertx == null) {
            ctx.fail(new BaseUserRequestException("vertx == null"));
        }

        storage = ctx.get("storage");
        if (storage == null) {
            ctx.fail(new BaseUserRequestException("storage == null"));
        }

        configuration = ctx.get("configuration");
        if (configuration == null) {
            ctx.fail(new BaseUserRequestException("configuration == null"));
        }

        jsonResponseBuilder = ctx.get("jsonResponseBuilder");
        if (jsonResponseBuilder == null) {
            ctx.fail(new BaseUserRequestException("jsonResponseBuilder == null"));
        }

        htmlResponseBuilder = ctx.get("htmlResponseBuilder");
        if (htmlResponseBuilder == null) {
            ctx.fail(new BaseUserRequestException("htmlResponseBuilder == null"));
        }
        
        ctx.put("requestType", doesAcceptJson(ctx) ? RequestType.JSON : RequestType.HTML);
    }

    private boolean doesAcceptJson(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);

        return (accept != null && accept.contains("application/json"));
    }

    @Override
    public void handle(RoutingContext ctx) {
        logger.debug(MessageFormat.format("Invoking {0} ...", this.getClass().toString()));
        preprocess(ctx);
    }

    public void checkHtmlRequest(RoutingContext ctx, Handler<Void> handler) {
        if (! ctx.get("requestType").equals(RequestType.HTML))
            htmlResponseBuilder.badRequest(ctx);

        else handler.handle(null);
    }

    public void checkJsonRequest(RoutingContext ctx, Handler<Void> handler) {
        if (! ctx.get("requestType").equals(RequestType.JSON))
            jsonResponseBuilder.badRequest(ctx);

        else handler.handle(null);
    }
}
