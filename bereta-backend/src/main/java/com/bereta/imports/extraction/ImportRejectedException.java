package com.bereta.imports.extraction;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Document refusé ou illisible : message clair destiné à l'utilisateur. */
public class ImportRejectedException extends ResponseStatusException {

    public ImportRejectedException(HttpStatus status, String message) {
        super(status, message);
    }

    public static ImportRejectedException unreadable(String message) {
        return new ImportRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
