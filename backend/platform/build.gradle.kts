dependencies {
    implementation(project(":common"))
    implementation(project(":identity"))
    implementation(project(":quiz"))
    implementation(project(":session"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
