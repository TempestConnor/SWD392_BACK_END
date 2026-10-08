package com.swd392.backend.rating.entity;

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
public class PlayerRatingId implements Serializable {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "mode_code", nullable = false, length = 20)
    private String modeCode;
}
