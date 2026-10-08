package com.swd392.backend.invitation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.invitation.mapper.InvitationMapperImpl;
import com.swd392.backend.invitation.model.CreatedInvitation;
import com.swd392.backend.invitation.model.HostColor;
import com.swd392.backend.invitation.model.Invitation;
import com.swd392.backend.invitation.model.InvitationRequest;
import com.swd392.backend.invitation.model.InvitationStatus;
import com.swd392.backend.invitation.service.InvitationService;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapperImpl;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvitationController.class)
@Import({SecurityConfig.class, InvitationMapperImpl.class, OnlineGameMapperImpl.class})
class InvitationControllerTest {

    private static final String INVITATIONS_URL = "/api/invitations";
    private static final String CLAIM_URL = "/api/invitations/claim";
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
    private static final Instant CREATED_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvitationService invitationService;

    @Test
    void create_returns_201_with_token_and_rated_default_clock_ok() throws Exception {
        // given
        var body = """
                {"hostColor":"BLACK"}
                """;
        when(invitationService.create(eq(1L), any())).thenReturn(createdInvitation());
        var captor = ArgumentCaptor.forClass(InvitationRequest.class);

        // when
        mockMvc.perform(post(INVITATIONS_URL)
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(TOKEN))
                .andExpect(jsonPath("$.invitation.id").value(10))
                .andExpect(jsonPath("$.invitation.status").value("OPEN"))
                .andExpect(jsonPath("$.invitation.hostColor").value("BLACK"))
                .andExpect(jsonPath("$.invitation.rated").value(true))
                .andExpect(jsonPath("$.invitation.mode").value("CLASSICAL"))
                .andExpect(jsonPath("$.invitation.variant").value("STANDARD"))
                .andExpect(jsonPath("$.invitation.clock.enabled").value(true))
                .andExpect(jsonPath("$.invitation.clock.initialMinutes").value(30))
                .andExpect(jsonPath("$.invitation.clock.incrementSeconds").value(10))
                .andExpect(jsonPath("$.invitation.expiresAt").value("2026-10-08T10:15:00Z"))
                .andExpect(jsonPath("$.invitation.gameId").doesNotExist());

        // then
        verify(invitationService).create(eq(1L), captor.capture());
        var request = captor.getValue();
        assertAll(
                () -> assertThat(request.getHostColor()).isEqualTo(HostColor.BLACK),
                () -> assertThat(request.getClockEnabled()).isNull(),
                () -> assertThat(request.getInitialMinutes()).isNull());
    }

    @Test
    void create_invalid_fields_returns_400_problem_ko() throws Exception {
        // given
        var body =
                """
                {"hostColor":"GREEN","initialMinutes":4,"incrementSeconds":61}
                """;

        // when / then
        mockMvc.perform(post(INVITATIONS_URL)
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field")
                        .value(containsInAnyOrder("hostColor", "initialMinutes", "incrementSeconds")));
        verify(invitationService, never()).create(anyLong(), any());
    }

    @Test
    void create_without_token_returns_401_ko() throws Exception {
        // given
        var body = """
                {"hostColor":"WHITE"}
                """;

        // when / then
        mockMvc.perform(post(INVITATIONS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        verify(invitationService, never()).create(anyLong(), any());
    }

    @Test
    void claim_returns_pending_game_from_joiner_perspective_ok() throws Exception {
        // given
        var body = """
                {"token":"%s"}
                """.formatted(TOKEN);
        when(invitationService.claim(TOKEN, 2L)).thenReturn(pendingGame());

        // when / then
        mockMvc.perform(post(CLAIM_URL)
                        .with(jwt().jwt(token -> token.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.source").value("INVITE"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.viewerColor").value("WHITE"))
                .andExpect(jsonPath("$.white.accountId").value(2))
                .andExpect(jsonPath("$.white.displayName").value("Joiner"))
                .andExpect(jsonPath("$.white.ready").value(false))
                .andExpect(jsonPath("$.black.accountId").value(1))
                .andExpect(jsonPath("$.clock.initialMinutes").value(30))
                .andExpect(jsonPath("$.readyDeadlineAt").value("2026-10-08T10:03:00Z"))
                .andExpect(jsonPath("$.whiteRemainingMs").value(1_800_000));
    }

    @Test
    void claim_malformed_token_returns_400_ko() throws Exception {
        // given
        var body = """
                {"token":"not a token"}
                """;

        // when / then
        mockMvc.perform(post(CLAIM_URL)
                        .with(jwt().jwt(token -> token.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("token"));
        verify(invitationService, never()).claim(anyString(), anyLong());
    }

    @Test
    void claim_full_invitation_returns_409_problem_ko() throws Exception {
        // when / then
        assertClaimProblem(AppErrorMessage.INVITATION_FULL, 409);
    }

    @Test
    void claim_expired_invitation_returns_410_problem_ko() throws Exception {
        // when / then
        assertClaimProblem(AppErrorMessage.INVITATION_EXPIRED, 410);
    }

    @Test
    void get_invitation_of_another_host_returns_404_ko() throws Exception {
        // given
        when(invitationService.getInvitation(10L, 3L))
                .thenThrow(new AppException(AppErrorMessage.INVITATION_NOT_FOUND));

        // when / then
        mockMvc.perform(get(INVITATIONS_URL + "/10").with(jwt().jwt(token -> token.subject("3"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVITATION_NOT_FOUND"));
    }

    @Test
    void cancel_returns_cancelled_invitation_ok() throws Exception {
        // given
        var invitation = invitation(InvitationStatus.CANCELLED);
        invitation.setCancelledAt(CREATED_AT.plusSeconds(60));
        when(invitationService.cancel(10L, 1L)).thenReturn(invitation);

        // when / then
        mockMvc.perform(post(INVITATIONS_URL + "/10/cancel").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").value("2026-10-08T10:01:00Z"));
    }

    private void assertClaimProblem(AppErrorMessage error, int expectedStatus) throws Exception {
        var body = """
                {"token":"%s"}
                """.formatted(TOKEN);
        when(invitationService.claim(TOKEN, 2L)).thenThrow(new AppException(error));
        mockMvc.perform(post(CLAIM_URL)
                        .with(jwt().jwt(token -> token.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value(error.name()));
    }

    private CreatedInvitation createdInvitation() {
        return CreatedInvitation.builder()
                .withInvitation(invitation(InvitationStatus.OPEN))
                .withToken(TOKEN)
                .build();
    }

    private Invitation invitation(InvitationStatus status) {
        return Invitation.builder()
                .withId(10L)
                .withHostAccountId(1L)
                .withHostColor(HostColor.BLACK)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withClockEnabled(true)
                .withInitialSeconds(1800)
                .withIncrementSeconds(10)
                .withRated(true)
                .withStatus(status)
                .withCreatedAt(CREATED_AT)
                .withExpiresAt(CREATED_AT.plusSeconds(900))
                .build();
    }

    private OnlineGame pendingGame() {
        return OnlineGame.builder()
                .withId(100L)
                .withSource(GameSource.INVITE)
                .withStatus(OnlineGameStatus.PENDING)
                .withWhiteAccountId(2L)
                .withWhiteDisplayName("Joiner")
                .withBlackAccountId(1L)
                .withBlackDisplayName("Host")
                .withViewerColor(ChessColor.WHITE)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withRated(true)
                .withClockEnabled(true)
                .withInitialSeconds(1800)
                .withIncrementSeconds(10)
                .withCreatedAt(CREATED_AT)
                .withReadyDeadlineAt(CREATED_AT.plusSeconds(180))
                .withWhiteRemainingMs(1_800_000L)
                .withBlackRemainingMs(1_800_000L)
                .build();
    }
}
