import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("multiplatform") version "1.4.31"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    application
}

group = "me.nanod"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClassName = "com.github.nanodeath.TestJSONKt"
}


kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
//
//        tasks.register<CreateStartScripts>("scripts") {
//
//            mainClassName = "com.github.nanodeath.TestJSONKt"
//            applicationName = "testjson"
//            outputDir = File(project.buildDir, "scripts")
//        }

        tasks {
            named<ShadowJar>("shadowJar") {
                archiveBaseName.set("app")
                mergeServiceFiles()
                manifest {
                    attributes(mapOf("Main-Class" to "com.github.nanodeath.TestJSONKt"))
                }
                from(kotlin.jvm().compilations.getByName("main").output)
                configurations = mutableListOf(kotlin.jvm().compilations.getByName("main").compileDependencyFiles as Configuration)
            }
        }
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    
    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
                implementation("org.jetbrains.kotlinx:kotlinx-io-jvm:0.1.16")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                val junitVersion = "5.6.0"
                implementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
                implementation("org.assertj:assertj-core:3.19.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
            }
        }
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        val nativeMain by getting
        val nativeTest by getting
    }
}
