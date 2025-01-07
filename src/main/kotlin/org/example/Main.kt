package org.example

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.CoreCommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentWriteAccessGuard
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.DocumentCommitProcessor
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.platform.modification.KaElementModificationType
import org.jetbrains.kotlin.analysis.api.platform.modification.KaSourceModificationService
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtLibraryModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import kotlin.io.path.Path
import kotlin.system.exitProcess

class WriteAccessGuard: DocumentWriteAccessGuard() {
    override fun isWritable(p0: Document): Result {
        return success()
    }
}

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
                    addBinaryRootsFromJdkHome(Path("/usr/lib/jvm/java-21-openjdk"), false)
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

    CoreApplicationEnvironment.registerExtensionPoint(session.application.extensionArea, DocumentWriteAccessGuard.EP_NAME, WriteAccessGuard::class.java)
    println("PROJECT SCANNED")

    val psiFile = session.modulesWithFiles[mainSourceModule]?.find {
        it.name == "Main.kt"
    }
    val ktFile = (psiFile as? KtFile)!! // PsiJavaFile for java

    diagnostics(ktFile)

    // Edit
    val cmd = session.application.getService(CommandProcessor::class.java)
    val psiDocMgr = PsiDocumentManager.getInstance(session.project)
    val doc = psiDocMgr.getDocument(ktFile)!!

    // commitDocument is no-op unless document listeners are setup
    (psiDocMgr as PsiDocumentManagerBase).disableBackgroundCommit(Disposable {  })  // We need to set this because EDT thread is not set (and can't be set)
    doc.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            super.documentChanged(event)

            psiDocMgr.documentChanged(event)
        }
    })

    cmd.executeCommand(session.project, {
        session.application.runWriteAction {
            try {
                doc.replaceString(computeOffset(ktFile.text, 13, 0), computeOffset(ktFile.text, 13, 0), "fun a() {}")
                println("UNCOMMITED: ${psiDocMgr.uncommittedDocuments.size}")   // This reports 1 after registering listeners
                psiDocMgr.commitDocument(doc)   // This should commit the document to the ktfile, but it doesnt
                println("UNCOMMITED: ${psiDocMgr.uncommittedDocuments.size}")
                //ktFile.onContentReload()    // This sets the changed in Document to the KtFile

                val sms =
                    KaSourceModificationService.getInstance(session.project)          // This service is implemented by FIR frontend
                sms.handleElementModification(ktFile, KaElementModificationType.Unknown)    // This invalidates caches
            } catch (e: Exception) {
                println(e)  // But an exception is thrown because there is no DocumentCommitProcessor, but we can't register it before it is being requested :) so that means editing the ktfile is not supported
            }
        }
    }, "sample", null)

    session.application.runReadAction {
        println(ktFile.viewProvider.document.text)
        println(ktFile.text)

        diagnostics(ktFile)
    }

    //goToDefinition(ktFile, 3, 29)

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

fun goToDefinition(ktFile: KtFile, line: Int, column: Int) {
    val offset = computeOffset(ktFile.text, line, column)
    val ref = ktFile.findReferenceAt(offset)!!
    val element = ref.resolve()!!
    val file = element.containingFile
    println("GO TO DEFINITION:")
    println("REF: $ref")
    println("${file.containingDirectory}/${file.containingFile.name}")
    println(element.textRange)
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
