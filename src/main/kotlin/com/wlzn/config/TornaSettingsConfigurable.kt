package com.wlzn.config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.table.AbstractTableModel

class TornaSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var serverUrlField: JTextField
    private lateinit var authorField: JTextField
    private lateinit var defaultFolderField: JTextField
    private val projectRows = mutableListOf<ProjectConfig>()
    private lateinit var tableModel: ProjectTableModel
    private lateinit var table: JBTable

    override fun getDisplayName(): String = "Torna Sync"

    override fun createComponent(): JComponent {
        val settings = TornaSettings.getInstance(project)
        val state = settings.state

        serverUrlField = JTextField(state.serverUrl)
        authorField = JTextField(state.author)
        defaultFolderField = JTextField(state.defaultFolder)

        projectRows.clear()
        projectRows.addAll(state.projects.map { ProjectConfig(it.name, it.token) })

        tableModel = ProjectTableModel(projectRows)
        table = JBTable(tableModel).apply {
            setShowGrid(true)
            columnModel.getColumn(0).preferredWidth = 200
            columnModel.getColumn(1).preferredWidth = 400
        }

        val tablePanel = ToolbarDecorator.createDecorator(table)
            .setAddAction {
                val dialog = ProjectEditDialog(null)
                if (dialog.showAndGet()) {
                    projectRows.add(ProjectConfig(dialog.projectName, dialog.projectToken))
                    tableModel.fireTableRowsInserted(projectRows.size - 1, projectRows.size - 1)
                }
            }
            .setEditAction {
                val row = table.selectedRow
                if (row >= 0) {
                    val config = projectRows[row]
                    val dialog = ProjectEditDialog(config)
                    if (dialog.showAndGet()) {
                        config.name = dialog.projectName
                        config.token = dialog.projectToken
                        tableModel.fireTableRowsUpdated(row, row)
                    }
                }
            }
            .setRemoveAction {
                val row = table.selectedRow
                if (row >= 0) {
                    val name = projectRows[row].name
                    val confirm = Messages.showYesNoDialog(
                        project,
                        "确定要删除项目「$name」吗？",
                        "删除项目",
                        Messages.getQuestionIcon()
                    )
                    if (confirm == Messages.YES) {
                        projectRows.removeAt(row)
                        tableModel.fireTableRowsDeleted(row, row)
                    }
                }
            }
            .disableUpDownActions()
            .createPanel()

        return panel {
            group("Torna 服务配置") {
                row("服务地址:") {
                    cell(serverUrlField).columns(COLUMNS_LARGE)
                        .comment("例如: http://localhost:7700")
                }
            }
            group("项目列表") {
                row {
                    cell(tablePanel).align(Align.FILL)
                }.comment("双击或点击编辑按钮修改项目配置，Token 在 Torna 项目的 OpenAPI 页面获取")
            }
            group("默认值") {
                row("作者:") {
                    cell(authorField).columns(COLUMNS_MEDIUM)
                }
                row("默认文件夹:") {
                    cell(defaultFolderField).columns(COLUMNS_MEDIUM)
                        .comment("接口归属的文件夹名称，留空则使用 Controller 类名")
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val state = TornaSettings.getInstance(project).state
        if (serverUrlField.text != state.serverUrl ||
            authorField.text != state.author ||
            defaultFolderField.text != state.defaultFolder
        ) return true

        if (projectRows.size != state.projects.size) return true
        return projectRows.zip(state.projects).any { (a, b) -> a.name != b.name || a.token != b.token }
    }

    override fun apply() {
        val settings = TornaSettings.getInstance(project)
        val newProjects = projectRows.map { ProjectConfig(it.name, it.token) }.toMutableList()
        val oldIndex = settings.state.selectedProjectIndex
        val selectedIndex = if (oldIndex in newProjects.indices) oldIndex else 0
        settings.loadState(
            TornaSettings.State(
                serverUrl = serverUrlField.text,
                projects = newProjects,
                selectedProjectIndex = selectedIndex,
                author = authorField.text,
                defaultFolder = defaultFolderField.text
            )
        )
    }

    override fun reset() {
        val state = TornaSettings.getInstance(project).state
        serverUrlField.text = state.serverUrl
        authorField.text = state.author
        defaultFolderField.text = state.defaultFolder
        projectRows.clear()
        projectRows.addAll(state.projects.map { ProjectConfig(it.name, it.token) })
        tableModel.fireTableDataChanged()
    }

    private class ProjectTableModel(
        private val rows: MutableList<ProjectConfig>
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("项目名称", "Token")

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 2
        override fun getColumnName(column: Int): String = columnNames[column]
        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.name
                1 -> maskToken(row.token)
                else -> ""
            }
        }

        private fun maskToken(token: String): String {
            if (token.length <= 8) return "****"
            return token.take(4) + "****" + token.takeLast(4)
        }
    }

    private class ProjectEditDialog(existing: ProjectConfig?) : DialogWrapper(true) {

        private val nameField = JTextField(existing?.name ?: "", 30)
        private val tokenField = JPasswordField(existing?.token ?: "").apply { columns = 30 }

        val projectName: String get() = nameField.text.trim()
        val projectToken: String get() = String(tokenField.password).trim()

        init {
            title = if (existing == null) "添加项目" else "编辑项目"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return panel {
                row("项目名称:") {
                    cell(nameField).columns(COLUMNS_LARGE)
                }
                row("Token:") {
                    cell(tokenField).columns(COLUMNS_LARGE)
                        .comment("在 Torna 项目的 OpenAPI 页面获取")
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (projectName.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("项目名称不能为空", nameField)
            }
            if (projectToken.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Token 不能为空", tokenField)
            }
            return null
        }
    }
}
