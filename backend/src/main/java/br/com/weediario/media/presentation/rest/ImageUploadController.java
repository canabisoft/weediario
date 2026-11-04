package br.com.weediario.media.presentation.rest;

import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.media.application.usecase.UploadImageUseCase;
import br.com.weediario.media.application.usecase.UploadImageUseCase.UploadedImage;
import br.com.weediario.shared.presentation.Viewer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Upload de uma foto por requisição (o app mobile envia em paralelo e mostra progresso individual).
 * Devolve o id para ser referenciado ao publicar o update do diário.
 * <p>
 * Só {@link #MAX_IN_MEMORY} fotos ficam na memória ao mesmo tempo (cada uma tem até 13 MB); as outras esperam a vez por
 * até {@link #WAIT_SECONDS} s e, se não der, recebem 429. O nginx também limita os envios simultâneos.
 */
@RestController
@RequestMapping("/api/v1/media/images")
public class ImageUploadController {

    static final int MAX_IN_MEMORY = 4;
    static final int WAIT_SECONDS = 30;

    private final Semaphore slots = new Semaphore(MAX_IN_MEMORY, true);
    private final UploadImageUseCase uploadImage;

    public ImageUploadController(UploadImageUseCase uploadImage) {
        this.uploadImage = uploadImage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImage upload(@AuthenticationPrincipal Jwt principal, @RequestPart("file") MultipartFile file) {
        var viewer = Viewer.requireId(principal);
        acquireSlot();
        try {
            return uploadImage.execute(viewer, file.getBytes());
        } catch (IOException e) {
            throw new InvalidImageException("Falha ao receber o arquivo");
        } finally {
            slots.release();
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new UploadsBusyException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UploadsBusyException();
        }
    }

    @ExceptionHandler(UploadsBusyException.class)
    ResponseEntity<ProblemDetail> handleBusy() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas fotos sendo enviadas agora. Tente esta de novo em alguns segundos."));
    }

    static final class UploadsBusyException extends RuntimeException {
        UploadsBusyException() {
            super(null, null, false, false);
        }
    }
}
