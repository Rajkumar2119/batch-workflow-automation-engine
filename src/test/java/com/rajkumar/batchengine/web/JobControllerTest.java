package com.rajkumar.batchengine.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rajkumar.batchengine.domain.TriggerSource;
import com.rajkumar.batchengine.repository.JobAuditRepository;
import com.rajkumar.batchengine.service.JobConflictException;
import com.rajkumar.batchengine.service.JobNotFoundException;
import com.rajkumar.batchengine.service.JobRunStatus;
import com.rajkumar.batchengine.service.JobWorkflowService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {JobController.class, AuditController.class})
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobWorkflowService workflowService;

    @MockBean
    private JobAuditRepository auditRepository;

    @Test
    void launchReturns202WithLocation() throws Exception {
        when(workflowService.launch(eq("dailyFeedJob"), eq(LocalDate.of(2026, 10, 20)),
                eq(TriggerSource.CONTROL_M), eq("CTM-SERVER-01"), eq(false)))
                .thenReturn(runStatus(42L, "STARTING"));

        mockMvc.perform(post("/api/v1/jobs/dailyFeedJob/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"runDate":"2026-10-20","triggerSource":"CONTROL_M","triggeredBy":"CTM-SERVER-01"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/jobs/runs/42"))
                .andExpect(jsonPath("$.executionId").value(42))
                .andExpect(jsonPath("$.status").value("STARTING"));
    }

    @Test
    void launchWithoutBodyDefaultsToTodayAndApi() throws Exception {
        when(workflowService.launch(anyString(), any(), any(), any(), anyBoolean()))
                .thenReturn(runStatus(7L, "STARTING"));

        mockMvc.perform(post("/api/v1/jobs/dailyFeedJob/runs"))
                .andExpect(status().isAccepted());

        verify(workflowService).launch("dailyFeedJob", LocalDate.now(), TriggerSource.API, null, false);
    }

    @Test
    void alreadyCompletedRunReturns409() throws Exception {
        when(workflowService.launch(anyString(), any(), any(), any(), anyBoolean()))
                .thenThrow(new JobConflictException("already completed"));

        mockMvc.perform(post("/api/v1/jobs/dailyFeedJob/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runDate\":\"2026-10-20\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("already completed"));
    }

    @Test
    void unknownExecutionReturns404() throws Exception {
        when(workflowService.status(99L)).thenThrow(new JobNotFoundException("No job execution with id 99"));

        mockMvc.perform(get("/api/v1/jobs/runs/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidCountReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/dailyFeedJob/runs").param("count", "0"))
                .andExpect(status().isBadRequest());
    }

    private static JobRunStatus runStatus(Long id, String status) {
        return new JobRunStatus(id, "dailyFeedJob", status, "UNKNOWN", "", null, null,
                Map.of("runDate", "2026-10-20"), List.of());
    }
}
