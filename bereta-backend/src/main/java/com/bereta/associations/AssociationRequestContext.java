package com.bereta.associations;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

@Component
public class AssociationRequestContext {
    public static final String REQUEST_ATTRIBUTE = AssociationRequestContext.class.getName() + ".activeId";

    public UUID requireId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez une association.");
        }
        Object value = servletAttributes.getRequest().getAttribute(REQUEST_ATTRIBUTE);
        if (!(value instanceof UUID id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sélectionnez une association.");
        }
        return id;
    }
}
