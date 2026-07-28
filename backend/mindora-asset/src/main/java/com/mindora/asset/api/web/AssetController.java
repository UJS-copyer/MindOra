package com.mindora.asset.api.web;

import com.mindora.asset.application.AssetService;
import com.mindora.asset.domain.Asset;
import com.mindora.common.api.ApiResponse;
import com.mindora.common.id.PublicIds;
import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class AssetController {
    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping(value = "/api/v1/admin/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetView> upload(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String assetType,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) throws java.io.IOException {
        return success(view(assetService.upload(
                file.getOriginalFilename(),
                file.getContentType(),
                assetType,
                file.getSize(),
                file.getInputStream())), traceId);
    }

    @GetMapping("/api/v1/admin/assets")
    public ApiResponse<java.util.List<AssetView>> list(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(assetService.list().stream().map(this::view).toList(), traceId);
    }

    @GetMapping("/api/v1/public/assets/{id}")
    public ResponseEntity<byte[]> content(@PathVariable String id) {
        UUID assetId = PublicIds.toUuid(id);
        Asset asset = assetService.find(assetId)
                .orElseThrow(() -> new com.mindora.common.exception.BusinessException(
                        "asset_not_found", "Asset not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(assetService.content(assetId));
    }

    private AssetView view(Asset asset) {
        return new AssetView(
                PublicIds.toPublicId(asset.id()),
                asset.fileName(),
                asset.mimeType(),
                asset.size(),
                asset.assetType(),
                asset.publicUrl(),
                asset.createdAt());
    }

    private <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.success(
                data,
                traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
    }

    public record AssetView(
            String id,
            String fileName,
            String mimeType,
            long size,
            String assetType,
            String publicUrl,
            Instant createdAt) {
    }
}
