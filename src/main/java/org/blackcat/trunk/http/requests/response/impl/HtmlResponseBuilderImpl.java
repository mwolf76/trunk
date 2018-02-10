package org.blackcat.trunk.http.requests.response.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.TemplateEngine;
import org.blackcat.trunk.http.ResponseStatus;
import org.blackcat.trunk.http.Headers;
import org.blackcat.trunk.http.requests.response.HtmlResponseBuilder;
import org.blackcat.trunk.http.requests.response.ResponseBuilder;

public final class HtmlResponseBuilderImpl implements HtmlResponseBuilder {

    private final TemplateEngine engine;
    private final String templateDir = "templates/";

    public HtmlResponseBuilderImpl(TemplateEngine engine) {
        this.engine = engine;
    }

    @Override
    public void success(RoutingContext ctx, String templateName) {
        engine.render(ctx, templateDir + templateName, asyncResult -> {
            if (asyncResult.failed()) {
                ctx.fail(asyncResult.cause());
            } else {
                Buffer result = asyncResult.result();
                ctx.response()
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                    .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
                    .end(result);
            }
        });
    }

//    @Override
//    public void ok(RoutingContext ctx) {
//        ctx.response()
//            .setStatusCode(ResponseStatus.OK.getStatusCode())
//            .setStatusMessage(ResponseStatus.OK.getStatusMessage())
//            .end(ResponseStatus.OK.toString());
//    }

    @Override
    public void badRequest(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.BAD_REQUEST, "bad-request");
    }

    @Override
    public void forbidden(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.FORBIDDEN, "forbidden");
    }

    @Override
    public void notFound(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.NOT_FOUND, "not-found");
    }

    @Override
    public void methodNotAllowed(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.METHOD_NOT_ALLOWED, "method-not-allowed");
    }

    @Override
    public void conflict(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.INTERNAL_SERVER_ERROR, "conflict");
    }

    @Override
    public void notAcceptable(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.NOT_ACCEPTABLE, "not-acceptable");
    }

    @Override
    public void internalServerError(RoutingContext ctx) {
        makeErrorResponse(ctx, ResponseStatus.INTERNAL_SERVER_ERROR, "internal-error");
    }

    private void makeErrorResponse(RoutingContext ctx, ResponseStatus status, String templateName) {
        HttpServerResponse response = ctx.response();
        response
            .setStatusCode(status.getStatusCode())
            .setStatusMessage(status.getStatusMessage());

        engine.render(ctx, templateDir + templateName, asyncResult -> {
            if (asyncResult.failed())
                ctx.fail(asyncResult.cause());
            else {
                response
                    .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                    .end(asyncResult.result());
            }
        });
    }
}
