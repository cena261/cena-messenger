package com.cena.chat_app.config;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import java.util.Collections;

@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${spring.data.mongodb.host}")
    private String host;

    @Value("${spring.data.mongodb.port}")
    private int port;

    @Value("${spring.data.mongodb.username}")
    private String username;

    @Value("${spring.data.mongodb.password}")
    private String password;

    @Value("${spring.data.mongodb.authentication-database}")
    private String authenticationDatabase;

    @Override
    protected String getDatabaseName() {
        return database;
    }

    @Override
    protected void configureClientSettings(MongoClientSettings.Builder builder) {
        builder
            .credential(MongoCredential.createCredential(username, authenticationDatabase, password.toCharArray()))
            .applyToClusterSettings(settings -> {
                settings.hosts(Collections.singletonList(new ServerAddress(host, port)));
            });
    }
}
