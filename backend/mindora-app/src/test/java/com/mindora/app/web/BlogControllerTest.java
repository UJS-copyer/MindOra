package com.mindora.app.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindora.app.MindOraApplication;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import com.mindora.user.infrastructure.TokenService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
class BlogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Test
    void superAdminManagesArticleAndPublicSiteReadsPublishedContent() throws Exception {
        String adminToken = tokenFor(RoleName.SUPER_ADMIN);
        String categoryId = postForId("/api/v1/admin/categories", adminToken, """
                {"name":"Engineering"}
                """);
        String tagId = postForId("/api/v1/admin/tags", adminToken, """
                {"name":"Java"}
                """);

        String articleId = postForId("/api/v1/admin/articles", adminToken, """
                {
                  "title":"Spring Notes",
                  "slug":"spring-notes",
                  "summary":"A short summary",
                  "body":"# Spring",
                  "categoryId":"%s",
                  "tagIds":["%s"],
                  "visibility":"public"
                }
                """.formatted(categoryId, tagId));

        mockMvc.perform(post("/api/v1/admin/articles/{id}/publish", articleId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("published")));

        mockMvc.perform(get("/api/v1/public/articles")
                        .param("categoryId", categoryId)
                        .param("tagId", tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slug", is("spring-notes")));

        mockMvc.perform(get("/api/v1/public/articles/{slug}", "spring-notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readCount", is(1)));

        mockMvc.perform(post("/api/v1/admin/articles/{id}/unpublish", articleId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("unpublished")));

        mockMvc.perform(get("/api/v1/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void regularUserCannotCreateAdminArticle() throws Exception {
        mockMvc.perform(post("/api/v1/admin/articles")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Blocked",
                                  "slug":"blocked",
                                  "summary":"Blocked",
                                  "body":"# Blocked",
                                  "visibility":"public"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanEditDraftBeforePublishing() throws Exception {
        String adminToken = tokenFor(RoleName.SUPER_ADMIN);
        String articleId = postForId("/api/v1/admin/articles", adminToken, """
                {
                  "title":"Draft",
                  "slug":"draft",
                  "summary":"Draft",
                  "body":"# Draft",
                  "visibility":"public"
                }
                """);

        mockMvc.perform(put("/api/v1/admin/articles/{id}", articleId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Edited Draft",
                                  "slug":"edited-draft",
                                  "summary":"Edited",
                                  "body":"# Edited",
                                  "visibility":"public"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Edited Draft")))
                .andExpect(jsonPath("$.data.slug", is("edited-draft")));
    }

    @Test
    void adminListsArticlesCategoriesAndTags() throws Exception {
        String adminToken = tokenFor(RoleName.SUPER_ADMIN);
        postForId("/api/v1/admin/categories", adminToken, """
                {"name":"Notes"}
                """);
        postForId("/api/v1/admin/tags", adminToken, """
                {"name":"MindOra"}
                """);
        postForId("/api/v1/admin/articles", adminToken, """
                {
                  "title":"Listed",
                  "slug":"listed",
                  "summary":"Listed",
                  "body":"# Listed",
                  "visibility":"public"
                }
                """);

        mockMvc.perform(get("/api/v1/admin/articles")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slug", is("listed")));

        mockMvc.perform(get("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/v1/admin/tags")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    private String postForId(String path, String token, String body) throws Exception {
        String content = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("success")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(content).get("data");
        return data.get("id").asText();
    }

    private String tokenFor(RoleName role) {
        return tokenService.issue(new UserAccount(
                UUID.randomUUID(),
                role.name().toLowerCase() + "@example.com",
                "hash",
                Set.of(role)));
    }
}
