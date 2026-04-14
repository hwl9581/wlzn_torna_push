package com.wlzn.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.wlzn.config.TornaSettings
import com.wlzn.model.DocItem
import com.wlzn.model.DocPushData
import com.wlzn.parser.SpringApiParser
import com.wlzn.service.TornaApiClient
import com.wlzn.ui.PushDocDialog

class PushToTornaAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = TornaSettings.getInstance(project)

        if (settings.state.projects.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "请先在 Settings → Tools → Torna Sync 中配置至少一个项目",
                "Torna 配置缺失"
            )
            return
        }

        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        if (psiFile !is PsiClassOwner) {
            Messages.showWarningDialog(project, "仅支持 Java/Groovy 文件", "不支持的文件类型")
            return
        }

        val docItems: List<DocItem>
        var folderName: String

        if (editor != null) {
            val offset = editor.caretModel.offset
            val element = psiFile.findElementAt(offset)

            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            if (method != null) {
                val docItem = SpringApiParser.parseMethod(method)
                if (docItem == null) {
                    Messages.showWarningDialog(project, "未识别到 Spring MVC 映射注解", "无法解析")
                    return
                }
                docItems = listOf(docItem)
                folderName = settings.state.defaultFolder.ifBlank { SpringApiParser.getClassName(method) }
            } else {
                val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                    ?: psiFile.classes.firstOrNull()
                if (psiClass == null) {
                    Messages.showWarningDialog(project, "未找到类定义", "无法解析")
                    return
                }
                docItems = SpringApiParser.parseClass(psiClass)
                folderName = settings.state.defaultFolder.ifBlank { psiClass.name ?: "Default" }
            }
        } else {
            val psiClass = psiFile.classes.firstOrNull()
            if (psiClass == null) {
                Messages.showWarningDialog(project, "未找到类定义", "无法解析")
                return
            }
            docItems = SpringApiParser.parseClass(psiClass)
            folderName = settings.state.defaultFolder.ifBlank { psiClass.name ?: "Default" }
        }

        if (docItems.isEmpty()) {
            Messages.showInfoMessage(project, "没有找到可推送的接口", "提示")
            return
        }

        val dialog = PushDocDialog(project, docItems, folderName)
        if (!dialog.showAndGet()) return

        folderName = dialog.folderName
        val selectedProject = dialog.selectedProjectConfig
        if (selectedProject == null) {
            Messages.showWarningDialog(project, "未选择目标项目", "推送失败")
            return
        }

        val finalDocItems = if (dialog.apiName != null && docItems.size == 1) {
            listOf(docItems[0].copy(name = dialog.apiName!!))
        } else {
            docItems
        }

        val folder = DocItem(
            name = folderName,
            isFolder = true,
            items = finalDocItems
        )

        val pushData = DocPushData(
            apis = listOf(folder),
            author = settings.state.author,
            isReplace = dialog.isReplace
        )

        val serverUrl = settings.state.serverUrl
        val token = selectedProject.token

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "推送接口到 Torna...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "正在推送 ${docItems.size} 个接口到 [${selectedProject.name}]..."
                val result = TornaApiClient.pushDoc(serverUrl, token, pushData)

                ApplicationManager.getApplication().invokeLater {
                    result.fold(
                        onSuccess = {
                            Messages.showInfoMessage(
                                project,
                                "成功推送 ${docItems.size} 个接口到项目 [${selectedProject.name}]",
                                "推送成功"
                            )
                        },
                        onFailure = { ex ->
                            Messages.showErrorDialog(project, ex.message ?: "未知错误", "推送失败")
                        }
                    )
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.extension in SUPPORTED_EXTENSIONS
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("java", "groovy")
    }
}
