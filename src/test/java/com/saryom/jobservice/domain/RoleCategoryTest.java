package com.saryom.jobservice.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCategoryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Senior Java Developer            | JAVA_BACKEND",
            "Java/Spring Boot Engineer III    | JAVA_BACKEND",
            "Backend Engineer (Microservices) | JAVA_BACKEND",
            // Full stack wins over the languages it mentions.
            "Full Stack Java Developer        | FULL_STACK",
            "Fullstack Engineer - React/Node  | FULL_STACK",
            // "JavaScript" contains "Java"; it must not land in the Java bucket.
            "JavaScript Developer             | FRONTEND",
            "React Front-End Engineer         | FRONTEND",
            "UI Developer                     | FRONTEND",
            "Android Engineer                 | MOBILE",
            "iOS Developer                    | MOBILE",
            "Data Engineer - Snowflake        | DATA",
            "Machine Learning Engineer        | DATA",
            "DevOps Engineer                  | DEVOPS_CLOUD",
            "AWS Cloud Architect              | DEVOPS_CLOUD",
            "SDET                             | QA",
            "QA Automation Analyst            | QA",
            ".NET Developer                   | DOTNET",
            "C# Engineer                      | DOTNET",
            "Python Developer                 | PYTHON",
            "Engineering Manager              | MANAGEMENT",
            "Solutions Architect              | MANAGEMENT",
            "Business Development Rep         | OTHER",
    })
    void titlesFallIntoTheBucketAPersonWouldExpect(String title, RoleCategory expected) {
        assertThat(RoleCategory.of(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "'   '"})
    void aMissingTitleIsOtherRatherThanAnError(String title) {
        assertThat(RoleCategory.of(title)).isEqualTo(RoleCategory.OTHER);
        assertThat(RoleCategory.of(null)).isEqualTo(RoleCategory.OTHER);
    }
}
