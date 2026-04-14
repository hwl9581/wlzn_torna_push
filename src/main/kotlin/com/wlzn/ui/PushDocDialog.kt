package com.wlzn.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import com.wlzn.config.ProjectConfig
import com.wlzn.config.TornaSettings
import com.wlzn.model.DocItem
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.JTextField

class PushDocDialog(
    private val project: Project,
    private val docItems: List<DocItem>,
    private val defaultFolder: String
) : DialogWrapper(project, true) {

    private lateinit var folderField: JTextField
    private lateinit var previewArea: JTextArea
    private lateinit var projectComboBox: ComboBox<String>
    private var nameField: JTextField? = null
    private lateinit var replaceCheckbox: JCheckBox

    var folderName: String = defaultFolder
        private set

    var apiName: String? = null
        private set

    var isReplace: Boolean = false
        private set

    var selectedProjectConfig: ProjectConfig? = null
        private set

    init {
        title = "推送接口到 Torna"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val settings = TornaSettings.getInstance(project)
        val state = settings.state
        val preview = buildPreview()

        folderField = JTextField(defaultFolder)
        replaceCheckbox = JCheckBox("覆盖同名接口（勾选后会替换相同 URL 的已有文档）", false)
        previewArea = JTextArea(preview).apply {
            isEditable = false
            rows = 15
            columns = 60
            lineWrap = true
            wrapStyleWord = true
        }

        val projectNames = state.projects.map { it.name }.toTypedArray()
        projectComboBox = ComboBox(DefaultComboBoxModel(projectNames))
        val cachedIndex = state.selectedProjectIndex
        if (cachedIndex in state.projects.indices) {
            projectComboBox.selectedIndex = cachedIndex
        }

        return panel {
            group("推送配置") {
                row("服务地址:") {
                    label(state.serverUrl)
                }
                row("目标项目:") {
                    cell(projectComboBox).columns(COLUMNS_MEDIUM)
                        .comment("选择要推送到的 Torna 项目")
                }
                row("文件夹名称:") {
                    cell(folderField).columns(COLUMNS_MEDIUM)
                        .comment("接口将归属到此文件夹下")
                }
                if (docItems.size == 1) {
                    nameField = JTextField(docItems[0].name)
                    row("接口名称:") {
                        cell(nameField!!).columns(COLUMNS_MEDIUM)
                    }
                }
                row {
                    cell(replaceCheckbox)
                }
            }
            group("接口预览 (共 ${docItems.size} 个接口)") {
                row {
                    scrollCell(previewArea)
                        .align(Align.FILL)
                }
            }
        }
    }

    override fun doOKAction() {
        val settings = TornaSettings.getInstance(project)
        val state = settings.state

        folderName = folderField.text
        apiName = nameField?.text
        isReplace = replaceCheckbox.isSelected

        val selectedIdx = projectComboBox.selectedIndex
        if (selectedIdx in state.projects.indices) {
            selectedProjectConfig = state.projects[selectedIdx]
            state.selectedProjectIndex = selectedIdx
        }

        super.doOKAction()
    }

    private fun buildPreview(): String {
        val sb = StringBuilder()
        for (item in docItems) {
            sb.appendLine("${item.httpMethod} ${item.url}")
            sb.appendLine("  名称: ${item.name}")
            if (item.description.isNotEmpty()) {
                sb.appendLine("  描述: ${item.description}")
            }
            if (item.queryParams.isNotEmpty()) {
                sb.appendLine("  Query参数:")
                item.queryParams.forEach { p ->
                    sb.appendLine("    - ${p.name}: ${p.type}${if (p.required) " (必填)" else ""} ${p.description}")
                }
            }
            if (item.pathParams.isNotEmpty()) {
                sb.appendLine("  Path参数:")
                item.pathParams.forEach { p ->
                    sb.appendLine("    - ${p.name}: ${p.type} ${p.description}")
                }
            }
            if (item.requestParams.isNotEmpty()) {
                sb.appendLine("  请求体参数:")
                printParams(sb, item.requestParams, 4)
            }
            if (item.responseParams.isNotEmpty()) {
                sb.appendLine("  返回值参数:")
                printParams(sb, item.responseParams, 4)
            }
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    private fun printParams(sb: StringBuilder, params: List<com.wlzn.model.DocParam>, indent: Int) {
        val prefix = " ".repeat(indent)
        for (p in params) {
            sb.appendLine("$prefix- ${p.name}: ${p.type}${if (p.required) " (必填)" else ""} ${p.description}")
            if (p.children.isNotEmpty()) {
                printParams(sb, p.children, indent + 2)
            }
        }
    }
}
