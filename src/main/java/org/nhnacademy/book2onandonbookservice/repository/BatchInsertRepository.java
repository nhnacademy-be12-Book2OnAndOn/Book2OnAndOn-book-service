package org.nhnacademy.book2onandonbookservice.repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nhnacademy.book2onandonbookservice.entity.Book;
import org.nhnacademy.book2onandonbookservice.entity.BookContributor;
import org.nhnacademy.book2onandonbookservice.entity.BookImage;
import org.nhnacademy.book2onandonbookservice.entity.BookPublisher;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class BatchInsertRepository {

    private final JdbcTemplate jdbcTemplate;


    public void saveAllBooks(List<Book> books) {

        String sql =
                "INSERT INTO book (book_title, ISBN, book_publish_date, price_standard, price_sales, is_wrapped, stock_count, book_status, book_description, book_chapter, book_volume, like_count) "
                        +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Book book = books.get(i);
                ps.setString(1, book.getTitle());
                ps.setString(2, book.getIsbn());
                ps.setDate(3, Date.valueOf(book.getPublishDate()));
                ps.setLong(4, book.getPriceStandard());
                ps.setLong(5, book.getPriceSales() != null ? book.getPriceSales() : 0L);
                ps.setBoolean(6, book.getIsWrapped());
                ps.setInt(7, book.getStockCount());
                ps.setString(8, book.getStatus().name());
                ps.setString(9, book.getDescription());
                ps.setString(10, book.getChapter());
                ps.setString(11, book.getVolume());
                ps.setLong(12, book.getLikeCount());
            }

            @Override
            public int getBatchSize() {
                return books.size();
            }
        });
    }

    public void saveBookImages(List<BookImage> images) {
        if (images.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO book_image (book_id, book_image_path) VALUES (?,?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                BookImage image = images.get(i);
                ps.setLong(1, image.getBook().getId());
                ps.setString(2, image.getImagePath());
            }

            @Override
            public int getBatchSize() {
                return images.size();
            }
        });
    }

    public void saveBookRelations(List<BookContributor> contributors, List<BookPublisher> publishers) {
        if (!contributors.isEmpty()) {
            String sql = "INSERT IGNORE INTO book_contributor (book_id, contributor_id, role_type) VALUES (?, ?, ?)";
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    BookContributor bc = contributors.get(i);
                    ps.setLong(1, bc.getBook().getId());
                    ps.setLong(2, bc.getContributor().getId());
                    ps.setString(3, bc.getRoleType());
                }

                @Override
                public int getBatchSize() {
                    return contributors.size();
                }
            });
        }

        if (!publishers.isEmpty()) {
            String sql = "INSERT IGNORE INTO book_publisher (book_id, publisher_id) VALUES (?, ?)";
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    BookPublisher bp = publishers.get(i);
                    ps.setLong(1, bp.getBook().getId());
                    ps.setLong(2, bp.getPublisher().getId());
                }

                @Override
                public int getBatchSize() {
                    return publishers.size();
                }
            });
        }
    }
}