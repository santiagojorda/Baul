plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.santiagojorda.baul"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.santiagojorda.baul"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            // Los clientes de Google (google-api-client, google-http-client, google-auth-library,
            // guava, grpc-context, etc.) traen jars con licencias/manifiestos duplicados.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/LICENSE.md",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/NOTICE.md",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
}

kover {
    reports {
        filters {
            excludes {
                // UI visual (Compose) y bootstrap de la app: no se testea con unit tests, solo
                // infla el denominador del % de coverage sin aportar señal real.
                annotatedBy("androidx.compose.runtime.Composable")
                classes(
                    // Wildcard al final en las dos de acá abajo para agarrar también las clases
                    // sintéticas que genera un lambda adentro (setContent {} de Compose): esas
                    // quedan como MainActivity$onCreate$1, no matchean un nombre exacto.
                    "com.santiagojorda.baul.MainActivity*",
                    "com.santiagojorda.baul.BaulApplication",
                    "com.santiagojorda.baul.ui.theme.*",
                    "com.santiagojorda.baul.ui.navigation.*",
                    "com.santiagojorda.baul.ui.common.*",
                    "com.santiagojorda.baul.widget.*",
                    "com.santiagojorda.baul.ui.*.*Screen*",
                    // Hablan de verdad con WorkManager, HTTP a las APIs de Google, o Google
                    // Sign-In: no hay forma de fakearlos sin un seam de DI que hoy no existe. Se
                    // dejan afuera del % en vez de simular tests que no prueban nada real.
                    // UploadOutcomeResolver es la excepción: es la lógica pura que se extrajo de
                    // UploadWorker.doWork() justamente para poder testearla sin WorkManager de por
                    // medio (ver UploadOutcomeResolverTest), así que no entra en esta lista.
                    "com.santiagojorda.baul.work.MediaScanWorker",
                    "com.santiagojorda.baul.work.UploadWorkScheduler",
                    "com.santiagojorda.baul.work.UploadNotificationService*",
                    "com.santiagojorda.baul.work.UploadWorker*",
                    // Idem wildcard: GoogleAuthManager tiene withContext { } / lambdas internos
                    // que Kotlin compila como clases separadas (GoogleAuthManager$metodo$N).
                    "com.santiagojorda.baul.auth.GoogleAuthManager*",
                    "com.santiagojorda.baul.upload.GooglePhotosUploader",
                    "com.santiagojorda.baul.upload.DriveUploader",
                    "com.santiagojorda.baul.media.SyncCoordinator",
                    "com.santiagojorda.baul.media.MediaMetadataReader",
                    "com.santiagojorda.baul.media.MediaChangeObserver",
                    // FolderPlaceholder es pura interacción con ContentResolver/MediaStore real,
                    // sin lógica separable para testear; AllFilesAccess sí tiene test (más abajo).
                    "com.santiagojorda.baul.storage.FolderPlaceholder",
                )
            }
        }
        variant("debug") {
            xml {
                onCheck = false
            }
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    testImplementation(libs.work.testing)

    implementation(libs.glance.appwidget)

    implementation(libs.play.services.auth)

    implementation(libs.media3.transformer)
    implementation(libs.media3.common)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
