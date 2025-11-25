package org.nhnacademy.book2onandonbookservice.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookPublisherDto {
    private Long id;    // book_publisher_id
    private Long publisherId;   // publisher_id
    private String publisherName;   // publisher_name
}
