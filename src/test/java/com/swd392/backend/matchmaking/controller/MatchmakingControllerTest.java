package com.swd392.backend.matchmaking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.matchmaking.mapper.MatchmakingMapperImpl;
import com.swd392.backend.matchmaking.model.QueueJoinRequest;
import com.swd392.backend.matchmaking.model.QueueRequest;
import com.swd392.backend.matchmaking.model.QueueStatus;
import com.swd392.backend.matchmaking.service.MatchmakingService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchmakingController.class)
@Import({SecurityConfig.class, MatchmakingMapperImpl.class})
class MatchmakingControllerTest {

    private static final String JOIN_URL = "/api/matchmaking/join";
    private static final String HEARTBEAT_URL = "/api/matchmaking/heartbeat";
    private static final String CANCEL_URL = "/api/matchmaking/cancel";
    private static final Instant JOINED_AT = Instant.parse("2026-10-19T10:00:00Z");
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchmakingService matchmakingService;

    @Test
    void join_returns_open_queue_request_ok() throws Exception {
        // given
        var body = """
                {"mode":"CLASSICAL","variant":"STANDARD"}
                """;
        when(matchmakingService.join(eq(1L), any())).thenReturn(queueRequest(QueueStatus.QUEUED, null));
        var captor = ArgumentCaptor.forClass(QueueJoinRequest.class);

        // when
        mockMvc.perform(post(JOIN_URL)
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.mode").value("CLASSICAL"))
                .andExpect(jsonPath("$.variant").value("STANDARD"))
                .andExpect(jsonPath("$.ratingAtEntry").value(1200))
                .andExpect(jsonPath("$.joinedAt").value("2026-10-19T10:00:00Z"))
                .andExpect(jsonPath("$.heartbeatIntervalSeconds").value(10))
                .andExpect(jsonPath("$.leftAt").doesNotExist())
                .andExpect(jsonPath("$.gameId").doesNotExist());

        // then
        verify(matchmakingService).join(eq(1L), captor.capture());
        var request = captor.getValue();
        assertAll(
                () -> assertThat(request.getModeCode()).isEqualTo("CLASSICAL"),
                () -> assertThat(request.getVariantCode()).isEqualTo("STANDARD"));
    }

    @Test
    void join_with_other_mode_or_variant_returns_400_problem_ko() throws Exception {
        // given
        var body = """
                {"mode":"BLITZ","variant":"CHESS960"}
                """;

        // when / then
        mockMvc.perform(post(JOIN_URL)
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field").value(containsInAnyOrder("mode", "variant")));
        verify(matchmakingService, never()).join(anyLong(), any());
    }

    @Test
    void join_when_matchmaking_unconfigured_returns_503_problem_ko() throws Exception {
        // given
        var body = """
                {"mode":"CLASSICAL","variant":"STANDARD"}
                """;
        when(matchmakingService.join(eq(1L), any()))
                .thenThrow(new AppException(AppErrorMessage.MATCHMAKING_UNAVAILABLE));

        // when / then
        mockMvc.perform(post(JOIN_URL)
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("MATCHMAKING_UNAVAILABLE"));
    }

    @Test
    void join_without_token_returns_401_ko() throws Exception {
        // given
        var body = """
                {"mode":"CLASSICAL","variant":"STANDARD"}
                """;

        // when / then
        mockMvc.perform(post(JOIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verify(matchmakingService, never()).join(anyLong(), any());
    }

    @Test
    void heartbeat_after_match_returns_game_id_ok() throws Exception {
        // given
        when(matchmakingService.heartbeat(1L)).thenReturn(queueRequest(QueueStatus.MATCHED, 100L));

        // when / then
        mockMvc.perform(post(HEARTBEAT_URL).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.gameId").value(100));
    }

    @Test
    void heartbeat_when_never_queued_returns_404_problem_ko() throws Exception {
        // given
        when(matchmakingService.heartbeat(1L)).thenThrow(new AppException(AppErrorMessage.QUEUE_REQUEST_NOT_FOUND));

        // when / then
        mockMvc.perform(post(HEARTBEAT_URL).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("QUEUE_REQUEST_NOT_FOUND"));
    }

    @Test
    void cancel_returns_cancelled_request_ok() throws Exception {
        // given
        when(matchmakingService.cancel(1L)).thenReturn(queueRequest(QueueStatus.CANCELLED, null));

        // when / then
        mockMvc.perform(post(CANCEL_URL).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.leftAt").value("2026-10-19T10:00:05Z"));
    }

    private QueueRequest queueRequest(QueueStatus status, Long gameId) {
        var closed = status != QueueStatus.QUEUED;

        return QueueRequest.builder()
                .withId(10L)
                .withAccountId(1L)
                .withModeCode("CLASSICAL")
                .withVariantCode("STANDARD")
                .withRatingAtEntry(1200)
                .withStatus(status)
                .withJoinedAt(JOINED_AT)
                .withHeartbeatAt(JOINED_AT)
                .withLeftAt(closed ? JOINED_AT.plusSeconds(5) : null)
                .withGameId(gameId)
                .withHeartbeatIntervalSeconds(10)
                .build();
    }
}
