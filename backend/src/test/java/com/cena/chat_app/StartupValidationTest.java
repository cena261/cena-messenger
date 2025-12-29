package com.cena.chat_app;

import com.cena.chat_app.config.FeatureFlags;
import com.cena.chat_app.config.TestMongoDBConfiguration;
import com.cena.chat_app.config.TestRedisConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestRedisConfiguration.class, TestMongoDBConfiguration.class})
class StartupValidationTest {

    @Autowired
    private FeatureFlags featureFlags;

    @Test
    void testApplicationStartsSuccessfully() {
        assertNotNull(featureFlags);
    }

    @Test
    void testFeatureFlagsAreSetDuringStartup() {
        assertTrue(featureFlags.isMediaEnabled());
        assertTrue(featureFlags.isEmailEnabled());
    }
}
