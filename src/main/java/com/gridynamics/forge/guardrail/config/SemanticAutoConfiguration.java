package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.cache.RedisEmbeddingCache;
import com.gridynamics.forge.guardrail.chain.SemanticValidator;
import com.gridynamics.forge.guardrail.embedding.EmbeddingProvider;
import com.gridynamics.forge.guardrail.embedding.OnnxEmbeddingProvider;
import com.gridynamics.forge.guardrail.semantic.PgVectorSemanticStore;
import com.gridynamics.forge.guardrail.semantic.SemanticPatternSeeder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

/**
 * Semantic layer auto-configuration (ONNX + pgvector). Requires a {@link JdbcTemplate}
 * and {@code forge.guardrail.semantic.enabled=true}.
 */
@AutoConfiguration
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        RedisAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "forge.guardrail.semantic", name = "enabled", havingValue = "true")
@ConditionalOnClass(name = "ai.onnxruntime.OrtEnvironment")
public class SemanticAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    public EmbeddingProvider onnxEmbeddingProvider(GuardrailProperties props) throws Exception {
        var onnx = props.getSemantic().getOnnx();
        return new OnnxEmbeddingProvider(
                onnx.getModelPath(),
                onnx.getTokenizerPath(),
                onnx.getMaxTokens(),
                onnx.getIntraOpThreads()
        );
    }

    @Bean
    @ConditionalOnMissingBean(PgVectorSemanticStore.class)
    @ConditionalOnBean(JdbcTemplate.class)
    public PgVectorSemanticStore pgVectorSemanticStore(JdbcTemplate jdbcTemplate,
                                                        GuardrailProperties props) {
        return new PgVectorSemanticStore(
                jdbcTemplate,
                props.getSemantic().getPgvector().getTableName()
        );
    }

    @Bean
    @ConditionalOnMissingBean(RedisEmbeddingCache.class)
    @ConditionalOnExpression("${forge.guardrail.semantic.redis.enabled:true}")
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisEmbeddingCache redisEmbeddingCache(StringRedisTemplate redisTemplate,
                                                    GuardrailProperties props) {
        var redis = props.getSemantic().getRedis();
        return new RedisEmbeddingCache(
                redisTemplate,
                Duration.ofSeconds(redis.getTtlSeconds()),
                redis.getKeyPrefix()
        );
    }

    @Bean
    @ConditionalOnMissingBean(SemanticValidator.class)
    @ConditionalOnBean({EmbeddingProvider.class, PgVectorSemanticStore.class})
    public SemanticValidator semanticValidator(
            GuardrailProperties props,
            EmbeddingProvider embeddingProvider,
            PgVectorSemanticStore semanticStore,
            ObjectProvider<RedisEmbeddingCache> redisCacheProvider) {
        return new SemanticValidator(
                props,
                embeddingProvider,
                semanticStore,
                redisCacheProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean(SemanticPatternSeeder.class)
    @ConditionalOnBean({JdbcTemplate.class, EmbeddingProvider.class, PgVectorSemanticStore.class})
    public SemanticPatternSeeder semanticPatternSeeder(JdbcTemplate jdbcTemplate,
                                                        EmbeddingProvider embeddingProvider,
                                                        PgVectorSemanticStore semanticStore) {
        return new SemanticPatternSeeder(jdbcTemplate, embeddingProvider, semanticStore);
    }

    @Bean
    @ConditionalOnMissingBean(SemanticStartupInitializer.class)
    @ConditionalOnBean({EmbeddingProvider.class, SemanticPatternSeeder.class, JdbcTemplate.class})
    public SemanticStartupInitializer semanticStartupInitializer(
            GuardrailProperties props,
            EmbeddingProvider embeddingProvider,
            SemanticPatternSeeder seeder,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        return new SemanticStartupInitializer(
                props, embeddingProvider, seeder, jdbcTemplate, redisTemplateProvider);
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean(name = "guardrailSemanticSeedRunner")
    @ConditionalOnBean(SemanticStartupInitializer.class)
    public ApplicationListener<ApplicationReadyEvent> guardrailSemanticSeedRunner(
            SemanticStartupInitializer initializer) {
        return event -> initializer.initialize();
    }

    /** Fails startup when semantic is enabled but the SEMANTIC validator bean cannot be created. */
    @Bean
    @ConditionalOnBean(GuardrailProperties.class)
    public SmartInitializingSingleton semanticPipelineVerifier(
            GuardrailProperties props,
            ObjectProvider<SemanticValidator> semanticValidator) {
        return () -> {
            if (!props.getSemantic().isEnabled()) {
                return;
            }
            if (semanticValidator.getIfAvailable() != null) {
                return;
            }
            if (props.getSemantic().isFailOpen()) {
                org.slf4j.LoggerFactory.getLogger(SemanticAutoConfiguration.class)
                        .error("""
                                [GUARDRAIL] semantic.enabled=true but SemanticValidator was not created. \
                                Add spring-boot-starter-jdbc + datasource, ONNX model paths, and pgvector. \
                                Paraphrased unsafe prompts will be allowed.""");
                return;
            }
            throw new IllegalStateException(
                    "forge.guardrail.semantic.enabled=true but SemanticValidator is missing. "
                            + "Provide JdbcTemplate (datasource), valid semantic.onnx.* paths, and pgvector.");
        };
    }
}
