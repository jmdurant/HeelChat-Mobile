plugins {
    id("librechat.kmp.feature")
}

android {
    namespace = "com.garfiec.librechat.feature.heelcode"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // RemoteEvent (the SSE event type) lives in :core:network and is referenced by
            // the ViewModel + repository interface, so depend on it directly like :feature:settings does.
            implementation(project(":core:network"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        getByName("androidUnitTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}
