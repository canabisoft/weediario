package br.com.weediario.shared.application;

import br.com.weediario.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void sizeAndPageAreClampedToSaneValues() {
        PageQuery query = new PageQuery(-3, 10_000);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(PageQuery.MAX_SIZE);
        assertThat(new PageQuery(2, 0).size()).isEqualTo(PageQuery.DEFAULT_SIZE);
    }

    @Test
    void deepPagesAreRefused() {
        assertThat(new PageQuery(PageQuery.MAX_PAGE, PageQuery.MAX_SIZE).offset()).isEqualTo(50_000);
        assertThatThrownBy(() -> new PageQuery(PageQuery.MAX_PAGE + 1, 20)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new PageQuery(Integer.MAX_VALUE, 20)).isInstanceOf(DomainException.class);
    }
}
