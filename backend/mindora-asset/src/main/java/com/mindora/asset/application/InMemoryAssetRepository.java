package com.mindora.asset.application;

import com.mindora.asset.domain.Asset;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAssetRepository implements AssetRepository {
    private final Map<UUID, Asset> assets = new LinkedHashMap<>();
    private final Map<UUID, byte[]> contents = new LinkedHashMap<>();

    @Override
    public Asset save(Asset asset, InputStream content) throws IOException {
        assets.put(asset.id(), asset);
        contents.put(asset.id(), content.readAllBytes());
        return asset;
    }

    @Override
    public Optional<Asset> find(UUID id) {
        return Optional.ofNullable(assets.get(id));
    }

    @Override
    public Optional<byte[]> content(UUID id) {
        return Optional.ofNullable(contents.get(id)).map(byte[]::clone);
    }

    @Override
    public List<Asset> list() {
        return new ArrayList<>(assets.values());
    }

    @Override
    public void delete(UUID id) {
        assets.remove(id);
        contents.remove(id);
    }
}
