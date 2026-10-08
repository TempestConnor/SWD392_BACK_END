package com.swd392.backend.invitation.service;

import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.invitation.entity.InvitationEntity;
import com.swd392.backend.invitation.mapper.InvitationEntityMapper;
import com.swd392.backend.invitation.model.CreatedInvitation;
import com.swd392.backend.invitation.model.HostColor;
import com.swd392.backend.invitation.model.Invitation;
import com.swd392.backend.invitation.model.InvitationRequest;
import com.swd392.backend.invitation.model.InvitationStatus;
import com.swd392.backend.invitation.repository.InvitationRepository;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final Duration INVITATION_LIFETIME = Duration.ofMinutes(15);
    private static final String STANDARD_VARIANT = "STANDARD";
    private static final String CLASSICAL_MODE = "CLASSICAL";
    private static final int DEFAULT_INITIAL_MINUTES = 30;
    private static final int DEFAULT_INCREMENT_SECONDS = 10;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int RATED_INITIAL_SECONDS = DEFAULT_INITIAL_MINUTES * SECONDS_PER_MINUTE;
    private static final int RATED_INCREMENT_SECONDS = DEFAULT_INCREMENT_SECONDS;
    private static final int TOKEN_BYTES = 32;
    private static final String TOKEN_HASH_ALGORITHM = "SHA-256";

    private final InvitationRepository invitationRepository;
    private final InvitationEntityMapper invitationEntityMapper;
    private final OnlineGameService onlineGameService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public CreatedInvitation create(Long hostAccountId, InvitationRequest request) {
        Instant now = now();
        Invitation invitation = newInvitation(hostAccountId, request, now);
        String token = newToken();
        InvitationEntity entity = invitationEntityMapper.toEntity(invitation, hashToken(token));
        InvitationEntity saved = invitationRepository.saveAndFlush(entity);
        log.info(
                "[INVITATION] - ACTION: create: invitationId: {}, hostAccountId: {}",
                saved.getId(),
                saved.getHostAccountId());

        CreatedInvitation created = CreatedInvitation.builder()
                .withInvitation(toInvitation(saved, now))
                .withToken(token)
                .build();

        return created;
    }

    @Override
    public Invitation getInvitation(Long invitationId, Long hostAccountId) {
        InvitationEntity entity = invitationRepository
                .findById(invitationId)
                .filter(found -> found.getHostAccountId().equals(hostAccountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.INVITATION_NOT_FOUND));

        return toInvitation(entity, now());
    }

    @Override
    public Invitation cancel(Long invitationId, Long hostAccountId) {
        InvitationEntity entity = invitationRepository
                .findForUpdateById(invitationId)
                .filter(found -> found.getHostAccountId().equals(hostAccountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.INVITATION_NOT_FOUND));
        Instant now = now();
        if (entity.getCancelledAt() != null) {
            return toInvitation(entity, now);
        }
        if (entity.getClaimedAt() != null) {
            throw new AppException(AppErrorMessage.INVITATION_ALREADY_CLAIMED);
        }
        if (isExpired(entity, now)) {
            throw new AppException(AppErrorMessage.INVITATION_EXPIRED);
        }

        entity.setCancelledAt(now);
        InvitationEntity saved = invitationRepository.save(entity);
        log.info("[INVITATION] - ACTION: cancel: invitationId: {}", saved.getId());

        return toInvitation(saved, now);
    }

    @Override
    public OnlineGame claim(String token, Long joinerAccountId) {
        InvitationEntity entity = invitationRepository
                .findForUpdateByTokenHash(hashToken(token))
                .orElseThrow(() -> new AppException(AppErrorMessage.INVITATION_NOT_FOUND));
        Instant now = now();
        if (entity.getHostAccountId().equals(joinerAccountId)) {
            throw new AppException(AppErrorMessage.CANNOT_JOIN_OWN_INVITATION);
        }
        if (entity.getClaimedAt() != null) {
            return claimedGame(entity, joinerAccountId);
        }
        if (entity.getCancelledAt() != null) {
            throw new AppException(AppErrorMessage.INVITATION_CANCELLED);
        }
        if (isExpired(entity, now)) {
            throw new AppException(AppErrorMessage.INVITATION_EXPIRED);
        }

        OnlineGame pendingGame = onlineGameService.createPendingGame(newGame(entity, joinerAccountId, now));
        entity.setJoinerAccountId(joinerAccountId);
        entity.setGameId(pendingGame.getId());
        entity.setClaimedAt(now);
        invitationRepository.saveAndFlush(entity);
        log.info(
                "[INVITATION] - ACTION: claim: invitationId: {}, joinerAccountId: {}, gameId: {}",
                entity.getId(),
                joinerAccountId,
                pendingGame.getId());

        return onlineGameService.getGame(pendingGame.getId(), joinerAccountId);
    }

    private OnlineGame claimedGame(InvitationEntity entity, Long joinerAccountId) {
        boolean sameJoiner = entity.getJoinerAccountId().equals(joinerAccountId);
        if (!sameJoiner) {
            throw new AppException(AppErrorMessage.INVITATION_FULL);
        }

        return onlineGameService.getGame(entity.getGameId(), joinerAccountId);
    }

    private Invitation newInvitation(Long hostAccountId, InvitationRequest request, Instant now) {
        boolean clockDisabled = Boolean.FALSE.equals(request.getClockEnabled());
        boolean hasTiming = request.getInitialMinutes() != null || request.getIncrementSeconds() != null;
        if (clockDisabled && hasTiming) {
            throw new AppException(AppErrorMessage.DISABLED_CLOCK_HAS_TIMING);
        }
        Integer initialSeconds = clockDisabled
                ? null
                : valueOrDefault(request.getInitialMinutes(), DEFAULT_INITIAL_MINUTES) * SECONDS_PER_MINUTE;
        Integer incrementSeconds =
                clockDisabled ? null : valueOrDefault(request.getIncrementSeconds(), DEFAULT_INCREMENT_SECONDS);

        Invitation invitation = Invitation.builder()
                .withHostAccountId(hostAccountId)
                .withHostColor(request.getHostColor())
                .withVariantCode(STANDARD_VARIANT)
                .withModeCode(CLASSICAL_MODE)
                .withClockEnabled(!clockDisabled)
                .withInitialSeconds(initialSeconds)
                .withIncrementSeconds(incrementSeconds)
                .withCreatedAt(now)
                .withExpiresAt(now.plus(INVITATION_LIFETIME))
                .build();

        return invitation;
    }

    private OnlineGame newGame(InvitationEntity entity, Long joinerAccountId, Instant now) {
        boolean hostIsWhite = resolveHostIsWhite(HostColor.valueOf(entity.getHostColor()));
        OnlineGame game = OnlineGame.builder()
                .withSource(GameSource.INVITE)
                .withWhiteAccountId(hostIsWhite ? entity.getHostAccountId() : joinerAccountId)
                .withBlackAccountId(hostIsWhite ? joinerAccountId : entity.getHostAccountId())
                .withVariantCode(entity.getVariantCode())
                .withModeCode(entity.getModeCode())
                .withRated(isRated(entity.isClockEnabled(), entity.getInitialSeconds(), entity.getIncrementSeconds()))
                .withClockEnabled(entity.isClockEnabled())
                .withInitialSeconds(entity.getInitialSeconds())
                .withIncrementSeconds(entity.getIncrementSeconds())
                .withCreatedAt(now)
                .build();

        return game;
    }

    private boolean resolveHostIsWhite(HostColor hostColor) {
        return switch (hostColor) {
            case WHITE -> true;
            case BLACK -> false;
            case RANDOM -> ThreadLocalRandom.current().nextBoolean();
        };
    }

    private Invitation toInvitation(InvitationEntity entity, Instant now) {
        Invitation invitation = invitationEntityMapper.toModel(entity);
        invitation.setRated(isRated(entity.isClockEnabled(), entity.getInitialSeconds(), entity.getIncrementSeconds()));
        invitation.setStatus(statusOf(entity, now));

        return invitation;
    }

    private InvitationStatus statusOf(InvitationEntity entity, Instant now) {
        if (entity.getClaimedAt() != null) {
            return InvitationStatus.CLAIMED;
        }
        if (entity.getCancelledAt() != null) {
            return InvitationStatus.CANCELLED;
        }
        if (isExpired(entity, now)) {
            return InvitationStatus.EXPIRED;
        }

        return InvitationStatus.OPEN;
    }

    private boolean isExpired(InvitationEntity entity, Instant now) {
        return !now.isBefore(entity.getExpiresAt());
    }

    private boolean isRated(boolean clockEnabled, Integer initialSeconds, Integer incrementSeconds) {
        boolean defaultClock = Integer.valueOf(RATED_INITIAL_SECONDS).equals(initialSeconds)
                && Integer.valueOf(RATED_INCREMENT_SECONDS).equals(incrementSeconds);

        return clockEnabled && defaultClock;
    }

    private String newToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private byte[] hashToken(String token) {
        try {
            return MessageDigest.getInstance(TOKEN_HASH_ALGORITHM).digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(TOKEN_HASH_ALGORITHM + " is unavailable", exception);
        }
    }

    // Columns are datetime2(3); truncating keeps expires_at exactly created_at + 15 minutes as the schema requires.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
