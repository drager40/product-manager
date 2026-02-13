package com.bugs.productmanager.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "budget", uniqueConstraints = @UniqueConstraint(columnNames = {"ym", "category", "division", "department", "team"}))
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ym", nullable = false, length = 10)
    private String ym;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 20)
    private String division;

    @Column(length = 50)
    private String department;

    @Column(length = 50)
    private String team;

    @Column(name = "monthly_amount", precision = 12, scale = 0)
    private BigDecimal monthlyAmount = BigDecimal.ZERO;

    @Column(name = "prev_remaining", precision = 12, scale = 0)
    private BigDecimal prevRemaining = BigDecimal.ZERO;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getYm() { return ym; }
    public void setYm(String ym) { this.ym = ym; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public BigDecimal getMonthlyAmount() { return monthlyAmount; }
    public void setMonthlyAmount(BigDecimal monthlyAmount) { this.monthlyAmount = monthlyAmount; }

    public BigDecimal getPrevRemaining() { return prevRemaining; }
    public void setPrevRemaining(BigDecimal prevRemaining) { this.prevRemaining = prevRemaining; }

    public BigDecimal getTotalBudget() {
        return (monthlyAmount != null ? monthlyAmount : BigDecimal.ZERO)
                .add(prevRemaining != null ? prevRemaining : BigDecimal.ZERO);
    }
}
