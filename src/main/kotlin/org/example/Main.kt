package org.example

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackagePartProviderFactory
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProviderFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinClassFileDecompiler
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinClsStubBuilder
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
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
    val ktFile = (psiFile as? KtFile)!! // PsiJavaFile for java

    diagnostics(ktFile)

    goToDefinition(ktFile, 11, 8)

    // For some reason analysis api does not exit (a bug has been reported)
    exitProcess(0)
}

fun printPsiTree(ktFile: KtFile) {
    val rootNode = ktFile.node.psi

    printPsiNode(rootNode, 0)
}

fun printPsiNode(node: PsiElement, depth: Int) {
    // Print the node with indentation based on its depth in the tree
    val indent = "  ".repeat(depth)
    println("$indent${node.javaClass.simpleName}: ${node.text}")

    // Recursively print child nodes
    for (child in node.children) {
        printPsiNode(child, depth + 1)
    }
}

fun computeOffset(text: String, line: Int, column: Int): Int {
    return text.lineSequence().take(line - 1).sumOf { it.length + 1 } + column - 1
}

@OptIn(KaIdeApi::class)
fun goToDefinition(ktFile: KtFile, line: Int, column: Int) {
    val offset = computeOffset(ktFile.text, line, column)
    val element = ktFile.findElementAt(offset)!!
    val ref = element.parent as KtReferenceExpression
    val module = analyze(ref) {
        val a = ref.mainReference.resolveToSymbol()!! as KaCallableSymbol
        println(a.importableFqName)
        println(a.callableId?.asSingleFqName()?.asString())
        println(a.callableId?.packageName?.asString())
        println(a.callableId?.callableName?.asString())
        a.containingModule
    }

    val provider = KotlinPackagePartProviderFactory.getInstance(ktFile.project).createPackagePartProvider(module.contentScope)
    val names = provider.findPackageParts("kotlin.io").map { it.replace("/", ".")}
    names.forEach {
        val psiClass = JavaPsiFacade.getInstance(ktFile.project).findClass(it, module.contentScope)!!
        val fns = psiClass.methods.mapNotNull { it.name }
        if(fns.contains("println")) {
            println("!!! ${psiClass.containingFile.virtualFile.path}")

            println("${psiClass.containingFile::class.java}")

            // Kotlin decompiler test :)
            val decomp = KotlinClassFileDecompiler().createFileViewProvider(psiClass.containingFile.virtualFile, PsiManager.getInstance(ktFile.project), physical = true)
            val decompKtFile = decomp.getPsi(KotlinLanguage.INSTANCE) as KtFile
            println(decomp.content.get())
            println(decompKtFile::class.java)
            decompKtFile.declarations
                .forEach { println(it.name) }

        }
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
