package com.mindora.app.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import com.mindora.user.application.port.TokenService;
import com.mindora.user.domain.RoleName;
import com.mindora.user.domain.UserAccount;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
class AdminAccessControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Test
    void adminMenusExposeKnowledgeBaseSection() throws Exception {
        mockMvc.perform(get("/api/v1/admin/menus")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(RoleName.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].path", hasItem("/knowledge")))
                .andExpect(jsonPath("$.data[1].path", is("/knowledge")))
                .andExpect(jsonPath("$.data[1].children[*].path", hasItem("sources")))
                .andExpect(jsonPath("$.data[1].children[*].path", hasItem("documents")))
                .andExpect(jsonPath("$.data[1].children[*].path", hasItem("indexes")));
    }

    private String tokenFor(RoleName role) {
        return tokenService.issue(new UserAccount(
                UUID.randomUUID(),
                role.value() + "@example.com",
                "hash",
                Set.of(role.value())));
    }
}
