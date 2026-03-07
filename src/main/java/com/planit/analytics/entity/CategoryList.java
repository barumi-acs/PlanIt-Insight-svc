package com.planit.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "category_list")
@Getter
public class CategoryList {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "list_id")
    private Long listId;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;
}
