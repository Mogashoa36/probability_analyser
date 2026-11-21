package com.betanalyzer.esoccer.service.external;

/**
 * A failure worth showing the user verbatim: the provider is blocked, needs a
 * key, or answered with something unusable. Anything else is a bug.
 */
public class BetwayDataException extends RuntimeException {
    public BetwayDataException(String message) { super(message); }
    public BetwayDataException(String message, Throwable cause) { super(message, cause); }
}
