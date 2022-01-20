package com.dpforge.manifestguard

import name.fraser.neil.plaintext.diff_match_patch as DiffMatchPatch
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.LinkedList
import javax.inject.Inject

abstract class CompareMergedManifestTask @Inject constructor(
    private val args: Args,
) : DefaultTask() {

    private val mergedManifestFile: File = args.mergedManifestFile
    private val referenceManifestFile: File = args.referenceManifestFile

    @TaskAction
    fun execute() {
        args.htmlDiffFile.delete()

        logger.debug("Merged AndroidManifest.xml: $args.mergedManifestFile")
        logger.debug("Reference AndroidManifest.xml: $referenceManifestFile")

        if (!referenceManifestFile.exists()) {
            logger.info("Reference AndroidManifest.xml does not exist. Just creating it.")
            mergedManifestFile.copyTo(referenceManifestFile)
        } else {
            compareManifests()
        }
    }

    private fun compareManifests() {
        val dmp = DiffMatchPatch()
        val diffList = dmp.diff_main(
            referenceManifestFile.readText(),
            mergedManifestFile.readText(),
        )
        if (diffList.hasOnlyEqual) {
            logger.info("AndroidManifests are the same")
        } else {
            logger.error("AndroidManifests are different")
            writeHtmlReport(dmp, diffList)
            throw GradleException(
                "AndroidManifest.xml has changed. " +
                        "Refer to '${args.htmlDiffFile.absolutePath}' for more details"
            )
        }
    }

    private fun writeHtmlReport(dmp: DiffMatchPatch, diffList: LinkedList<DiffMatchPatch.Diff>) {
        dmp.diff_cleanupSemantic(diffList)
        args.htmlDiffFile.ensureParentsExist()
        val html = buildString {
            append("<pre>")
            append(dmp.diff_prettyHtml(diffList))
            append("</pre>")
        }
        args.htmlDiffFile.writeText(html)
    }

    private val List<DiffMatchPatch.Diff>.hasOnlyEqual: Boolean
        get() = all { it.operation == DiffMatchPatch.Operation.EQUAL }

    private fun File.ensureParentsExist() {
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                error("Failed to create dirs for file '${this}'")
            }
        }
    }

    class Args(
        val mergedManifestFile: File,
        val referenceManifestFile: File,
        val htmlDiffFile: File,
    )

}