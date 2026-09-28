package com.swd392.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Swd392BackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void hello_whenApplicationStarts_shouldReturnWelcomeMessage() throws Exception {
		mockMvc.perform(get("/api/hello"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("SWD392 backend is running"));
	}

}
