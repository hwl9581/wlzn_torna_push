package com.wlzn.parser

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

object ContextPathResolver {

    private val CONFIG_FILES = listOf("application.yml", "application.yaml", "application.properties")

    fun resolve(psiFile: PsiFile): String {
        val module = ModuleUtilCore.findModuleForFile(psiFile) ?: return ""
        val sourceRoots = ModuleRootManager.getInstance(module).sourceRoots

        for (root in sourceRoots) {
            for (configName in CONFIG_FILES) {
                val configFile = root.findChild(configName) ?: continue
                val contextPath = parseContextPath(configFile, configName)
                if (contextPath.isNotEmpty()) return contextPath
            }
        }
        return ""
    }

    private fun parseContextPath(file: VirtualFile, fileName: String): String {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        return if (fileName.endsWith(".properties")) {
            parseFromProperties(content)
        } else {
            parseFromYaml(content)
        }
    }

    private fun parseFromProperties(content: String): String {
        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("server.servlet.context-path")) {
                val value = trimmed.substringAfter("=").trim()
                return normalizeContextPath(value)
            }
        }
        return ""
    }

    private fun parseFromYaml(content: String): String {
        val lines = content.lines()
        var serverFound = false
        var serverIndent = -1
        var servletFound = false
        var servletIndent = -1

        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue

            val indent = line.length - line.trimStart().length
            val trimmed = line.trim()

            if (!serverFound) {
                if (trimmed == "server:" && indent == 0) {
                    serverFound = true
                    serverIndent = indent
                }
                continue
            }

            if (indent <= serverIndent && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                serverFound = false
                servletFound = false
                if (trimmed == "server:") {
                    serverFound = true
                    serverIndent = indent
                }
                continue
            }

            if (!servletFound) {
                if (trimmed == "servlet:" && indent > serverIndent) {
                    servletFound = true
                    servletIndent = indent
                }
                continue
            }

            if (indent <= servletIndent) {
                servletFound = false
                continue
            }

            if (trimmed.startsWith("context-path:")) {
                val value = trimmed.substringAfter(":").trim().removeSurrounding("'").removeSurrounding("\"")
                return normalizeContextPath(value)
            }
        }
        return ""
    }

    private fun normalizeContextPath(path: String): String {
        if (path.isBlank()) return ""
        val p = path.removeSurrounding("'").removeSurrounding("\"").trim()
        return if (p.startsWith("/")) p.trimEnd('/') else "/$p".trimEnd('/')
    }
}
