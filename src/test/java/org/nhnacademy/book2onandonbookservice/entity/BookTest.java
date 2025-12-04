package org.nhnacademy.book2onandonbookservice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookTest {

    @Test
    @DisplayName("좋아요 증가 테스트 : 정상적으로 1증가")
    void increaseLikeCount() {
        Book book = Book.builder()
                .title("테스트 책")
                .likeCount(0L)
                .build();
        book.increaseLikeCount();
        assertThat(book.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("좋아요 증가 테스트: likeCount가 null일 경우 0으로 초기화 후 증가해야됨")
    void increaseLikeCount_whenNull() {
        Book book = new Book();
        book.increaseLikeCount(); //내부에서 null -> 0 -> 1로 되나?
        assertThat(book.getLikeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("좋아요 감소 테스트: 0이하일때는 감소X 0유지")
    void decreaseLikeCount() {
        Book book = Book.builder()
                .likeCount(0L)
                .build();

        book.decreaseLikeCount();
        assertThat(book.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("평점 수정 테스트")
    void updateRating() {
        Book book = Book.builder()
                .rating(3.5)
                .build();
        book.updateRating(4.5);
        assertThat(book.getRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("출판사 추가 및 확인")
    void addAndHasPublisher() {
        Book book = Book.builder()
                .title("테스트 책")
                .build();

        Publisher publisher = mock(Publisher.class);

        book.addPublisher(publisher);

        boolean hasPublisher = book.hasPublisher(publisher);
        assertThat(hasPublisher).isTrue();

        assertThat(book.getBookPublishers()).hasSize(1);
        assertThat(book.getBookPublishers().iterator().next().getPublisher()).isEqualTo(publisher);
    }

    @Test
    @DisplayName("없는 출판사 확인 테스트")
    void hasPublisher_false() {
        Book book = Book.builder().build();
        Publisher pA = mock(Publisher.class);
        Publisher pB = mock(Publisher.class);

        book.addPublisher(pA);

        boolean result = book.hasPublisher(pB);
        assertThat(result).isFalse();
    }
}