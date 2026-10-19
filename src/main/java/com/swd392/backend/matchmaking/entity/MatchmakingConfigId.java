package com.swd392.backend.matchmaking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
@EqualsAndHashCode
public class MatchmakingConfigId implements Serializable {

    @Column(name = "mode_code", nullable = false, length = 20)
    private String modeCode;

    @Column(name = "variant_code", nullable = false, length = 24)
    private String variantCode;
}
