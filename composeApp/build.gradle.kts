import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // --- YENİ EKLENEN PROFESYONEL İKON PAKETİ ---
            implementation(compose.materialIconsExtended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.badiadam.aserver.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            // İŞTE EKSİK OLAN O SİHİRLİ SATIR (Telemetri radarı ve kalkan için şart):
            modules("java.management")

            packageName = "AServer"
            packageVersion = "1.0.0"

            // OTOMATİK KISAYOL, BAŞLAT MENÜSÜ VE İKON AYARLARI:
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icon.ico"))
                shortcut = true
                menuGroup = "AServer"
            }
        }
    }
}