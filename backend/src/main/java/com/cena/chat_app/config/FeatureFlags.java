package com.cena.chat_app.config;

import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class FeatureFlags {
    private boolean mediaEnabled = false;
    private boolean emailEnabled = false;
}
