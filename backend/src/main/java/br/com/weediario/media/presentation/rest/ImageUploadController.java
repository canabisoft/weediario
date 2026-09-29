package br.com.weediario.media.presentation.rest;

import br.com.weediario.media.application.exception.InvalidImageException;
import br.com.weediario.media.application.usecase.UploadImageUseCase;
import br.com.weediario.media.application.usecase.UploadImageUseCase.UploadedImage;
import br.com.weediario.shared.presentation.Viewer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Upload de uma foto por requisição (o app mobile envia em paralelo e mostra progresso individual).
 * Devolve o id para ser referenciado ao publicar o update do diário.
 */
@RestController
@RequestMapping("/api/v1/media/images")
public class ImageUploadController {

    private final UploadImageUseCase uploadImage;

    public ImageUploadController(UploadImageUseCase uploadImage) {
        this.uploadImage = uploadImage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedImage upload(@AuthenticationPrincipal Jwt principal, @RequestPart("file") MultipartFile file) {
        try {
            return uploadImage.execute(Viewer.requireId(principal), file.getBytes());
        } catch (IOException e) {
            throw new InvalidImageException("Falha ao receber o arquivo");
        }
    }
}
