package org.blackcat.trunk.http.requests.response;

import io.vertx.ext.web.RoutingContext;

public interface HtmlResponseBuilder extends ResponseBuilder {
    void success(RoutingContext ctx, String templateName);
}
