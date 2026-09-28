package org.example.authservice.repository;

import org.example.authservice.config.TestR2dbcConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Import(TestR2dbcConfig.class)
public abstract class BaseRepositoryTest {
    @Autowired protected DatabaseClient databaseClient;

    @BeforeEach
    void cleanDatabase() {
        StepVerifier.create(
                delete("addresses")
                        .then(delete("sessions"))
                        .then(delete("refresh_tokens"))
                        .then(delete("user_profiles"))
                        .then(delete("user_roles"))
                        .then(delete("users_credentials"))
        ).verifyComplete();
    }

    protected Mono<Void> insertUser(Long id,String email) {
        return databaseClient.sql("""
                INSERT INTO users_credentials
                (id,email,password,full_name,role,enabled,created_at,updated_at)
                VALUES
                (:id,:email,'encoded-password','Test User','ROLE_CUSTOMER',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """)
                .bind("id",id)
                .bind("email",email)
                .fetch()
                .rowsUpdated()
                .then();
    }

    protected Mono<Void> insertProfile(Long id,Long userId) {
        return databaseClient.sql("""
                INSERT INTO user_profiles
                (id,auth_user_id,customer_segment,status,created_at,updated_at)
                VALUES
                (:id,:userId,'STANDARD','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """)
                .bind("id",id)
                .bind("userId",userId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    protected Mono<Void> insertRefreshToken(Long id,Long userId,String token) {
        return databaseClient.sql("""
                INSERT INTO refresh_tokens
                (id,user_id,token,expires_at,revoked,created_at)
                VALUES
                (:id,:userId,:token,DATEADD('DAY',7,CURRENT_TIMESTAMP),FALSE,CURRENT_TIMESTAMP)
                """)
                .bind("id",id)
                .bind("userId",userId)
                .bind("token",token)
                .fetch()
                .rowsUpdated()
                .then();
    }

    protected Mono<Void> delete(String table) {
        return databaseClient.sql("DELETE FROM "+table).fetch().rowsUpdated().then();
    }
}