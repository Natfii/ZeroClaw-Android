plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.gobley.cargo)
    alias(libs.plugins.gobley.uniffi)
    `maven-publish`
}

android {
    namespace = "com.zeroclaw.lib"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

cargo {
    packageDirectory = file("../zeroclaw-android/zeroclaw-ffi")
}

uniffi {
    generateFromLibrary {
        packageName = "com.zeroclaw.ffi"
    }
}

dependencies {
    api(libs.jna) {
        artifact {
            type = "aar"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.zeroclaw"
            artifactId = "zeroclaw-android"
            version = "0.0.10"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "LocalRepository"
            url = uri(layout.buildDirectory.dir("repo"))
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Natfii/ZeroClaw-Android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}
