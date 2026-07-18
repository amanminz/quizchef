dependencies {
    implementation(project(":common"))
    implementation(project(":security"))
    implementation(project(":session"))
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("io.micrometer:micrometer-core")
    implementation("org.springframework.boot:spring-boot-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
