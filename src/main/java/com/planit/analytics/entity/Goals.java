package com.planit.analytics.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "goals")
@Getter
public class Goals {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "goals_id")
    private Long goalsId;
    
    @Column(name = "list_id")
    private Long listId;
    
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;
}
