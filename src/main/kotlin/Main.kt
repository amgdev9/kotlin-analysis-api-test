package org.example

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.Path

fun main() {
    var mainSourceModule: KaSourceModule? = null
    val session = buildStandaloneAnalysisAPISession {
        buildKtModuleProvider {
            platform = JvmPlatforms.defaultJvmPlatform

            // Kotlin standard library
            val stdlibModule = addModule(
                buildKtLibraryModule {
                    addBinaryRoot(Path("/home/amg/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.0.20/7388d355f7cceb002cd387ccb7ab3850e4e0a07f/kotlin-stdlib-2.0.20.jar"))
                    platform = JvmPlatforms.defaultJvmPlatform
                    libraryName = "stdlib"
                }
            )

            // JDK so standard library works for java files
            val jdkModule = addModule(
                buildKtSdkModule {
                    platform = JvmPlatforms.defaultJvmPlatform
                    addBinaryRootsFromJdkHome(Path("/usr/lib/jvm/java-11-openjdk-amd64"), false)
                    libraryName = "JDK"
                }
            )

            mainSourceModule = addModule(buildKtSourceModule {
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

    val psiFile = session.modulesWithFiles[mainSourceModule]?.find {
        it.name == "Main.kt"
    }
    val ktFile = (psiFile as? KtFile)!! // PsiJavaFile for java

    diagnostics(ktFile)
    goToDefinition(ktFile, 3, 29)
}

fun computeOffset(text: String, line: Int, column: Int): Int {
    return text.lineSequence().take(line - 1).sumOf { it.length + 1 } + column - 1
}

fun goToDefinition(ktFile: KtFile, line: Int, column: Int) {
    val offset = computeOffset(ktFile.text, line, column)
    analyze(ktFile) {
        val ref = ktFile.findReferenceAt(offset)!!
        val file = ref.resolve()!!.containingFile
        println("GO TO DEFINITION:")
        println("REF: $ref")
        println("${file.containingDirectory}/${file.containingFile.name}")
    }
}

fun diagnostics(ktFile: KtFile) {
    analyze(ktFile) {
        val diagnostics = ktFile.collectDiagnostics(KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
        println("Diagnostics: ${diagnostics.size}")
        diagnostics.forEach {
            println("${it.severity}: ${it.defaultMessage} | range: ${it.textRanges}")
        }
    }
}
