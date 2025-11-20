package org.nhnacademy.book2onandonbookservice.dto.common;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BookPublisherDto {
    private Long id;    // book_publisher_id
    private Long publisherId;   // publisher_id
    private String publisherName;   // publisher_name
}
