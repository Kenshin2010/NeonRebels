import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}
dependencies {
    api(libs.gdx.core)
    testImplementation(libs.junit)
}
tasks.register<JavaExec>("modelTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.manroid.neonrebels.model.ModelChecks")
    enableAssertions = true
}
