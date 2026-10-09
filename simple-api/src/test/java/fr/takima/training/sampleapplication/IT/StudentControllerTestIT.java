package fr.takima.training.sampleapplication.IT;

import fr.takima.training.simpleapi.SimpleApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@SpringBootTest(classes = {SimpleApiApplication.class})
class StudentControllerTestIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Sql({"/InsertData.sql"})
    void testGetStudentById() throws Exception {
        mockMvc.perform(get("/students/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id", equalTo(6)))
                .andExpect(jsonPath("firstname", equalTo("Jeanne")))
                .andExpect(jsonPath("lastname", equalTo("Ausecours")))
                .andExpect(jsonPath("department.id", equalTo(4)))
                .andExpect(jsonPath("department.name", equalTo("GC")));
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testGetNonExistingStudentById() throws Exception {
        mockMvc.perform(get("/students/666"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testPostStudent() throws Exception {
        String body = """
                {
                    "firstname": "Didier",
                    "lastname": "Deschamps",
                    "department": {
                        "id": 4,
                        "name": "GC"
                    }
                }
                """;
        mockMvc.perform(post("/students")
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists("location"));
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testPostStudentWithoutLastName() throws Exception {
        String body = """
                {
                    "firstname": "Didier",
                    "department": {
                        "id": 4,
                        "name": "GC"
                    }
                }
                """;
        mockMvc.perform(post("/students")
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testPostStudentWithoutDepartment() throws Exception {
        String body = """
                {
                    "lastname": "Didier"
                }
                """;
        mockMvc.perform(post("/students")
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testUpdateStudent() throws Exception {
        mockMvc.perform(get("/students/11"))
                .andExpect(jsonPath("id", equalTo(11)))
                .andExpect(jsonPath("firstname", equalTo("Sophie")))
                .andExpect(jsonPath("lastname", equalTo("Schutt")))
                .andExpect(jsonPath("department.id", equalTo(9)))
                .andExpect(jsonPath("department.name", equalTo("PERF-I")));

        String body = """
                {
                    "firstname": "Francis",
                    "lastname": "Huster",
                    "department": {
                        "id": 1,
                        "name": "ASI"
                    }
                }
                """;
        mockMvc.perform(put("/students/11")
                        .content(body)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id", equalTo(11)))
                .andExpect(jsonPath("firstname", equalTo("Francis")))
                .andExpect(jsonPath("lastname", equalTo("Huster")))
                .andExpect(jsonPath("department.id", equalTo(1)))
                .andExpect(jsonPath("department.name", equalTo("ASI")));
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testDeleteStudent() throws Exception {
        mockMvc.perform(get("/students/1"))
                .andExpect(jsonPath("id", equalTo(1)));
        mockMvc.perform(delete("/students/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/students/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testPostCannotOverwriteStudentUsingBodyId() throws Exception {
        String original = mockMvc.perform(get("/students/1"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 1, "firstname": "New", "lastname": "Student",
                                 "department": {"id": 4, "name": "GC"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("location", not(endsWith("/1"))));
        mockMvc.perform(get("/students/1"))
                .andExpect(content().json(original));
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testUpdateUsesPathIdInsteadOfBodyId() throws Exception {
        String original = mockMvc.perform(get("/students/1"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(put("/students/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": 1, "firstname": "Updated", "lastname": "Student",
                                 "department": {"id": 4, "name": "GC"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id", equalTo(11)));
        mockMvc.perform(get("/students/1"))
                .andExpect(content().json(original));
        mockMvc.perform(get("/students/11"))
                .andExpect(jsonPath("firstname", equalTo("Updated")));
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testInvalidUpdateReturnsBadRequestWithoutChangingStudent() throws Exception {
        String original = mockMvc.perform(get("/students/11"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(put("/students/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstname": "Invalid", "department": {"id": 4}}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/students/11"))
                .andExpect(content().json(original));
    }

    @Test
    @Sql({"/InsertData.sql"})
    void testControllersDoNotAllowUntrustedOrigins() throws Exception {
        for (String path : new String[]{"/", "/students", "/departments"}) {
            mockMvc.perform(get(path).header("Origin", "https://untrusted.example"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mockMvc.perform(options(path)
                            .header("Origin", "https://untrusted.example")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }
}
