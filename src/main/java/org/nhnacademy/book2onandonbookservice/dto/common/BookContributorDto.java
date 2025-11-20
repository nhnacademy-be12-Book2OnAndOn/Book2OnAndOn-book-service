package org.nhnacademy.book2onandonbookservice.dto.common;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BookContributorDto {
    private Long id;    // book_contributor_id
    private String roleType;    // role_type
    private Long contributorId; // contributor_id
    private String contributorName; //contributor_name
}
