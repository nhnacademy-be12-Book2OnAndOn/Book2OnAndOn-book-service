package org.nhnacademy.book2onandonbookservice.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookContributorDto {
    private Long id;    // book_contributor_id
    private String roleType;    // role_type
    private Long contributorId; // contributor_id
    private String contributorName; //contributor_name
}
