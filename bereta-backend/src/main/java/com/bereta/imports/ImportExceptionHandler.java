package com.bereta.imports;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Erreurs propres à l'import, renvoyées avec un message clair (format ProblemDetail, comme le reste de l'API). */
@RestControllerAdvice
public class ImportExceptionHandler {

    /** Levée avant le contrôleur, dès que la requête dépasse les limites d'envoi. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Fichier trop volumineux : 10 Mo maximum par fichier.");
    }

    /** Deux modifications simultanées du même import (double clic, deux onglets) : une seule l'emporte. */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ProblemDetail concurrent(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Cet import vient d’être modifié ou validé. Rechargez la page.");
    }
}
