import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.gmazzo.buildconfig.generators.BuildConfigKotlinGenerator
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roomGradlePlugin)
    alias(libs.plugins.gradle.buildconfig)
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

var dbFileName ="beam_tube_debug.db"

compose.desktop {
    application {
        mainClass = "com.awebo.ytext.MainKt"

        buildTypes.release{
            dbFileName= "beam_tube.db"
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "YouTubeams"
            packageVersion = "1.0.0"
            val iconsRoot = project.file("desktop-icons")
            macOS {
                bundleID = "com.awebo.ytext"
                iconFile.set(iconsRoot.resolve("YTExt.icns"))
            }
        }
    }
}

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

    buildConfigField("DB_FILE_NAME", dbFileName)

    sourceSets.named("desktopMain") {
        useKotlinOutput() // resets `generator` back to default's Kotlin generator for JVM
        buildConfigField("DB_FILE_NAME", dbFileName)
    }
}