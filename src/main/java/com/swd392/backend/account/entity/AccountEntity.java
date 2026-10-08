package com.swd392.backend.account.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Nationalized
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Nationalized
    @Column(name = "display_name", nullable = false, length = 40, updatable = false)
    private String displayName;

    @Nationalized
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "signup_skill", nullable = false, length = 12, updatable = false)
    private String signupSkill;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "AccountRole", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "role_code", length = 20)
    private Set<String> roles = new HashSet<>();
}
