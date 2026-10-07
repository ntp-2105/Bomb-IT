plugins {
    java
    id("org.springframework.boot")
}

description = "Spring Boot adapters and runtime for authoritative multiplayer rooms"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation(project(":game-core"))
    implementation(project(":protocol-java"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
