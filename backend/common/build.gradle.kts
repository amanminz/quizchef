dependencies {
    api("org.springframework:spring-context")
    implementation("org.springframework:spring-webmvc")
    api("jakarta.persistence:jakarta.persistence-api")
    api("org.springframework.data:spring-data-commons")
    implementation("org.springframework:spring-tx")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.slf4j:slf4j-api")
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
