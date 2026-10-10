import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Android Lint, which AGP resolves separately from the normal configurations, depends on
// these libraries at versions with known vulnerabilities. They only run during the build
// and are not packaged into the app. A component metadata rule applies to every resolution
// in the project, lint's included, so it raises them to the patched versions declared in
// the version catalog, where Dependabot can keep them up to date.
val patchedVersions: Map<String, String> = listOf(
    libs.patched.bcpkix,
    libs.patched.bcprov,
    libs.patched.bcutil,
    libs.patched.commons.lang3,
    libs.patched.httpclient,
    libs.patched.jdom2,
    libs.patched.jose4j,
).map { it.get() }.associate { it.module.toString() to it.versionConstraint.requiredVersion }

allprojects {
    dependencies.components.all(PatchedVersionsRule::class.java) {
        params(patchedVersions)
    }
}

@CacheableRule
abstract class PatchedVersionsRule @Inject constructor(
    private val patchedVersions: Map<String, String>,
) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                forEach { dependency ->
                    patchedVersions["${dependency.group}:${dependency.name}"]?.let { version ->
                        dependency.version { require(version) }
                    }
                }
            }
        }
    }
}
