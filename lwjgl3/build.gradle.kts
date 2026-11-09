import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
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
    implementation(project(":core"))
    implementation(libs.gdx.lwjgl3)
    runtimeOnly("com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:natives-desktop")
}
// Share Android assets; libGDX resolves internal files from the classpath on desktop.
sourceSets { main { resources.srcDir(rootProject.file("app/src/main/assets")) } }
application { mainClass.set("com.manroid.neonrebels.lwjgl3.DesktopLauncher") }
tasks.named<JavaExec>("run") {
    if (System.getProperty("os.name").contains("Mac")) jvmArgs("-XstartOnFirstThread")
}
