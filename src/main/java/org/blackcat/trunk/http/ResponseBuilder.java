package org.blackcat.trunk.http;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.TemplateEngine;

public class ResponseBuilder {

    private TemplateEngine templateEngine;
    private Logger logger;

    ResponseBuilder(TemplateEngine templateEngine, Logger logger) {
        this.templateEngine = templateEngine;
        this.logger = logger;
    }

    /* 200 */ public void ok(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Ok: {}", request.uri());

        ctx.response()
            .setStatusCode(StatusCode.OK.getStatusCode())
            .setStatusMessage(StatusCode.OK.getStatusMessage())
            .end(StatusCode.OK.toString());
    }

    /* 200 */ public void done(RoutingContext ctx) {
        ctx.response()
            .end();
    }

    /* 302 */ public void found(RoutingContext ctx, String targetURI) {
        logger.debug("Redirecting to {}", targetURI);
        ctx.response()
            .putHeader(Headers.LOCATION_HEADER, targetURI)
            .setStatusCode(StatusCode.FOUND.getStatusCode())
            .setStatusMessage(StatusCode.FOUND.getStatusMessage())
            .end();
    }

    /* 304 */ public void notModified(RoutingContext ctx, String etag) {
        ctx.response()
            .setStatusCode(StatusCode.NOT_MODIFIED.getStatusCode())
            .setStatusMessage(StatusCode.NOT_MODIFIED.getStatusMessage())
            .putHeader(Headers.ETAG_HEADER, etag)
            .putHeader(Headers.CONTENT_LENGTH_HEADER, "0")
            .end();
    }

    /* 400 */ public void badRequest(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Bad Request: {}", request.uri());

        request.response()
            .setStatusCode(StatusCode.BAD_REQUEST.getStatusCode())
            .setStatusMessage(StatusCode.BAD_REQUEST.getStatusMessage())
            .end();
    }

    /* 401 */ public void forbidden(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        final MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);
        boolean html = (accept != null && accept.contains("text/html"));
        boolean json = (accept != null && accept.contains("application/json"));

        logger.debug("Resource not found: {}", request.uri());
        HttpServerResponse response = ctx.response();
        response
            .setStatusCode(StatusCode.FORBIDDEN.getStatusCode())
            .setStatusMessage(StatusCode.FORBIDDEN.getStatusMessage());

        if (html) {
            templateEngine.render(ctx, "templates/forbidden", asyncResult -> {
                if (asyncResult.succeeded()) {
                    response
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .end(asyncResult.result());
                }
            });
        } else if (json) {
            response
                .end(new JsonObject()
                         .put("status", "error")
                         .put("message", "Not Found")
                         .encodePrettily());
        } else /* assume: text/plain */ {
            response
                .end(StatusCode.NOT_FOUND.toString());
        }
    }

    /* 404 */ public void notFound(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        MultiMap headers = request.headers();

        String accept = headers.get(Headers.ACCEPT_HEADER);
        boolean html = (accept != null && accept.contains("text/html"));
        boolean json = (accept != null && accept.contains("application/json"));

        HttpServerResponse response = ctx.response();
        response
            .setStatusCode(StatusCode.NOT_FOUND.getStatusCode())
            .setStatusMessage(StatusCode.NOT_FOUND.getStatusMessage());

        if (html) {
            templateEngine.render(ctx, "templates/notfound", asyncResult -> {
                if (asyncResult.succeeded()) {
                    response
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .end(asyncResult.result());
                }
            });
        } else if (json) {
            response
                .end(new JsonObject()
                         .put("status", "error")
                         .put("message", "Not Found")
                         .encodePrettily());
        } else /* assume: text/plain */ {
            response
                .end(StatusCode.NOT_FOUND.toString());
        }
    }

    /* 405 */ public void notAllowed(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Not allowed: {}", request.uri());

        request.response()
            .setStatusCode(StatusCode.METHOD_NOT_ALLOWED.getStatusCode())
            .setStatusMessage(StatusCode.METHOD_NOT_ALLOWED.getStatusMessage())
            .end(StatusCode.METHOD_NOT_ALLOWED.toString());
    }

    /* 409 */ public void conflict(RoutingContext ctx, String message) {
        HttpServerRequest request = ctx.request();
        logger.debug("Conflict: {}", message);

        request.response()
            .setStatusCode(StatusCode.CONFLICT.getStatusCode())
            .setStatusMessage(StatusCode.CONFLICT.getStatusMessage())
            .end(message);
    }

    /* 406 */ public void notAcceptable(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.debug("Not acceptable: {}", request.uri());

        request.response()
            .setStatusCode(StatusCode.NOT_ACCEPTABLE.getStatusCode())
            .setStatusMessage(StatusCode.NOT_ACCEPTABLE.getStatusMessage())
            .end(StatusCode.NOT_ACCEPTABLE.toString());
    }

    /* 500 */ public void internalServerError(RoutingContext ctx) {
        HttpServerRequest request = ctx.request();
        logger.error("Internal error for request {}: {}",
            request.uri(), ctx.failure().getMessage());

        final MultiMap headers = request.headers();
        String accept = headers.get(Headers.ACCEPT_HEADER);
        boolean html = (accept != null && accept.contains("text/html"));
        boolean json = (accept != null && accept.contains("application/json"));

        HttpServerResponse response = ctx.response();
        response
            .setStatusCode(StatusCode.INTERNAL_SERVER_ERROR.getStatusCode())
            .setStatusMessage(StatusCode.INTERNAL_SERVER_ERROR.getStatusMessage());

        if (html) {
            templateEngine.render(ctx, "templates/internal", asyncResult -> {
                if (asyncResult.succeeded()) {
                    response
                        .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
                        .end(asyncResult.result());
                }
            });
        } else if (json) {
            response
                .end(new JsonObject()
                         .put("status", "error")
                         .put("message", "Not Found")
                         .encodePrettily());
        } else /* assume: text/plain */ {
            response
                .end(StatusCode.INTERNAL_SERVER_ERROR.toString());
        }
    }

    void htmlResponse(RoutingContext ctx, Buffer result) {
        ctx.response()
            .putHeader(Headers.CONTENT_TYPE_HEADER, "text/html; charset=utf-8")
            .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(result.length()))
            .end(result);
    }

    void jsonResponse(RoutingContext ctx, String body) {
        ctx.response()
            .putHeader(Headers.CONTENT_LENGTH_HEADER, String.valueOf(body.length()))
            .putHeader(Headers.CONTENT_TYPE_HEADER, "application/json; charset=utf-8")
            .end(body);
    }
}
