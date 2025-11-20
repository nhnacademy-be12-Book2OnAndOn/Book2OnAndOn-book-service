package org.nhnacademy.book2onandonbookservice.dto.common;

import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ContributorDto {
    private Long id;    // contributor_id
    private String name;    // contributor_name
    private String description; // contributor_description
    private Date birthDate; // birth_year
}
