package org.nhnacademy.book2onandonbookservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.nhnacademy.book2onandonbookservice.domain.BookStatus;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.nhnacademy.book2onandonbookservice.entity.Contributor;
import org.nhnacademy.book2onandonbookservice.entity.Publisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BatchInsertRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BatchInsertRepository batchInsertRepository;

    @Test
    @DisplayName("성공: 도서 목록 일괄 저장 (saveAllBooks)")
    void saveAllBooks_Success() throws SQLException {
        Book book = mock(Book.class);
        when(book.getTitle()).thenReturn("Title");
        when(book.getIsbn()).thenReturn("12345");
        when(book.getPublishDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(book.getPriceStandard()).thenReturn(10000L);
        when(book.getPriceSales()).thenReturn(9000L);
        when(book.getIsWrapped()).thenReturn(true);
        when(book.getStockCount()).thenReturn(10);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);
        when(book.getDescription()).thenReturn("Desc");
        when(book.getChapter()).thenReturn("Chap");
        when(book.getVolume()).thenReturn("Vol");
        when(book.getLikeCount()).thenReturn(0L);

        List<Book> books = List.of(book);

        batchInsertRepository.saveAllBooks(books);

        ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), captor.capture());

        BatchPreparedStatementSetter setter = captor.getValue();
        PreparedStatement ps = mock(PreparedStatement.class);
        setter.setValues(ps, 0);

        verify(ps).setString(1, "Title");
        verify(ps).setString(2, "12345");
        verify(ps).setDate(3, Date.valueOf(LocalDate.of(2024, 1, 1)));
        verify(ps).setLong(4, 10000L);
        verify(ps).setLong(5, 9000L);
        verify(ps).setBoolean(6, true);
        verify(ps).setInt(7, 10);
        verify(ps).setString(8, "ON_SALE");
        verify(ps).setString(9, "Desc");
        verify(ps).setString(10, "Chap");
        verify(ps).setString(11, "Vol");
        verify(ps).setLong(12, 0L);
        assertThat(setter.getBatchSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공: 판매가가 null일 경우 0으로 저장 (saveAllBooks)")
    void saveAllBooks_NullPriceSales() throws SQLException {
        Book book = mock(Book.class);
        when(book.getPublishDate()).thenReturn(LocalDate.now());
        when(book.getPriceSales()).thenReturn(null);
        when(book.getStatus()).thenReturn(BookStatus.ON_SALE);

        List<Book> books = List.of(book);

        batchInsertRepository.saveAllBooks(books);

        ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), captor.capture());

        BatchPreparedStatementSetter setter = captor.getValue();
        PreparedStatement ps = mock(PreparedStatement.class);
        setter.setValues(ps, 0);

        verify(ps).setLong(5, 0L);
    }

    @Test
    @DisplayName("성공: 도서 이미지 일괄 저장 (saveBookImages)")
    void saveBookImages_Success() throws SQLException {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);

        BookImage image = mock(BookImage.class);
        when(image.getBook()).thenReturn(book);
        when(image.getImagePath()).thenReturn("path/to/img");
        when(image.isThumbnail()).thenReturn(true);

        List<BookImage> images = List.of(image);

        batchInsertRepository.saveBookImages(images);

        ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), captor.capture());

        BatchPreparedStatementSetter setter = captor.getValue();
        PreparedStatement ps = mock(PreparedStatement.class);
        setter.setValues(ps, 0);

        verify(ps).setLong(1, 1L);
        verify(ps).setString(2, "path/to/img");
        verify(ps).setBoolean(3, true);
        assertThat(setter.getBatchSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공: 이미지 리스트가 비어있으면 저장 로직 건너뜀")
    void saveBookImages_EmptyList() {
        batchInsertRepository.saveBookImages(Collections.emptyList());
        verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("성공: 도서 연관관계(작가, 출판사) 저장")
    void saveBookRelations_Success() throws SQLException {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);

        Contributor contributor = mock(Contributor.class);
        when(contributor.getId()).thenReturn(10L);
        BookContributor bc = mock(BookContributor.class);
        when(bc.getBook()).thenReturn(book);
        when(bc.getContributor()).thenReturn(contributor);
        when(bc.getRoleType()).thenReturn("Author");

        Publisher publisher = mock(Publisher.class);
        when(publisher.getId()).thenReturn(20L);
        BookPublisher bp = mock(BookPublisher.class);
        when(bp.getBook()).thenReturn(book);
        when(bp.getPublisher()).thenReturn(publisher);

        batchInsertRepository.saveBookRelations(List.of(bc), List.of(bp));

        ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate, times(2)).batchUpdate(any(String.class), captor.capture());

        List<BatchPreparedStatementSetter> setters = captor.getAllValues();

        BatchPreparedStatementSetter contributorSetter = setters.get(0);
        PreparedStatement ps1 = mock(PreparedStatement.class);
        contributorSetter.setValues(ps1, 0);
        verify(ps1).setLong(1, 1L);
        verify(ps1).setLong(2, 10L);
        verify(ps1).setString(3, "Author");
        assertThat(contributorSetter.getBatchSize()).isEqualTo(1);

        BatchPreparedStatementSetter publisherSetter = setters.get(1);
        PreparedStatement ps2 = mock(PreparedStatement.class);
        publisherSetter.setValues(ps2, 0);
        verify(ps2).setLong(1, 1L);
        verify(ps2).setLong(2, 20L);
        assertThat(publisherSetter.getBatchSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공: 작가 정보만 있을 경우 작가 테이블만 INSERT")
    void saveBookRelations_OnlyContributors() {
        // Mock 객체만 생성하고 내부 메서드 스텁(when)은 제거함 (UnnecessaryStubbingException 방지)
        BookContributor bc = mock(BookContributor.class);

        batchInsertRepository.saveBookRelations(List.of(bc), Collections.emptyList());

        verify(jdbcTemplate, times(1)).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("성공: 출판사 정보만 있을 경우 출판사 테이블만 INSERT")
    void saveBookRelations_OnlyPublishers() {
        // Mock 객체만 생성하고 내부 메서드 스텁(when)은 제거함
        BookPublisher bp = mock(BookPublisher.class);

        batchInsertRepository.saveBookRelations(Collections.emptyList(), List.of(bp));

        verify(jdbcTemplate, times(1)).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("성공: 작가/출판사 모두 비어있으면 아무것도 안 함")
    void saveBookRelations_BothEmpty() {
        batchInsertRepository.saveBookRelations(Collections.emptyList(), Collections.emptyList());
        verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("실패: 도서 저장 중 DB 예외 발생")
    void saveAllBooks_Fail_DbException() {
        List<Book> books = List.of(mock(Book.class));
        doThrow(new DataAccessException("DB Error") {})
                .when(jdbcTemplate).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));

        assertThatThrownBy(() -> batchInsertRepository.saveAllBooks(books))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("실패: 이미지 저장 중 DB 예외 발생")
    void saveBookImages_Fail_DbException() {
        List<BookImage> images = List.of(mock(BookImage.class));
        doThrow(new DataAccessException("DB Error") {})
                .when(jdbcTemplate).batchUpdate(any(String.class), any(BatchPreparedStatementSetter.class));

        assertThatThrownBy(() -> batchInsertRepository.saveBookImages(images))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("실패: PreparedStatement 값 설정 중 SQL 예외 발생")
    void setValues_SQLException() throws SQLException {
        Book book = mock(Book.class);
        List<Book> books = List.of(book);

        batchInsertRepository.saveAllBooks(books);

        ArgumentCaptor<BatchPreparedStatementSetter> captor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(any(String.class), captor.capture());

        BatchPreparedStatementSetter setter = captor.getValue();
        PreparedStatement ps = mock(PreparedStatement.class);

        doThrow(new SQLException()).when(ps).setString(anyInt(), any());

        assertThatThrownBy(() -> setter.setValues(ps, 0))
                .isInstanceOf(SQLException.class);
    }
}