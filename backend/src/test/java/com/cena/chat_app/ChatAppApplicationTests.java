package com.cena.chat_app;

import com.cena.chat_app.config.TestRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
class ChatAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
