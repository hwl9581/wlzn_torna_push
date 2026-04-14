package com.wlzn.config

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

@Tag("project")
data class ProjectConfig(
    @Tag("name") var name: String = "",
    @Tag("token") var token: String = ""
)

@Service(Service.Level.PROJECT)
@State(
    name = "TornaSettings",
    storages = [Storage("torna-sync.xml")]
)
class TornaSettings : PersistentStateComponent<TornaSettings.State> {

    data class State(
        var serverUrl: String = "http://localhost:7700",
        @XCollection(style = XCollection.Style.v2)
        var projects: MutableList<ProjectConfig> = mutableListOf(),
        var selectedProjectIndex: Int = 0,
        var author: String = "",
        var defaultFolder: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        state.serverUrl = state.serverUrl.trim().trimStart('\uFEFF', '\u200B')
        state.projects.forEach { p ->
            p.token = p.token.trim().trimStart('\uFEFF', '\u200B')
        }
        if (state.selectedProjectIndex < 0 || state.selectedProjectIndex >= state.projects.size) {
            state.selectedProjectIndex = 0
        }
        myState = state
    }

    fun getSelectedProject(): ProjectConfig? {
        val idx = myState.selectedProjectIndex
        return if (idx in myState.projects.indices) myState.projects[idx] else myState.projects.firstOrNull()
    }

    companion object {
        fun getInstance(project: Project): TornaSettings =
            project.getService(TornaSettings::class.java)
    }
}
