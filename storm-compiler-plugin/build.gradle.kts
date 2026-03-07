plugins {
    kotlin("jvm") version "2.0.21"
    `maven-publish`
}

group = "st.orm"
version = "1.10.0"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")
    testImplementation("dev.zacsweers.kctfork:core:0.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "st.orm"
            artifactId = "storm-compiler-plugin"
        }
    }
}
