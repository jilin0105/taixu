plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("src/main/java")
        }
        test {
            kotlin.srcDir("src/test/java")
        }
    }
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":core:model"))
    testImplementation(libs.junit)
}
