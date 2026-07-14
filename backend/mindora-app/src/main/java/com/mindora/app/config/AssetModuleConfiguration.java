package com.mindora.app.config;

import com.mindora.asset.application.AssetRepository;
import com.mindora.asset.application.AssetService;
import com.mindora.asset.application.FileSystemAssetRepository;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetModuleConfiguration {
    @Bean
    AssetRepository assetRepository(
            @Value("${mindora.assets.storage-dir:data/assets}") String storageDirectory) {
        return new FileSystemAssetRepository(Path.of(storageDirectory));
    }

    @Bean
    AssetService assetService(AssetRepository assetRepository) {
        return new AssetService(assetRepository);
    }
}
