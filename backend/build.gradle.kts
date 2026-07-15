import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.withType

plugins {
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "io.quizchef"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Spring resolves @PathVariable/@RequestParam names via reflection;
    // only the boot plugin adds -parameters by itself, library modules
    // with controllers need it too.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    dependencies {
        add("compileOnly", "org.projectlombok:lombok:1.18.36")
        add("annotationProcessor", "org.projectlombok:lombok:1.18.36")

        add("compileOnly", "org.mapstruct:mapstruct:1.6.3")
        add("annotationProcessor", "org.mapstruct:mapstruct-processor:1.6.3")
        add("annotationProcessor", "org.projectlombok:lombok-mapstruct-binding:0.2.0")

        add("testImplementation", platform("org.junit:junit-bom:5.11.4"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testImplementation", "org.mockito:mockito-junit-jupiter:5.14.2")
        add("testImplementation", platform("org.testcontainers:testcontainers-bom:1.21.3"))
        add("testImplementation", "org.testcontainers:junit-jupiter")
        add("testImplementation", "com.tngtech.archunit:archunit-junit5:1.4.0")
    }
}
