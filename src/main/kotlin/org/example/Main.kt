package org.example

import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.Path
import kotlin.system.exitProcess

fun main() {
    var mainSourceModule: KaSourceModule? = null
    val javaPlatform = JvmPlatforms.jvmPlatformByTargetVersion(JvmTarget.JVM_21)
    val session = buildStandaloneAnalysisAPISession {
        buildKtModuleProvider {
            platform = javaPlatform

            // Kotlin standard library
            val stdlibModule = addModule(
                buildKtLibraryModule {
                    addBinaryRoot(Path("/home/amg/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.1.0/85f8b81009cda5890e54ba67d64b5e599c645020/kotlin-stdlib-2.1.0.jar"))
                    platform = javaPlatform
                    libraryName = "stdlib"
                }
            )

            // JDK so standard library works for java files
            val jdkModule = addModule(
                buildKtSdkModule {
                    platform = javaPlatform
                    addBinaryRootsFromJdkHome(Path("/usr/lib/jvm/java-21-openjdk"), false)
                    libraryName = "JDK"
                }
            )

            mainSourceModule = addModule(buildKtSourceModule {
                moduleName = "MyModule"
                platform = javaPlatform

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
    val ktFile = (psiFile as? KtFile)!!

    diagnostics(ktFile)

    goToDefinition(ktFile, 11, 8)

    // For some reason analysis api does not exit
    exitProcess(0)
}

fun computeOffset(text: String, line: Int, column: Int): Int {
    return text.lineSequence().take(line - 1).sumOf { it.length + 1 } + column - 1
}

@OptIn(KaIdeApi::class)
fun goToDefinition(ktFile: KtFile, line: Int, column: Int) {
    val offset = computeOffset(ktFile.text, line, column)
    analyze(ktFile) {
        val ref = ktFile.findReferenceAt(offset) as KtReference
        val symbol = ref.resolveToSymbol()!!
        println("Symbol $symbol ${symbol.containingModule} ${symbol.importableFqName}")
        val fileSymbol = symbol.containingFile!!
        println("FileSymbol $fileSymbol")
        val file = fileSymbol.psi as KtFile
        println("Resolve reference:")
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
