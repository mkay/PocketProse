import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.singular.writer"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.singular.writer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Keep the git sha out of the APK, so an F-Droid rebuild of the tagged commit can
            // match the published binary byte-for-byte (the lesson RubberRing 0.3 paid for).
            vcsInfo { include = false }
        }
    }

    androidResources {
        // Emit a locale config from the res/values-* folders and point the manifest at it, so the
        // app shows up under Android's own Settings > Apps > Language. Without it the in-app picker
        // still works — AppCompatDelegate forwards to the framework on 13+ — but the system list has
        // no entry for this app, and the two places a per-app language can be set disagree about
        // whether one can be set at all.
        //
        // Generated rather than hand-written, so it cannot fall behind: adding res/values-fr puts
        // French in the system list by existing. The in-app picker's own list is still the explicit
        // `supported_locales` array, and deliberately so — see locales.xml for why asking the
        // resource table would offer eighty languages this app has never been translated into.
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        // For the version shown on the About screen. Generated from the constants above only —
        // unlike vcsInfo it embeds nothing build-specific, so it stays reproducible.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests {
            // The markdown/ tests read the real archive out of app/src/test/fixtures. Without this
            // they would run with the module root as the working directory on some Gradle versions
            // and with the project root on others; pinning it makes the fixture path one thing.
            all { it.workingDir = projectDir }
        }
    }
}

/**
 * Fails the build on a user-facing string typed straight into a composable.
 *
 * This exists because Android's own `HardcodedText` lint only reads XML layouts — it has nothing
 * to say about `Text("Cancel")`, which is the only way this app writes UI. Without a check of our
 * own, the next screen written would silently be English-only and nothing would complain.
 *
 * Deliberately narrow: it looks at the two constructs that actually put words in front of someone,
 * and leaves alone the places a bare string is legitimate (Compose animation labels, log messages,
 * file extensions) and every comment line. Something that slips past this is still caught by
 * reading the diff.
 */
val checkNoHardcodedUiStrings by tasks.registering {
    group = "verification"
    description = "Fails if a composable passes a literal where a string resource belongs."
    val sources = fileTree("src/main/java") { include("**/*.kt") }
    inputs.files(sources)
    // No real output; the up-to-date marker keeps repeat runs cheap.
    val stamp = layout.buildDirectory.file("tmp/hardcoded-ui-strings.ok")
    outputs.file(stamp)
    doLast {
        val patterns = listOf(
            Regex("\\bText\\(\\s*\""),
            Regex("\\bcontentDescription\\s*=\\s*\""),
        )
        // Comment lines are skipped. They cannot be an offence — a commented-out call is not
        // compiled — and without this the check fires on any KDoc that *discusses* the rule it
        // enforces, which it did the first time one was written.
        val offenders = sources.files.flatMap { file ->
            file.readLines().withIndex()
                .filterNot { (_, line) ->
                    val t = line.trimStart()
                    t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                }
                .filter { (_, line) -> patterns.any { it.containsMatchIn(line) } }
                .map { (i, line) -> "${file.relativeTo(projectDir)}:${i + 1}: ${line.trim()}" }
        }
        if (offenders.isNotEmpty()) {
            error(
                "Hardcoded UI strings — move these to res/values/strings.xml and read them " +
                    "with stringResource():\n" + offenders.joinToString("\n"),
            )
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

tasks.named("check") { dependsOn(checkNoHardcodedUiStrings) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Only for AppCompatDelegate.setApplicationLocales, which is how the language row in Settings
    // applies a choice. On Android 13+ that call forwards to the framework's per-app language, so
    // our picker and the one in Android's Settings are one value; below 33 AppCompat is what stores
    // the choice and re-applies it on launch — and that machinery hangs off AppCompatActivity,
    // which is why MainActivity extends it despite the UI being entirely Compose. minSdk is 26, so
    // that lower branch covers 26 to 32.
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    // Walking the folder the user granted us: SAF trees are a document-id tree, not a path, and
    // this wraps the DocumentsContract calls that walk one.
    implementation("androidx.documentfile:documentfile:1.0.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 2.9.x is compiled against API 36; 2.10+/2.11 require compileSdk 37.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Local JVM unit tests. Everything under markdown/ is pure Kotlin with no Android in it, which
    // is the point: a frontmatter round-trip bug is caught here rather than on a phone holding the
    // only copy of the archive.
    testImplementation("junit:junit:4.13.2")
}
