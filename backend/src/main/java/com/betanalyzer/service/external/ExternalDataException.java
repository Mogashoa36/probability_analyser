package com.betanalyzer.service.external;

/**
 * A failure worth showing the user verbatim: the provider is blocked, needs a
 * key, or answered with something unusable. Anything else (a plain
 * NullPointerException, a JSON parse error) is a bug and should surface as
 * one.
 */
public class ExternalDataException extends RuntimeException {

    public ExternalDataException(String message) {
        super(message);
    }

    public ExternalDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
