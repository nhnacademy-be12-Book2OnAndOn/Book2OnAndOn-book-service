package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class BookTagPK implements Serializable {
    private Long bookId;
    private Long tagId;


    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public Long getBookId() {
        return bookId;
    }

    public Long getTagId() {
        return tagId;
    }
}
