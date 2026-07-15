package com.mindora.app.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mindora.app.MindOraApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = MindOraApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesOpenApiDocumentForFrontendGeneration() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.info.title").value("MindOra API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/public/articles']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/articles']").exists())
                .andReturn();

        String outputFile = System.getProperty("openapi.output");
        if (outputFile != null && !outputFile.isBlank()) {
            Path outputPath = Path.of(outputFile);
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, result.getResponse().getContentAsString() + System.lineSeparator());
        }
    }
}
