plugins {
    java
}

description = "Java wire DTOs and JSON mapping for the shared network contract"

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("com.networknt:json-schema-validator:3.0.8")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    from("../contracts/game/state.schema.json") { into("contracts/game") }
    from("../contracts/websocket/messages.schema.json") { into("contracts/websocket") }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("contractRoot", rootProject.file("contracts").absolutePath)
}
