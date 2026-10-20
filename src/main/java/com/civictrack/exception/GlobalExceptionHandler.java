package com.civictrack.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns exceptions into friendly pages. Users never see stack traces or internal details. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, Model model, jakarta.servlet.http.HttpServletResponse response) {
        return render(HttpStatus.NOT_FOUND, ex.getMessage(), model, response);
    }

    @ExceptionHandler({NoResourceFoundException.class, MethodArgumentTypeMismatchException.class})
    public String handleMissingPage(Exception ex, Model model, jakarta.servlet.http.HttpServletResponse response) {
        return render(HttpStatus.NOT_FOUND, "The page you asked for does not exist.", model, response);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public String handleForbidden(ForbiddenOperationException ex, Model model, jakarta.servlet.http.HttpServletResponse response) {
        return render(HttpStatus.FORBIDDEN, ex.getMessage(), model, response);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String handleBusinessRule(BusinessRuleException ex, Model model, jakarta.servlet.http.HttpServletResponse response) {
        return render(HttpStatus.BAD_REQUEST, ex.getMessage(), model, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(MaxUploadSizeExceededException ex, Model model, jakarta.servlet.http.HttpServletResponse response) {
        return render(HttpStatus.BAD_REQUEST, "The uploaded file is too large (maximum 2 MB).", model, response);
    }

    @ExceptionHandler(Exception.class)
    public String handleAnything(Exception ex, Model model, jakarta.servlet.http.HttpServletResponse response) {
        if (ex instanceof ErrorResponse er) { // e.g. 405 Method Not Allowed, 400 Bad Request
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return render(status, "The request could not be processed.", model, response);
            }
        }
        log.error("Unexpected error", ex); // full detail goes to the server log only
        return render(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again later.", model, response);
    }

    private String render(HttpStatus status, String message, Model model, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(status.value());
        model.addAttribute("status", status.value());
        model.addAttribute("message", message);
        return "error";
    }
}
