package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Contributor")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Contributor {
    // 기여자 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contributor_id")
    private Long id;

    // 기여자 이름
    @Setter
    @Column(name = "contributor_name", length = 50, nullable = false)
    @Size(min = 1, max = 50)
    private String contributorName;

    // 부가 정보
    @Setter
    @Column(name = "description")
    private String description;

    // 출생 연도
    @Setter
    @Column(name = "birth_year")
    private Date birthYear;

    @Setter
    @OneToMany(mappedBy = "contributor", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookContributor> bookContributors = new ArrayList<>();

}
