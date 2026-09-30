package br.com.weediario.diary.presentation.rest;

import br.com.weediario.diary.application.usecase.ChangeDiaryProductsUseCase;
import br.com.weediario.diary.domain.model.GrowSetup;
import br.com.weediario.diary.domain.model.ProductSnapshot;
import br.com.weediario.shared.presentation.Viewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class DiaryProductsController {

    private final ChangeDiaryProductsUseCase changeProducts;

    public DiaryProductsController(ChangeDiaryProductsUseCase changeProducts) {
        this.changeProducts = changeProducts;
    }

    /** Substitui a lista inteira (lista vazia remove todos). Só o autor do diário. */
    @PutMapping("/api/v1/diaries/{diaryId}/products")
    public List<ProductSnapshot> change(@AuthenticationPrincipal Jwt principal, @PathVariable UUID diaryId,
                                        @Valid @RequestBody ProductsRequest request) {
        return changeProducts.execute(diaryId, Viewer.requireId(principal), request.productIds());
    }

    public record ProductsRequest(@NotNull @Size(max = GrowSetup.MAX_PRODUCTS) List<@NotNull UUID> productIds) {
    }
}
