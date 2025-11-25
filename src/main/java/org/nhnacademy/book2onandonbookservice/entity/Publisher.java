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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Publisher")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class Publisher {
    // 출판사 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "publisher_id")
    private Long id;

    // 출판사 이름
    @Setter
    @Column(name = "publisher_name", length = 50, nullable = false)
    @Size(min = 1, max = 50)
    private String publisherName;

    @Setter
    @OneToMany(mappedBy = "publisher", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookPublisher> bookPublishers = new ArrayList<>();
}
