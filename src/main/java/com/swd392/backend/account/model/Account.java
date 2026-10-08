package com.swd392.backend.account.model;

import com.swd392.backend.rating.model.PlayerRating;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class Account {
    private Long id;
    private String email;
    private String displayName;
    private String passwordHash;
    private SkillLevel signupSkill;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Builder.Default
    private List<PlayerRating> ratings = new ArrayList<>();
}
