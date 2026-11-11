package com.saryom.jobservice.rls;

import com.saryom.jobservice.support.TestPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two tiers, checked against a real Postgres as the unprivileged role the
 * service runs as: a user's applications are theirs alone, and the recruiter
 * directory is everyone's to read.
 *
 * <p>Half of these go through the HTTP API and half ask the database directly,
 * deliberately. The API tests would pass on application-level filtering alone.
 * The direct ones run SQL with no {@code where user_id = ?} at all and prove
 * the policies hold when the application forgets, which is the case row level
 * security exists for.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RowLevelSecurityTest {

    private static final String ALICE = "Bearer dev:alice";
    private static final String BOB = "Bearer dev:bob";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestPostgres.register(registry);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource pool;

    @BeforeEach
    void emptyTables() throws SQLException {
        // Each user clears their own rows: nobody, the service included, can
        // delete across users, which is itself the property under test.
        for (String uid : new String[] {"alice", "bob"}) {
            try (Connection c = TestPostgres.connectAsService()) {
                c.setAutoCommit(false);
                actAs(c, uid);
                try (Statement sql = c.createStatement()) {
                    sql.execute("DELETE FROM application");
                    sql.execute("DELETE FROM import_token");
                    sql.execute("DELETE FROM recruiter");
                }
                c.commit();
            }
        }
    }

    private static void actAs(Connection c, String uid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "select set_config('app.user_id', ?, true), set_config('app.is_admin', 'false', true), "
                        + "set_config('app.token_hash', '', true)")) {
            ps.setString(1, uid == null ? "" : uid);
            ps.execute();
        }
    }

    private static long count(Connection c, String table) throws SQLException {
        try (Statement sql = c.createStatement(); ResultSet rs = sql.executeQuery("select count(*) from " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String createApplication(String who, String company) throws Exception {
        MvcResult result = mvc.perform(post("/api/jobs/applications").header("Authorization", who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":"%s","role":"Java Developer","status":"APPLIED",
                                 "contactName":"Priya Shah","contactEmail":"priya@%s.example",
                                 "contactPhone":"555-0100"}""".formatted(company, company.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // ---- private tier ------------------------------------------------------

    @Test
    void aUserSeesOnlyTheirOwnApplicationsThroughTheApi() throws Exception {
        String alices = createApplication(ALICE, "Acme");

        mvc.perform(get("/api/jobs/applications").header("Authorization", ALICE))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/jobs/applications").header("Authorization", BOB))
                .andExpect(jsonPath("$", hasSize(0)));
        // Not 403: whether the id exists at all is Alice's business.
        mvc.perform(get("/api/jobs/applications/" + alices).header("Authorization", BOB))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/jobs/applications/" + alices).header("Authorization", BOB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/applications").header("Authorization", ALICE))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void theDatabaseHidesOtherUsersApplicationsFromAnUnfilteredQuery() throws Exception {
        createApplication(ALICE, "Acme");

        try (Connection c = TestPostgres.connectAsService()) {
            c.setAutoCommit(false);
            // No where clause anywhere below. The policy is all that stands
            // between Bob and Alice's row.
            actAs(c, "bob");
            assertThat(count(c, "application")).isZero();
            actAs(c, "alice");
            assertThat(count(c, "application")).isEqualTo(1);
            actAs(c, null);
            assertThat(count(c, "application")).isZero();
            c.rollback();
        }
    }

    @Test
    void theDatabaseRefusesToChangeOrForgeAnotherUsersApplication() throws Exception {
        createApplication(ALICE, "Acme");

        try (Connection c = TestPostgres.connectAsService()) {
            c.setAutoCommit(false);
            actAs(c, "bob");
            try (Statement sql = c.createStatement()) {
                assertThat(sql.executeUpdate("UPDATE application SET company = 'Hacked'")).isZero();
                assertThat(sql.executeUpdate("DELETE FROM application")).isZero();
                // Writing a row into someone else's name is refused outright.
                assertThatThrownBy(() -> sql.executeUpdate(
                        "INSERT INTO application (id, user_id, company, role, status, created_at, updated_at) "
                                + "VALUES ('" + UUID.randomUUID() + "', 'alice', 'Forged', 'x', 'SAVED', now(), now())"))
                        .hasMessageContaining("row-level security");
            }
            c.rollback();
        }
    }

    @Test
    void aTransactionWithNoIdentityAtAllSeesNothingPrivate() throws Exception {
        createApplication(ALICE, "Acme");

        // A brand new session: the settings have never been written, which is
        // what a code path that bypassed the transaction manager would look like.
        try (Connection c = TestPostgres.connectAsService()) {
            assertThat(count(c, "application")).isZero();
            assertThat(count(c, "import_token")).isZero();
        }
    }

    @Test
    void oneRequestsIdentityDoesNotFollowThePooledConnectionToTheNext() throws Exception {
        createApplication(ALICE, "Acme");

        // Borrow every connection the pool will give out; Alice's request ran
        // on one of them. None may still carry her uid.
        Connection[] borrowed = new Connection[3];
        try {
            for (int i = 0; i < borrowed.length; i++) {
                borrowed[i] = pool.getConnection();
                try (Statement sql = borrowed[i].createStatement();
                     ResultSet rs = sql.executeQuery("select coalesce(current_setting('app.user_id', true), '')")) {
                    rs.next();
                    assertThat(rs.getString(1)).isEmpty();
                }
            }
        } finally {
            for (Connection c : borrowed) {
                if (c != null) {
                    c.close();
                }
            }
        }
    }

    // ---- public tier -------------------------------------------------------

    @Test
    void aSharedRecruiterIsVisibleToEveryoneButOnlyReachableWhenSignedIn() throws Exception {
        createApplication(ALICE, "Acme");

        mvc.perform(get("/api/jobs/recruiters").header("Authorization", BOB))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fullName").value("Priya Shah"))
                .andExpect(jsonPath("$.content[0].email").value("priya@acme.example"))
                .andExpect(jsonPath("$.content[0].roles[0].category").value("JAVA_BACKEND"))
                .andExpect(jsonPath("$.content[0].mine").value(false));
        mvc.perform(get("/api/jobs/recruiters"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].fullName").value("Priya Shah"))
                .andExpect(jsonPath("$.content[0].contactLocked").value(true))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].phone").doesNotExist());
        mvc.perform(get("/api/jobs/recruiters/summary"))
                .andExpect(jsonPath("$.recruiters").value(1))
                .andExpect(jsonPath("$.categories[0].category").value("JAVA_BACKEND"));
    }

    @Test
    void twoUsersReportingTheSameRecruiterShareOneEntry() throws Exception {
        createApplication(ALICE, "Acme");
        // Same email, so the same person, found by Bob on a different job.
        mvc.perform(post("/api/jobs/recruiters").header("Authorization", BOB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Priya Shah","email":"PRIYA@acme.example",
                                 "roleTitle":"React Developer","company":"Acme"}"""))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/jobs/recruiters").header("Authorization", BOB))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].seenBy").value(2))
                .andExpect(jsonPath("$.content[0].roles", hasSize(2)));
        mvc.perform(get("/api/jobs/recruiters").param("category", "FRONTEND"))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/jobs/recruiters").param("category", "DATA"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void onlyTheContributorCanRemoveADirectoryEntry() throws Exception {
        createApplication(ALICE, "Acme");
        MvcResult listed = mvc.perform(get("/api/jobs/recruiters").header("Authorization", ALICE)).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(listed.getResponse().getContentAsString(), "$.content[0].id");

        mvc.perform(delete("/api/jobs/recruiters/" + id).header("Authorization", BOB))
                .andExpect(status().isForbidden());
        try (Connection c = TestPostgres.connectAsService()) {
            c.setAutoCommit(false);
            actAs(c, "bob");
            try (Statement sql = c.createStatement()) {
                assertThat(sql.executeUpdate("DELETE FROM recruiter")).isZero();
                assertThat(sql.executeUpdate("UPDATE recruiter SET email = 'spam@example.com'")).isZero();
            }
            c.rollback();
        }
        mvc.perform(delete("/api/jobs/recruiters/" + id).header("Authorization", ALICE))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/jobs/recruiters")).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void theDirectoryCannotBeWrittenAnonymouslyOrInSomeoneElsesName() throws Exception {
        try (Connection c = TestPostgres.connectAsService()) {
            c.setAutoCommit(false);
            String insert = "INSERT INTO recruiter (id, dedupe_key, full_name, contributed_by, created_at, updated_at) "
                    + "VALUES ('%s', '%s', 'Spam Bot', '%s', now(), now())";
            actAs(c, null);
            try (Statement sql = c.createStatement()) {
                assertThatThrownBy(() -> sql.executeUpdate(insert.formatted(UUID.randomUUID(), "k1", "alice")))
                        .hasMessageContaining("row-level security");
            }
            c.rollback();
            actAs(c, "bob");
            try (Statement sql = c.createStatement()) {
                assertThatThrownBy(() -> sql.executeUpdate(insert.formatted(UUID.randomUUID(), "k2", "alice")))
                        .hasMessageContaining("row-level security");
            }
            c.rollback();
        }
    }

    // ---- import tokens -----------------------------------------------------

    @Test
    void anImportTokenUploadsForItsOwnerAndDoesNothingElse() throws Exception {
        MvcResult created = mvc.perform(post("/api/jobs/tokens").header("Authorization", ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"My laptop\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.token");
        assertThat(token).startsWith("sjt_");

        mvc.perform(post("/api/jobs/import").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobs":[
                                  {"id":"li-1","company":"Globex","role":"Senior Java Engineer","status":"Applied",
                                   "stage":"Interview","where":"Austin, TX","workType":"Hybrid","appliedAt":"2026-11-01",
                                   "contactName":"Sam Lee","contactEmail":"sam@globex.example"},
                                  {"id":"li-2","company":"Initech","role":"QA Analyst","status":"Skipped"}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.ignored").value(1))
                .andExpect(jsonPath("$.recruitersShared").value(1));

        mvc.perform(get("/api/jobs/applications").header("Authorization", ALICE))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("INTERVIEW"))
                .andExpect(jsonPath("$[0].workType").value("HYBRID"));
        mvc.perform(get("/api/jobs/applications").header("Authorization", BOB))
                .andExpect(jsonPath("$", hasSize(0)));
        // The token is for uploading. It does not read, and it cannot mint more.
        mvc.perform(get("/api/jobs/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/tokens").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs/tokens").header("Authorization", ALICE))
                .andExpect(jsonPath("$[0].lastUsedAt").exists())
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void uploadingTheSameTrackerTwiceChangesNothingTheSecondTime() throws Exception {
        String body = """
                {"jobs":[{"id":"dice-9","company":"Globex","role":"Java Developer","status":"Applied"}]}""";
        mvc.perform(post("/api/jobs/import").header("Authorization", ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.created").value(1));
        mvc.perform(post("/api/jobs/import").header("Authorization", ALICE)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/jobs/applications").header("Authorization", ALICE))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void aRevokedOrUnknownTokenIsRefused() throws Exception {
        MvcResult created = mvc.perform(post("/api/jobs/tokens").header("Authorization", ALICE))
                .andExpect(status().isCreated()).andReturn();
        String json = created.getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(json, "$.token");
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        // Bob cannot revoke it, or even see that it exists.
        mvc.perform(delete("/api/jobs/tokens/" + id).header("Authorization", BOB))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/jobs/tokens/" + id).header("Authorization", ALICE))
                .andExpect(status().isNoContent());

        String body = "{\"jobs\":[]}";
        mvc.perform(post("/api/jobs/import").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/import").header("Authorization", "Bearer sjt_made-up")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/jobs/import").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenRowIsReadableOnlyByItsOwnerOrByPresentingItsHash() throws Exception {
        mvc.perform(post("/api/jobs/tokens").header("Authorization", ALICE)).andExpect(status().isCreated());

        try (Connection c = TestPostgres.connectAsService()) {
            c.setAutoCommit(false);
            actAs(c, "bob");
            assertThat(count(c, "import_token")).isZero();
            actAs(c, "alice");
            assertThat(count(c, "import_token")).isEqualTo(1);
            String hash;
            try (Statement sql = c.createStatement(); ResultSet rs = sql.executeQuery("select token_hash from import_token")) {
                rs.next();
                hash = rs.getString(1);
            }
            // Signed in as nobody, but holding the hash: that one row, no more.
            actAs(c, null);
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.token_hash', ?, true)")) {
                ps.setString(1, hash);
                ps.execute();
            }
            assertThat(count(c, "import_token")).isEqualTo(1);
            try (PreparedStatement ps = c.prepareStatement("select set_config('app.token_hash', ?, true)")) {
                ps.setString(1, "0".repeat(64));
                ps.execute();
            }
            assertThat(count(c, "import_token")).isZero();
            c.rollback();
        }
    }
}
