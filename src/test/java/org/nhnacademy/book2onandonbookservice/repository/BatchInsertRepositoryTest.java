package org.nhnacademy.book2onandonbookservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BatchInsertRepositoryTest {
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement preparedStatement;

    @InjectMocks
    private BatchInsertRepository batchInsertRepository;

    @Test
    @DisplayName("saveAllBooks: 도서 리스트 Batch Insert 성공 테스트")
    void saveAllBooks_success() throws SQLException {
        Book book = Book.builder()
                .title("테스트 책")
                .isbn("1234567890123")
                .publishDate(LocalDate.of(2023, 1, 1))
                .priceStandard(15000L)
                .priceSales(13500L)
                .isWrapped(true)
                .stockCount(100)
                .status(BookStatus.ON_SALE)
                .description("설명")
                .chapter("목차는 대체 언제 주시나요..")
                .volume("1권")
                .likeCount(10L)
                .build();

        List<Book> books = List.of(book);

        batchInsertRepository.saveAllBooks(books);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(
                BatchPreparedStatementSetter.class);

        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), pssCaptor.capture());

        String actualSql = sqlCaptor.getValue();
        assertThat(actualSql).contains("INSERT INTO book");

        BatchPreparedStatementSetter pss = pssCaptor.getValue();

        assertThat(pss.getBatchSize()).isEqualTo(1);

        pss.setValues(preparedStatement, 0);

        verify(preparedStatement).setString(1, book.getTitle());
        verify(preparedStatement).setString(1, book.getTitle());
        verify(preparedStatement).setString(2, book.getIsbn());
        verify(preparedStatement).setDate(3, Date.valueOf(book.getPublishDate()));
        verify(preparedStatement).setLong(4, book.getPriceStandard());
        verify(preparedStatement).setLong(5, book.getPriceSales() != null ? book.getPriceSales() : 0L);
        verify(preparedStatement).setBoolean(6, book.getIsWrapped());
        verify(preparedStatement).setInt(7, book.getStockCount());
        verify(preparedStatement).setString(8, book.getStatus().name());
        verify(preparedStatement).setString(9, book.getDescription());
        verify(preparedStatement).setString(10, book.getChapter());
        verify(preparedStatement).setString(11, book.getVolume());
        verify(preparedStatement).setLong(12, book.getLikeCount());
    }

    @Test
    @DisplayName("saveBooImages: 이미지 리스트 Batch Insert 성공 테스트")
    void saveBookImages_success() throws SQLException {
        Book mockBook = mock(Book.class);
        when(mockBook.getId()).thenReturn(1L);

        BookImage image = mock(BookImage.class);
        when(image.getBook()).thenReturn(mockBook);
        when(image.getImagePath()).thenReturn("image.jpg");

        List<BookImage> images = List.of(image);

        batchInsertRepository.saveBookImages(images);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> passCaptor = ArgumentCaptor.forClass(
                BatchPreparedStatementSetter.class);

        verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), passCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("INSERT INTO book_image");

        BatchPreparedStatementSetter pss = passCaptor.getValue();
        assertThat(pss.getBatchSize()).isEqualTo(1);

        pss.setValues(preparedStatement, 0);

        verify(preparedStatement).setLong(1, image.getBook().getId());
        verify(preparedStatement).setString(2, image.getImagePath());
    }

    @Test
    @DisplayName("saveBookImages: 리스트가 비어있으면 아무것도 실행하지 않음")
    void saveBookImages_empty() {
        batchInsertRepository.saveBookImages(Collections.emptyList());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }


    @Test
    @DisplayName("saveBookRelations: 작가 및 출판사 연관관계 저장 테스트")
    void saveBookRelations_success() throws SQLException {

        Book mockBook = mock(Book.class);
        when(mockBook.getId()).thenReturn(10L);

        Contributor mockContributor = mock(Contributor.class);
        when(mockContributor.getId()).thenReturn(20L);

        BookContributor bc = mock(BookContributor.class);
        when(bc.getBook()).thenReturn(mockBook);
        when(bc.getContributor()).thenReturn(mockContributor);
        when(bc.getRoleType()).thenReturn("AUTHOR");

        Publisher mockPublisher = mock(Publisher.class);
        when(mockPublisher.getId()).thenReturn(30L);

        BookPublisher bp = mock(BookPublisher.class);
        when(bp.getBook()).thenReturn(mockBook);
        when(bp.getPublisher()).thenReturn(mockPublisher);

        batchInsertRepository.saveBookRelations(List.of(bc), List.of(bp));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(
                BatchPreparedStatementSetter.class);

        verify(jdbcTemplate, times(2)).batchUpdate(sqlCaptor.capture(), pssCaptor.capture());

        List<String> sqls = sqlCaptor.getAllValues();
        List<BatchPreparedStatementSetter> setters = pssCaptor.getAllValues();

        assertThat(sqls.get(0)).contains("INSERT IGNORE INTO book_contributor");
        setters.get(0).setValues(preparedStatement, 0);
        verify(preparedStatement).setLong(1, 10L); // bookId
        verify(preparedStatement).setLong(2, 20L); // contributorId
        verify(preparedStatement).setString(3, "AUTHOR"); // roleType

        // 두 번째 호출 검증을 위해 PreparedStatement Mock 리셋 (verify count 혼선 방지)
        reset(preparedStatement);

        // 두 번째: Publisher 저장 확인
        assertThat(sqls.get(1)).contains("INSERT IGNORE INTO book_publisher");
        setters.get(1).setValues(preparedStatement, 0);
        verify(preparedStatement).setLong(1, 10L); // bookId
        verify(preparedStatement).setLong(2, 30L); // publisherId
    }

    @Test
    @DisplayName("saveBookRelations: 리스트가 비어있으면 해당 쿼리는 실행되지 않음")
    void saveBookRelations_empty() {
        List<BookContributor> contributors = Collections.emptyList();
        List<BookPublisher> publishers = Collections.emptyList();

        batchInsertRepository.saveBookRelations(contributors, publishers);

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }
}