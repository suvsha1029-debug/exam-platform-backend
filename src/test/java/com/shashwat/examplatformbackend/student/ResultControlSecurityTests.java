package com.shashwat.examplatformbackend.student;

import com.shashwat.examplatformbackend.config.AdminCsrfFilter;
import com.shashwat.examplatformbackend.config.AdminCsrfTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.desktop-console.required=false", "spring.datasource.url=jdbc:h2:mem:result-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class ResultControlSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired AdminCsrfFilter csrfFilter;
    @Autowired AdminCsrfTokenService tokens;
    MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(csrfFilter).build();
    }
    @Test void listingIsMasterOnly() throws Exception {
        mvc.perform(get("/api/master-admin/result-controls")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/master-admin/result-controls").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/master-admin/result-controls").with(user("master").roles("MASTER_ADMIN", "ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/master-admin/result-controls/SAMPLE/history").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void decisionsRequireMasterAndCsrfAndExplicitState() throws Exception {
        var session = new MockHttpSession();
        String token = tokens.token(session);
        String decision = "{\"withheld\":true,\"expectedWithheld\":false,\"reason\":\"Review pending\"}";
        mvc.perform(patch("/api/master-admin/result-controls/SAMPLE").with(user("admin").roles("ADMIN"))
                .session(session).header("X-Admin-CSRF", token).contentType("application/json").content(decision)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/master-admin/result-controls/SAMPLE").with(user("master").roles("MASTER_ADMIN", "ADMIN"))
                .session(session).contentType("application/json").content(decision)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/master-admin/result-controls/SAMPLE").with(user("master").roles("MASTER_ADMIN", "ADMIN"))
                .session(session).header("X-Admin-CSRF", token).contentType("application/json").content("{\"reason\":\"Review pending\"}")).andExpect(status().isBadRequest());
    }
}
