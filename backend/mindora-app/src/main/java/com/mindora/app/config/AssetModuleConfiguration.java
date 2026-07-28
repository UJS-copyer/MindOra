package com.mindora.app.config;

import com.mindora.asset.application.AssetService;
import com.mindora.asset.domain.AssetRepository;
import com.mindora.asset.infrastructure.persistence.filesystem.FileSystemAssetRepository;
import com.mindora.asset.infrastructure.persistence.jdbc.JdbcAssetRepository;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetModuleConfiguration {
    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.asset", havingValue = "filesystem")
    AssetRepository assetRepository(
            @Value("${mindora.assets.storage-dir:data/assets}") String storageDirectory) {
        return new FileSystemAssetRepository(Path.of(storageDirectory));
    }

    @Bean
    @ConditionalOnProperty(name = "mindora.persistence.asset", havingValue = "jdbc", matchIfMissing = true)
    AssetRepository jdbcAssetRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${mindora.assets.storage-dir:data/assets}") String storageDirectory) {
        return new JdbcAssetRepository(jdbcTemplate, Path.of(storageDirectory));
    }

    @Bean
    AssetService assetService(AssetRepository assetRepository) {
        return new AssetService(assetRepository);
    }
}
