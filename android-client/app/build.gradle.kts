plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.smartruble.mobile"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.smartruble.mobile"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0"
  // Android Emulator reaches the development host through 10.0.2.2.
  buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080/api/v1/\"")
 }
 buildTypes {
  release {
   buildConfigField("String", "API_URL", "\"https://api.example.com/api/v1/\"")
  }
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.04.01"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.google.code.gson:gson:2.12.1")
}
