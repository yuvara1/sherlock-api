package in.sherlock.project.presentation;

import in.sherlock.project.domain.ProjectException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ProjectExceptionHandler {
    @ExceptionHandler(ProjectException.class)
    ProblemDetail project(ProjectException exception) { return problem(exception.status(), exception.code(), exception.getMessage()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict() { return problem(409, "CONFLICT", "A resource with the same identifier, project slug, or email already exists."); }
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ProblemDetail invalid() { return problem(400, "VALIDATION_ERROR", "The request contains invalid or missing fields."); }
    private static ProblemDetail problem(int status, String code, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        result.setProperty("code", code);
        return result;
    }
}
