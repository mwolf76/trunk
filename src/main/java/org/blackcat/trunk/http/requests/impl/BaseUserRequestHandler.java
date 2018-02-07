package org.blackcat.trunk.http.requests.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.trunk.conf.Configuration;
import org.blackcat.trunk.http.ResponseBuilder;
import org.blackcat.trunk.storage.Storage;

import java.text.MessageFormat;

abstract public class BaseUserRequestHandler implements Handler<RoutingContext> {

    /* general refs */
    protected Vertx vertx;
    protected Storage storage;
    protected Configuration configuration;
    protected TemplateEngine templateEngine;
    protected ResponseBuilder responseBuilder;

    final private Logger logger = LoggerFactory.getLogger(BaseUserRequestHandler.class);

    private void fetchDependencies(RoutingContext ctx) {

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

        templateEngine = ctx.get("templateEngine");
        if (templateEngine == null) {
            ctx.fail(new BaseUserRequestException("templateEngine == null"));
        }

        responseBuilder = ctx.get("responseBuilder");
        if (responseBuilder == null) {
            ctx.fail(new BaseUserRequestException("responseBuilder == null"));
        }
    }

    @Override
    public void handle(RoutingContext ctx) {
        logger.debug(MessageFormat.format("Invoking {0} ...", this.getClass().toString()));
        fetchDependencies(ctx);

        // ctx.request().resume();
    }
}
