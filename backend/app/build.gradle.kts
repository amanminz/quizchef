plugins {
    id("org.springframework.boot")
}

springBoot {
    buildInfo {
        properties {
            // The Gradle plugin default (project name = "app", the module,
            // not the product) would make a confusing `/actuator/info`.
            name = "QuizChef"
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":identity"))
    implementation(project(":user"))
    implementation(project(":quiz"))
    implementation(project(":session"))
    implementation(project(":media"))
    implementation(project(":security"))
    implementation(project(":websocket"))
    implementation(project(":platform"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql:42.7.13")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
}
