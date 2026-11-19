import java.net.URI
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services) apply false
}

// Debug builds remain usable before the Firebase configuration is downloaded.
val firebaseConfigured = file("google-services.json").isFile
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")

val uploadStorePath = providers.environmentVariable("AXOO_UPLOAD_STORE_FILE").orNull
val uploadStorePassword = providers.environmentVariable("AXOO_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("AXOO_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("AXOO_UPLOAD_KEY_PASSWORD").orNull
val uploadSigningReady = listOf(
    uploadStorePath,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword,
).all { !it.isNullOrBlank() }

val localPropertiesText = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties"),
).asText.orElse("")

fun localValue(name: String): String = localPropertiesText.get().lineSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("$name=") }
    ?.substringAfter("=")
    ?.trim()
    .orEmpty()

fun httpsBaseUrl(property: String, default: String): String {
    val value = localValue(property).ifEmpty { default }
    val uri = URI(value)
    check(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null
    ) { "$property deve essere un URL HTTPS senza credenziali, query o frammento" }
    return uri.toASCIIString().trimEnd('/') + "/"
}

android {
    namespace = "com.axoo"
    compileSdk = 36

    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.axoo"
        minSdk = 24
        targetSdk = 36
        versionCode = 79
        versionName = "1.0.79"
        buildConfigField("boolean", "PUSH_CONFIGURED", firebaseConfigured.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val apiBaseUrl = httpsBaseUrl("axoo.api.url", "https://axoo-api-796038905520.europe-west9.run.app/api/v1/")
        val supabaseUrl = httpsBaseUrl("supabase.url", "https://cyrfznxopjeeidcmfgze.supabase.co/")
        val supabasePublishableKey = localValue("supabase.publishable.key")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
        val klipyApiKey = providers.environmentVariable("KLIPY_API_KEY")
            .orElse(providers.gradleProperty("klipyApiKey"))
            .orElse(providers.provider { localValue("klipy.api.key") })
            .get()
        check(klipyApiKey.isEmpty() || klipyApiKey.matches(Regex("[A-Za-z0-9_-]+"))) { "Chiave KLIPY non valida" }
        buildConfigField("String", "KLIPY_API_KEY", "\"$klipyApiKey\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                targets("rlottie_jni")
            }
        }

    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1+"
        }
    }

    signingConfigs {
        create("release") {
            if (uploadSigningReady) {
                storeFile = rootProject.file(requireNotNull(uploadStorePath))
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (uploadSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Material's AppCompat dependency is only used by dialog windows.
        // Our ComponentActivity and programmatic platform Views do not use
        // AppCompat widget substitution or its tint attributes.
        disable += "AppCompatCustomView"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val hiltJavaProcessor = configurations.resolvable("hiltJavaProcessor")

// The Hilt Java compile tasks inherit every KSP processor through AGP's default
// discovery path. Restrict them to Hilt's own processor to avoid loading Moshi's
// legacy KAPT processor (Moshi codegen itself continues to run through KSP).
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        if (name.startsWith("hiltJavaCompile")) {
            options.annotationProcessorPath = hiltJavaProcessor.get()
        }
    }
}


tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(firebaseConfigured) { "Aggiungi app/google-services.json per l'app Firebase com.axoo." }
        check(uploadSigningReady) {
            "Firma Release assente. Imposta AXOO_UPLOAD_STORE_FILE, " +
                "AXOO_UPLOAD_STORE_PASSWORD, AXOO_UPLOAD_KEY_ALIAS e AXOO_UPLOAD_KEY_PASSWORD."
        }
    }
}

dependencies {
    add(hiltJavaProcessor.name, libs.hilt.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.firebase.messaging)

    // Navigation
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.material)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    ksp(libs.moshi.codegen)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
