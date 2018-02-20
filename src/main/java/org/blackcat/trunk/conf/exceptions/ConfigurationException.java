package org.blackcat.trunk.conf.exceptions;

/**
 * Created by markus on 4/26/17.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException() {
    }

    public ConfigurationException(String s) {
        super(s);
    }

    public ConfigurationException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public ConfigurationException(Throwable throwable) {
        super(throwable);
    }

    public ConfigurationException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1);
    }
}
