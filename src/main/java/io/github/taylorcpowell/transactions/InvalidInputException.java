package io.github.taylorcpowell.transactions;

import java.io.IOException;

/** Input could not be read as a JSON array of transactions. */
final class InvalidInputException extends IOException {

    private static final long serialVersionUID = 1L;

    InvalidInputException(String message) {
        super(message);
    }

    InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
