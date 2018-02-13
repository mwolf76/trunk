package org.blackcat.trunk.resource.exceptions;

public class ConflictException extends ResourceException {

    public ConflictException(String s) {
        super(s);
    }

    public ConflictException(Throwable throwable) {
        super(throwable);
    }
}
