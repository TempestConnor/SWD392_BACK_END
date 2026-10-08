package com.swd392.backend.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "RatingMode")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class RatingModeEntity {

    @Id
    @Column(name = "mode_code", length = 20)
    private String modeCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "k_factor", nullable = false)
    private int kFactor;
}
