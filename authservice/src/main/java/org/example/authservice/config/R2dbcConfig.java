package org.example.authservice.config;

import org.springframework.context.annotation.*;
import org.springframework.data.r2dbc.dialect.H2Dialect;
import org.springframework.data.r2dbc.mapping.*;
import org.springframework.data.relational.core.mapping.NamingStrategy;

@Configuration
public class R2dbcConfig{
    @Bean
    public R2dbcMappingContext r2dbcMappingContext() {
        return R2dbcMappingContext.forPlainIdentifiers();
    }
}