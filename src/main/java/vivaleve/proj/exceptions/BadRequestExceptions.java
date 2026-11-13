package vivaleve.proj.exceptions;

import java.io.Serial;

public class BadRequestExceptions extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BadRequestExceptions(String message) {
        super(message);
    }
}
