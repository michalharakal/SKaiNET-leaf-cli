plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.shadow)
}

group = "sk.ainet.apps"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Transformers BOM — also imports the engine BOM, so all sk.ainet:* and
    // sk.ainet.core:* / sk.ainet.transformers:* artifacts are aligned.
    implementation(platform(libs.skainet.transformers.bom))

    implementation(libs.skainet.transformers.api)
    implementation(libs.skainet.transformers.core)
    implementation(libs.skainet.transformers.providers)
    implementation(libs.skainet.transformers.inference.bert)

    // Engine classes that LeafEmbedder.kt references directly. Upstream
    // declares them as Gradle `implementation`, which is runtime-only for
    // consumers, so they must be on this module's compile classpath.
    // Versions are aligned by the transformers BOM above.
    implementation(libs.skainet.lang.core)
    implementation(libs.skainet.io.core)
    implementation(libs.skainet.io.safetensors)
    implementation(libs.skainet.backend.cpu)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.cli)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("leaf-cli")
    archiveClassifier.set("all")
    archiveVersion.set("")

    manifest {
        attributes(
            "Main-Class" to "sk.ainet.apps.leaf.cli.MainKt",
            "Add-Opens" to "java.base/jdk.internal.misc",
            "Multi-Release" to "true"
        )
    }

    mergeServiceFiles()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}
