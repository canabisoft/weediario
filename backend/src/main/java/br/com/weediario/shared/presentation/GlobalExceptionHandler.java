package br.com.weediario.shared.presentation;

import br.com.weediario.shared.application.ForbiddenActionException;
import br.com.weediario.shared.application.ModeratorOnlyException;
import br.com.weediario.diary.application.exception.GrowerNotAllowedException;
import br.com.weediario.identity.application.exception.HashingBusyException;
import br.com.weediario.identity.application.exception.InvalidCredentialsException;
import br.com.weediario.identity.application.exception.LoginThrottledException;
import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.shared.application.QuotaExceededException;
import br.com.weediario.shared.application.ResourceNotFoundException;
import br.com.weediario.treatment.application.ConsentRequiredException;
import br.com.weediario.shared.domain.DomainException;
import br.com.weediario.shared.infrastructure.observability.SecuritySignals;
import br.com.weediario.shared.infrastructure.observability.SecuritySignals.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Traduz exceções em respostas RFC 9457 (Problem Details) sem vazar stack traces, SQL ou valores rejeitados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SecuritySignals signals;

    public GlobalExceptionHandler(SecuritySignals signals) {
        this.signals = signals;
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InvalidImageException.class)
    ProblemDetail handleInvalidImage(InvalidImageException ex) {
        signals.record(Signal.IMAGES_REJECTED);
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LoginThrottledException.class)
    ResponseEntity<ProblemDetail> handleThrottled(LoginThrottledException ex) {
        signals.record(Signal.LOGIN_THROTTLED);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    /** Cota da conta para a ação esgotada na janela atual (denúncias, exportação...). */
    @ExceptionHandler(QuotaExceededException.class)
    ResponseEntity<ProblemDetail> handleQuotaExceeded(QuotaExceededException ex) {
        signals.record(Signal.QUOTA_EXCEEDED);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    /** Teto global de Argon2 simultâneos atingido: falha rápida, sem contar como tentativa errada. */
    @ExceptionHandler(HashingBusyException.class)
    ResponseEntity<ProblemDetail> handleHashingBusy(HashingBusyException ex) {
        signals.record(Signal.HASHING_SATURATED);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        signals.record(Signal.LOGIN_FAILED);
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** 403 com o motivo (ex.: moderador agindo sobre outra conta da equipe). */
    @ExceptionHandler(ForbiddenActionException.class)
    ProblemDetail handleForbiddenAction(ForbiddenActionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** O cliente reconhece o {@code code} e mostra o termo de consentimento. */
    @ExceptionHandler(ConsentRequiredException.class)
    ProblemDetail handleConsentRequired(ConsentRequiredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setProperty("code", "TREATMENT_CONSENT_REQUIRED");
        return problem;
    }

    @ExceptionHandler({GrowerNotAllowedException.class, AccessDeniedException.class, ModeratorOnlyException.class})
    ProblemDetail handleForbidden(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    /** Corrida em índice único (ex.: dois cadastros simultâneos com o mesmo e-mail). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Não foi possível concluir a operação");
    }

    /**
     * Sem a mensagem da exceção (pode trazer dado de usuário, como o valor de uma chave duplicada no Postgres): o log
     * guarda só um código de referência, as classes da cadeia de erros e onde, no nosso código, aconteceu. O mesmo
     * código vai na resposta, para quem relatar o problema.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        String reference = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("Erro inesperado [{}]: {} em {}", reference, causeChain(ex), ownFrames(ex));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");
        problem.setProperty("reference", reference);
        return problem;
    }

    private static String causeChain(Throwable ex) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = ex; current != null && chain.length() < 500; current = current.getCause()) {
            chain.append(chain.isEmpty() ? "" : " <- ").append(current.getClass().getName());
            if (current.getCause() == current) {
                break;
            }
        }
        return chain.toString();
    }

    /** Até 5 linhas da pilha que são do próprio weediário (sem mensagens). */
    private static String ownFrames(Throwable ex) {
        return java.util.Arrays.stream(ex.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("br.com.weediario."))
                .limit(5)
                .map(frame -> frame.getClassName().substring("br.com.weediario.".length()) + "." + frame.getMethodName()
                        + ":" + frame.getLineNumber())
                .collect(java.util.stream.Collectors.joining(" > "));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido"));
    }

    record FieldViolation(String field, String message) {
    }
}
