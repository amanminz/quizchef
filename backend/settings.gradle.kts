plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "quizchef-backend"

include(
    "app",
    "common",
    "identity",
    "user",
    "quiz",
    "session",
    "media",
    "security",
    "websocket",
    "platform"
)
