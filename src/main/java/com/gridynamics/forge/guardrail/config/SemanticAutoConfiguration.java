package com.gridynamics.forge.guardrail.config;

import com.gridynamics.forge.guardrail.cache.RedisEmbeddingCache;
import com.gridynamics.forge.guardrail.chain.SemanticValidator;
import com.gridynamics.forge.guardrail.embedding.EmbeddingProvider;
import com.gridynamics.forge.guardrail.embedding.OnnxEmbeddingProvider;
import com.gridynamics.forge.guardrail.semantic.InMemorySemanticStore;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Semantic layer auto-configuration (ONNX + in-memory store).
 *
 * <p>Requires only an ONNX model ({@code forge.guardrail.semantic.onnx.*}) and
 * {@code forge.guardrail.semantic.enabled=true}. No database is needed — seed
 * embeddings are computed at startup from {@code classpath:semantic/semantic_seeds.csv}
 * and held entirely in the JVM heap.
 *
 * <p>Redis ({@code forge.guardrail.semantic.redis.enabled=true}) is optional and
 * caches the per-request ONNX embedding to avoid redundant model inference.
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
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
    @ConditionalOnMissingBean(InMemorySemanticStore.class)
    public InMemorySemanticStore inMemorySemanticStore() {
        return new InMemorySemanticStore();
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
    @ConditionalOnBean({EmbeddingProvider.class, InMemorySemanticStore.class})
    public SemanticValidator semanticValidator(
            GuardrailProperties props,
            EmbeddingProvider embeddingProvider,
            InMemorySemanticStore semanticStore,
            ObjectProvider<RedisEmbeddingCache> redisCacheProvider) {
        return new SemanticValidator(
                props,
                embeddingProvider,
                semanticStore,
                redisCacheProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnMissingBean(SemanticStartupInitializer.class)
    @ConditionalOnBean({EmbeddingProvider.class, InMemorySemanticStore.class})
    public SemanticStartupInitializer semanticStartupInitializer(
            GuardrailProperties props,
            EmbeddingProvider embeddingProvider,
            InMemorySemanticStore semanticStore,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        return new SemanticStartupInitializer(
                props, embeddingProvider, semanticStore, redisTemplateProvider);
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
                                Ensure the ONNX model paths (forge.guardrail.semantic.onnx.*) are valid \
                                and the onnxruntime JAR is on the classpath. \
                                Paraphrased unsafe prompts will be allowed.""");
                return;
            }
            throw new IllegalStateException(
                    "forge.guardrail.semantic.enabled=true but SemanticValidator is missing. "
                            + "Provide valid forge.guardrail.semantic.onnx.model-path and "
                            + "forge.guardrail.semantic.onnx.tokenizer-path.");
        };
    }
}
