package com.mindora.asset.domain;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {
    Asset save(Asset asset, InputStream content) throws IOException;

    Optional<Asset> find(UUID id);

    Optional<byte[]> content(UUID id);

    List<Asset> list();

    void delete(UUID id) throws IOException;
}
