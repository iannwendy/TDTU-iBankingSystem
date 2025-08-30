package com.ibanking.tuition.user;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    // Default constructor
    public Customer() {}

    // Getters
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public BigDecimal getBalance() { return balance; }
    public Long getVersion() { return version; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void setVersion(Long version) { this.version = version; }
}


