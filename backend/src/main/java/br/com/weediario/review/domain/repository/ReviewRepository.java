package br.com.weediario.review.domain.repository;

import br.com.weediario.review.domain.model.Review;

import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    boolean existsForDiary(UUID diaryId);
}
