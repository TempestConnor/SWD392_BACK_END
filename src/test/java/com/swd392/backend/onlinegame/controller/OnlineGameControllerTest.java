package com.swd392.backend.onlinegame.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapperImpl;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OnlineGameController.class)
@Import({SecurityConfig.class, OnlineGameMapperImpl.class})
class OnlineGameControllerTest {

    private static final String GAME_URL = "/api/online-games/100";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnlineGameService onlineGameService;

    @Test
    void get_game_by_non_participant_returns_404_ko() throws Exception {
        // given
        when(onlineGameService.getGame(100L, 3L)).thenThrow(new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));

        // when / then
        mockMvc.perform(get(GAME_URL).with(jwt().jwt(token -> token.subject("3"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ONLINE_GAME_NOT_FOUND"));
    }

    @Test
    void get_game_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(get(GAME_URL)).andExpect(status().isUnauthorized());
        verify(onlineGameService, never()).getGame(anyLong(), anyLong());
    }
}
