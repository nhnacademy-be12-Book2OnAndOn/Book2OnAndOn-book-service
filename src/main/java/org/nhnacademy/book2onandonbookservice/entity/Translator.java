package org.nhnacademy.book2onandonbookservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Translator")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Translator {
    // 작가 아이디
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "translator_id")
    private Long id;

    // 작가 이름
    @Setter
    @Column(name = "translator_name", length = 50, nullable = false)
    private String authorName;

    @OneToMany(mappedBy = "Translator", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookTranslator> bookTranslators = new ArrayList<>();

}