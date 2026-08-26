plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // 음악 모듈 테스트가 JUnit5(Jupiter)를 사용하므로 Android용 JUnit5 플러그인 적용
    id("de.mannodermaus.android-junit5")
}

android {
    namespace = "com.pknu.running"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pknu.running"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 데모 화면 (Activity + Lifecycle)
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")

    // GPS (Fused Location Provider)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Unit tests — 러닝 모듈(JUnit4 + Truth)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")

    // Unit tests — 음악 모듈(JUnit5/Jupiter)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    // JUnit4로 작성된 러닝 테스트도 Platform에서 함께 실행되도록 vintage 엔진 추가
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}
