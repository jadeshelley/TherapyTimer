import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

configurations.create("jnaResolvable") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val jnaToAndroidAbi = mapOf(
    "android-aarch64" to "arm64-v8a",
    "android-arm" to "armeabi-v7a",
    "android-x86" to "x86",
    "android-x86-64" to "x86_64",
    "android-x86_64" to "x86_64"
)

val extractJnaNativeLibs = tasks.register("extractJnaNativeLibs") {
    group = "build"
    description = "Extract JNA native libs to src/main/jniLibs (for Vosk voice recognition)"
    doLast {
        val resolved = configurations.getByName("jnaResolvable").resolve()
        logger.lifecycle("jnaResolvable resolved ${resolved.size} file(s): ${resolved.map { it.name }}")
        val jniLibsDir = file("src/main/jniLibs")
        jniLibsDir.mkdirs()
        var extracted = 0
        val soPaths = mutableListOf<String>()
        // Prefer AAR (we use jna@aar for Android); AAR has jni/arm64-v8a/libjnidispatch.so etc.
        val jnaAar = resolved.find { it.name.startsWith("jna-") && it.name.endsWith(".aar") }
            ?: resolved.find { it.name.contains("jna") && it.extension == "aar" }
        if (jnaAar != null) {
            logger.lifecycle("Using JNA AAR: ${jnaAar.absolutePath}")
            ZipInputStream(FileInputStream(jnaAar)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val path = entry.name
                        if (path.startsWith("jni/") && path.endsWith(".so")) {
                            val relative = path.removePrefix("jni/")
                            val outFile = File(jniLibsDir, relative)
                            outFile.parentFile.mkdirs()
                            outFile.outputStream().use { zis.copyTo(it) }
                            logger.lifecycle("Extracted JNA: $relative")
                            extracted++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } else {
            val jnaJar = resolved.find { it.name.startsWith("jna-") && it.name.endsWith(".jar") }
                ?: resolved.find { it.name.contains("jna") && it.extension == "jar" }
                ?: error("JNA AAR or JAR not found in: ${resolved.map { it.absolutePath }}. Add jnaResolvable with jna@aar.")
            logger.lifecycle("Using JNA JAR: ${jnaJar.absolutePath}")
            ZipInputStream(FileInputStream(jnaJar)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val path = entry.name
                        if (path.contains("jnidispatch") || path.contains("libjnidispatch")) soPaths.add(path)
                        val match = Regex("""com/sun/jna/([^/]+)/libjnidispatch\.so""").find(path)
                        if (match != null) {
                            val jnaAbi = match.groupValues[1]
                            val androidAbi = jnaToAndroidAbi[jnaAbi]
                            if (androidAbi != null) {
                                File(jniLibsDir, androidAbi).mkdirs()
                                File(jniLibsDir, "$androidAbi/libjnidispatch.so").outputStream().use { zis.copyTo(it) }
                                logger.lifecycle("Extracted JNA: $androidAbi/libjnidispatch.so")
                                extracted++
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (extracted == 0) {
                val jnaVersion = libs.versions.jna.get().toString()
                val mavenAar = File(project.buildDir, "jna-${jnaVersion}.aar")
                if (!mavenAar.exists()) {
                    logger.lifecycle("No Android libs in JAR. Downloading JNA $jnaVersion AAR from Maven Central...")
                    mavenAar.parentFile.mkdirs()
                    URL("https://repo1.maven.org/maven2/net/java/dev/jna/jna/${jnaVersion}/jna-${jnaVersion}.aar").openStream().use { input ->
                        mavenAar.outputStream().use { input.copyTo(it) }
                    }
                }
                logger.lifecycle("Extracting from JNA AAR: ${mavenAar.absolutePath}")
                ZipInputStream(FileInputStream(mavenAar)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val path = entry.name
                            if (path.startsWith("jni/") && path.endsWith(".so")) {
                                val relative = path.removePrefix("jni/")
                                val outFile = File(jniLibsDir, relative)
                                outFile.parentFile.mkdirs()
                                outFile.outputStream().use { zis.copyTo(it) }
                                logger.lifecycle("Extracted JNA: $relative")
                                extracted++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                if (extracted == 0) error("No JNA native libs in AAR. Paths in JAR with jnidispatch: $soPaths")
            }
        }
        if (extracted == 0) error("No JNA native libs extracted from resolved artifact(s)")
    }
}

android {
    namespace = "com.example.therapytimer"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.therapytimer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

tasks.whenTaskAdded {
    if (name == "mergeDebugNativeLibs" || name == "mergeReleaseNativeLibs" ||
        name == "mergeDebugJniLibFolders" || name == "mergeReleaseJniLibFolders") {
        dependsOn(extractJnaNativeLibs)
    }
}

// Run extract before every build so you don't have to run it manually
afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(extractJnaNativeLibs) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.vosk.android) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    // Use JNA AAR only (not the JAR) to avoid duplicate classes; same version as in libs.versions.toml
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    add("jnaResolvable", "net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
}