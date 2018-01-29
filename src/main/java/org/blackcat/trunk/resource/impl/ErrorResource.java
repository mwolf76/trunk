package org.blackcat.trunk.resource.impl;

import io.vertx.ext.web.RoutingContext;
import org.blackcat.trunk.http.ErrorResourceHandler;
import org.blackcat.trunk.http.ResponseBuilder;

final public class ErrorResource extends BaseResource {

    private boolean notFound = false;
    private boolean rejected = false;
    private boolean invalid = false;
    private boolean unit = false;

    private String message = null;
    public String getMessage() {
        return message;
    }

    @Override
    public int getOrdering() {
        return -1;
    }

    public boolean isNotFound() {
        return notFound;
    }

    public boolean isRejected() {
        return rejected;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public boolean isUnit() {
        return unit;
    }

    private ErrorResource()
    {}
    
    public static ErrorResource makeInvalid(String cause) {
        ErrorResource resource = new ErrorResource();
        resource.invalid = true;
        resource.message = cause;
        return resource;
    }

    public static ErrorResource makeNotFound() {
        ErrorResource resource = new ErrorResource();
        resource.notFound = true;
        return resource;
    }

    public static ErrorResource makeRejected() {
        ErrorResource resource = new ErrorResource();
        resource.rejected = true;
        return resource;
    }

    public static ErrorResource makeUnit() {
        ErrorResource resource = new ErrorResource();
        resource.unit = true;
        return resource;
    }
}
