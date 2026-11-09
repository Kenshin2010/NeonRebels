import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.manroid.neonrebels"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.manroid.neonrebels"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.gdx.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
// Extract the native libraries into generated JNI directories, one directory per ABI.
val gdxNatives by configurations.creating
val gdxVersion = libs.versions.gdx.get()
dependencies {
    for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
        gdxNatives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
    }
}
abstract class ExtractGdxNatives : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archives: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileOperations: FileSystemOperations

    @TaskAction
    fun extract() {
        fileOperations.sync {
            into(destination)
            for (archive in archives.files) {
                val abi = archive.name.substringAfter("natives-").removeSuffix(".jar")
                from(archiveOperations.zipTree(archive)) {
                    include("*.so")
                    into(abi)
                }
            }
        }
    }
}
val extractGdxNatives by tasks.registering(ExtractGdxNatives::class) {
    archives.from(gdxNatives)
    destination.set(layout.buildDirectory.dir("generated/gdxJniLibs"))
}
// AGP 9 rejects Provider-backed source dirs; the Variant API also wires the task dependency.
androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(extractGdxNatives, ExtractGdxNatives::destination)
    }
}
