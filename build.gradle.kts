// The Android Gradle Plugin's tooling pulls these libraries onto the build classpath
// at versions with known vulnerabilities. They only run during the build and are not
// packaged into the app, but pinning patched versions here clears the Dependabot
// alerts and lets Dependabot keep them up to date.
buildscript {
    dependencies {
        constraints {
            classpath("org.apache.commons:commons-lang3:3.21.0")
            classpath("org.apache.httpcomponents:httpclient:4.5.14")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcutil-jdk18on:1.86")
            classpath("org.jdom:jdom2:2.0.6.1")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
