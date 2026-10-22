package com.securewipe.backend.exception;

public class WipeOperationException extends RuntimeException {
    public WipeOperationException(String message) {
        super(message);
    }
    public WipeOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
