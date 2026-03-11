package com.planit.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "category_list")
@Getter
@Setter
public class CategoryList {
    
    @Id
    @Column(name = "list_id")
    private Long listId;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(columnDefinition = "TEXT")
    private String description;
}
