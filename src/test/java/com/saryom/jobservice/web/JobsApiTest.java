package com.saryom.jobservice.web;

import com.saryom.jobservice.auth.FirebaseAuthenticationFilter;
import com.saryom.jobservice.auth.ImportTokenAuthentication;
import com.saryom.jobservice.auth.ImportTokenAuthenticationFilter;
import com.saryom.jobservice.auth.RestAuthenticationEntryPoint;
import com.saryom.jobservice.auth.StubTokenVerifier;
import com.saryom.jobservice.config.SecurityConfig;
import com.saryom.jobservice.domain.RecruiterSort;
import com.saryom.jobservice.error.ApiExceptionHandler;
import com.saryom.jobservice.service.ApplicationService;
import com.saryom.jobservice.service.ImportService;
import com.saryom.jobservice.service.ImportTokenService;
import com.saryom.jobservice.service.RecruiterService;
import com.saryom.jobservice.web.dto.DirectorySummaryResponse;
import com.saryom.jobservice.web.dto.ImportResponse;
import com.saryom.jobservice.web.dto.RecruiterProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ApplicationController.class, ImportController.class, RecruiterController.class,
        TokenController.class})
@ActiveProfiles("dev")
@Import({SecurityConfig.class, FirebaseAuthenticationFilter.class, ImportTokenAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, StubTokenVerifier.class, ApiExceptionHandler.class})
class JobsApiTest {

    private static final String ALICE = "Bearer dev:alice";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApplicationService applications;
    @MockitoBean
    private ImportService imports;
    @MockitoBean
    private RecruiterService recruiters;
    @MockitoBean
    private ImportTokenService tokens;

    @Test
    void everythingPrivateNeedsASignIn() throws Exception {
        mvc.perform(get("/api/jobs/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/applications/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/tokens")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/tokens")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/import").contentType(MediaType.APPLICATION_JSON).content("{\"jobs\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/recruiters").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Sam\",\"roleTitle\":\"Dev\",\"email\":\"s@x.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theDirectoryAndItsSummaryArePublic() throws Exception {
        when(recruiters.browse(any(), any(), any(), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of()));
        when(recruiters.summary()).thenReturn(new DirectorySummaryResponse(0, 0, List.of(), List.of()));

        mvc.perform(get("/api/jobs/recruiters")).andExpect(status().isOk());
        mvc.perform(get("/api/jobs/recruiters/summary")).andExpect(status().isOk());
    }

    @Test
    void aSignedInViewerIsPassedThroughSoContactsUnlock() throws Exception {
        when(recruiters.browse(any(), any(), any(), anyInt(), anyInt(), eq("alice"))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/jobs/recruiters").header("Authorization", ALICE)).andExpect(status().isOk());

        verify(recruiters).browse(any(), any(), any(), anyInt(), anyInt(), eq("alice"));
    }

    @Test
    void anImportTokenWorksOnTheUploadEndpointOnly() throws Exception {
        UUID tokenId = UUID.randomUUID();
        when(tokens.resolve("sjt_good")).thenReturn(Optional.of(new ImportTokenAuthentication("alice", tokenId)));
        when(imports.upload(eq("alice"), eq(tokenId), any())).thenReturn(new ImportResponse(0, 0, 0, 0, 0, 0));

        mvc.perform(post("/api/jobs/import").header("Authorization", "Bearer sjt_good")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobs\":[]}"))
                .andExpect(status().isOk());
        // The same token, anywhere else, is not a sign-in.
        mvc.perform(get("/api/jobs/applications").header("Authorization", "Bearer sjt_good"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/tokens").header("Authorization", "Bearer sjt_good"))
                .andExpect(status().isUnauthorized());
        // And it does not unlock contact details as a viewer would.
        when(recruiters.browse(any(), any(), any(), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/jobs/recruiters").header("Authorization", "Bearer sjt_good"))
                .andExpect(status().isOk());
        verify(recruiters).browse(any(), any(), any(), anyInt(), anyInt(), isNull());
    }

    @Test
    void anUnknownImportTokenIsToldSoRatherThanTreatedAsAnonymous() throws Exception {
        when(tokens.resolve("sjt_stale")).thenReturn(Optional.empty());

        mvc.perform(post("/api/jobs/import").header("Authorization", "Bearer sjt_stale")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"jobs\":[]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("revoked")));
    }

    @Test
    void anApplicationNeedsACompanyARoleAndAStatus() throws Exception {
        mvc.perform(post("/api/jobs/applications").header("Authorization", ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"company\":\"\",\"role\":\"Dev\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.company").exists())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void theSortOrderIsPassedThroughAndAnUnknownOneFallsBackToTheDefault() throws Exception {
        when(recruiters.browse(any(), any(), any(), anyInt(), anyInt(), isNull())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/jobs/recruiters").param("sort", "reported")).andExpect(status().isOk());
        verify(recruiters).browse(isNull(), isNull(), eq(RecruiterSort.REPORTED), eq(0), eq(20), isNull());

        mvc.perform(get("/api/jobs/recruiters").param("sort", "whatever")).andExpect(status().isOk());
        verify(recruiters).browse(isNull(), isNull(), eq(RecruiterSort.RECENT), eq(0), eq(20), isNull());
    }

    @Test
    void aRecruiterProfileIsPublicAndKnowsWhoIsLooking() throws Exception {
        UUID id = UUID.randomUUID();
        when(recruiters.profile(eq(id), any())).thenReturn(new RecruiterProfileResponse(id, "Priya Shah", null,
                "Acme", null, null, null, true, false, 1, null, null, List.of(), List.of(), List.of()));

        mvc.perform(get("/api/jobs/recruiters/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Priya Shah"));
        verify(recruiters).profile(id, null);

        mvc.perform(get("/api/jobs/recruiters/" + id).header("Authorization", ALICE)).andExpect(status().isOk());
        verify(recruiters).profile(id, "alice");
    }

    @Test
    void settingAFollowUpNeedsASignInAndTakesADateOrNull() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/jobs/applications/" + id + "/follow-up").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"followUpOn\":\"2026-11-20\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/jobs/applications/" + id + "/follow-up").header("Authorization", ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"followUpOn\":\"2026-11-20\"}"))
                .andExpect(status().isOk());
        verify(applications).scheduleFollowUp(id, "alice", LocalDate.of(2026, 11, 20));

        mvc.perform(patch("/api/jobs/applications/" + id + "/follow-up").header("Authorization", ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"followUpOn\":null}"))
                .andExpect(status().isOk());
        verify(applications).scheduleFollowUp(id, "alice", null);
    }

    @Test
    void anUnknownRoleCategoryIsABadRequestNotAServerError() throws Exception {
        mvc.perform(get("/api/jobs/recruiters").param("category", "WIZARDRY"))
                .andExpect(status().isBadRequest());
    }
}
