package com.mindora.app.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindora.app.MindOraApplication;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.application.port.TokenService;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = MindOraApplication.class,
        properties = "mindora.assets.storage-dir=${java.io.tmpdir}/mindora-assets-test-${random.uuid}")
@AutoConfigureMockMvc
class AssetControllerTest {
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Test
    void superAdminUploadsListsAndServesAsset() throws Exception {
        String token = tokenFor(RoleName.SUPER_ADMIN);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                PNG_BYTES);

        String content = mockMvc.perform(multipart("/api/v1/admin/assets")
                        .file(file)
                        .param("assetType", "blog_cover")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetType", is("blog_cover")))
                .andExpect(jsonPath("$.data.publicUrl").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode asset = objectMapper.readTree(content).get("data");
        String id = asset.get("id").asText();

        mockMvc.perform(get("/api/v1/admin/assets")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/v1/public/assets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG_BYTES));
    }

    @Test
    void adminAssetUploadRequiresSuperAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                PNG_BYTES);

        mockMvc.perform(multipart("/api/v1/admin/assets").file(file))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/admin/assets")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsupportedAssetUploads() throws Exception {
        MockMultipartFile svg = new MockMultipartFile(
                "file",
                "cover.svg",
                "image/svg+xml",
                "<svg></svg>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/assets")
                        .file(svg)
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.SUPER_ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("asset_type_unsupported")));
    }

    @Test
    void assetDeletionIsNotExposedUntilReferenceTrackingExists() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/assets/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.SUPER_ADMIN)))
                .andExpect(status().isNotFound());
    }

    private String tokenFor(RoleName role) {
        return tokenService.issue(new UserAccount(
                UUID.randomUUID(),
                role.value() + "@example.com",
                "hash",
                Set.of(role.value())));
    }
}
