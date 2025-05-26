import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.gmazzo.buildconfig.generators.BuildConfigKotlinGenerator
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roomGradlePlugin)
    alias(libs.plugins.gradle.buildconfig)
    alias(libs.plugins.kotlinxSerialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.yt.api)
            implementation(libs.kmp.logging)
            implementation(libs.slf4j)

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)

            implementation(libs.coil.compose)
            implementation(libs.coil.okhttp)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewModel)

            implementation(libs.reorderable)

            api(libs.androidx.datastore.preferences.core)
            api(libs.androidx.datastore.core.okio)

            implementation(libs.ktor.core)
            implementation(libs.ktor.cio)
            implementation(libs.ktor.cn)
            implementation(libs.ktor.json)

            implementation(libs.kotlin.json)

        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

android {
    namespace = "com.awebo.ytext"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.awebo.ytext"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
//    add("kspDesktop", libs.room.compiler)
//    add("kspJvmTest", project(":test-processor"))
//    add("kspJs", project(":test-processor"))
//    add("kspJsTest", project(":test-processor"))
//    add("kspAndroidNativeX64", project(":test-processor"))
//    add("kspAndroidNativeX64Test", project(":test-processor"))
//    add("kspAndroidNativeArm64", project(":test-processor"))
//    add("kspAndroidNativeArm64Test", project(":test-processor"))
//    add("kspLinuxX64", project(":test-processor"))
//    add("kspLinuxX64Test", project(":test-processor"))
//    add("kspMingwX64", project(":test-processor"))
//    add("kspMingwX64Test", project(":test-processor"))
}


compose.desktop {
    application {
        mainClass = "com.awebo.ytext.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "YouTubeams"
            packageVersion = "1.0.0"
            val iconsRoot = project.file("desktop-icons")
            macOS {
                bundleID = "com.awebo.ytext"
                iconFile.set(iconsRoot.resolve("YTExt.icns"))
            }
            buildTypes.release.proguard {
                version.set("7.3.2")
                configurationFiles.from(project.file("proguard-rules.pro"))
                isEnabled.set(false)
                obfuscate.set(false)
            }
        }
    }
}

val isReleaseMode = project.hasProperty("releaseBuild") && project.property("releaseBuild") == "true"

// Root build.gradle.kts
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun loadSecret(
    key: String,
    propsFile: File = localPropertiesFile,
    props: Properties = localProperties
): String {
    return if (propsFile.exists()) props.getProperty(key)
    else System.getenv(key)
}

val ytApiKey: String? = loadSecret("YT_DATA_API_V3_KEY")
val ytAppName: String? = loadSecret("YT_APP_NAME")
val googleAiApiKey: String? = loadSecret("GOOGLE_AI_STUDIO_API_KEY")
val appVersion: String = System.getenv("APP_VERSION") ?: "\"1.0.0\""

buildConfig {
    generator = object : BuildConfigKotlinGenerator() {
        override fun adaptSpec(spec: TypeSpec) = spec.toBuilder()
            .addAnnotation(
                AnnotationSpec.builder(ClassName.bestGuess("kotlin.js.JsName"))
                    .addMember("name = %S", spec.name!!)
                    .build()
            )
            .build()
    }

    buildConfigField("IS_RELEASE_MODE", isReleaseMode)
    buildConfigField("String", "YT_DATA_API_V3_KEY", ytApiKey)
    buildConfigField("String", "YT_APP_NAME", ytAppName)
    buildConfigField("String", "GOOGLE_AI_STUDIO_API_KEY", googleAiApiKey)
    buildConfigField("String", "APP_VERSION", appVersion)

    sourceSets.named("desktopMain") {
        useKotlinOutput() // resets `generator` back to default's Kotlin generator for JVM
        buildConfigField("IS_RELEASE_MODE", isReleaseMode)
        buildConfigField("String", "YT_API_KEY", ytApiKey)
        buildConfigField("String", "YT_APP_NAME", ytAppName)
        buildConfigField("String", "GOOGLE_AI_STUDIO_API_KEY", googleAiApiKey)
        buildConfigField("String", "APP_VERSION", appVersion)
    }
}