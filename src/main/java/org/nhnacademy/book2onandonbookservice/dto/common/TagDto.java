package org.nhnacademy.book2onandonbookservice.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagDto {
    private Long id;    // tag_id
    private String name;    // tag_name
}
