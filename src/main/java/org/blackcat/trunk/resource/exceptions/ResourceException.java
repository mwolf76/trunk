package org.blackcat.trunk.resource.exceptions;

public class ResourceException extends RuntimeException {
    public ResourceException() {
    }

    public ResourceException(String s) {
        super(s);
    }

    public ResourceException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ResourceException(Throwable throwable) {
        super(throwable);
    }

    public ResourceException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
