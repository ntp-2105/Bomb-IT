# Gradle wrapper

Generate and commit the Gradle Wrapper scripts and JAR with Gradle 9.8.0 as the first Java build setup task. This environment has no Gradle installation, so the wrapper binary was not fabricated. Do not rely on a locally installed Gradle in CI; after generation, contributors and CI should use the checked-in `gradlew`/`gradlew.bat`.
