package org.example

import com.intellij.mock.MockProject
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.standalone.KtAlwaysAccessibleLifetimeTokenProvider
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.load.kotlin.getJvmModuleNameForDeserializedDescriptor
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.Path

@OptIn(KtAnalysisApiInternals::class)
fun main() {
    val session = buildStandaloneAnalysisAPISession {
        buildKtModuleProvider {
            platform = JvmPlatforms.defaultJvmPlatform
            (project as MockProject).registerService(
                KtLifetimeTokenProvider::class.java,
                KtAlwaysAccessibleLifetimeTokenProvider::class.java
            )

            val stdlibModule = addModule(
                buildKtLibraryModule {
                    addBinaryRoot(Path("/home/amg/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.20/7388d355f7cceb002cd387ccb7ab3850e4e0a07f/kotlin-stdlib-2.0.20.jar"))
                    platform = JvmPlatforms.defaultJvmPlatform
                    libraryName = "stdlib"
                }
            )

            val jdkModule = addModule(
                buildKtSdkModule {
                    platform = JvmPlatforms.defaultJvmPlatform
                    addBinaryRootsFromJdkHome(Path("/usr/lib/jvm/java-11-openjdk-amd64"), false)
                    sdkName = "JDK"
                }
            )

            addModule(buildKtSourceModule {
                moduleName = "MyModule"
                platform = JvmPlatforms.defaultJvmPlatform

                addSourceRoots(listOf(
                    Path("testproject")
                ))

                addRegularDependency(stdlibModule)
                addRegularDependency(jdkModule)
            })
        }
    }

    println("PROJECT SCANNED")

    // Get kotlin file
    val path = Path("testproject/Main.kt")
    val fs = StandardFileSystems.local()
    val psiManager = PsiManager.getInstance(session.project)
    val vFile = fs.findFileByPath(path.toString())
    val psiFile = vFile?.let(psiManager::findFile)
    val ktFile = (psiFile as? KtFile)!!

    // Get errors and warnings
    analyze(ktFile) {
        val diagnostics = ktFile.collectDiagnosticsForFile(KtDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
        println("Diagnostics: ${diagnostics.size}")
        diagnostics.forEach {
            println("${it.severity}: ${it.defaultMessage} | range: ${it.textRanges}")
        }
    }
}
