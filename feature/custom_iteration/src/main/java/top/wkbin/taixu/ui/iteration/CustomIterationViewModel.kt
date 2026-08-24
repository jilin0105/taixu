package top.wkbin.taixu.ui.iteration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.wkbin.taixu.iteration.engine.CustomIterationBootstrap

@HiltViewModel
class CustomIterationViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    data class UiState(
        val isBusy: Boolean = false,
        val workspaceReady: Boolean = false,
        val workspacePath: String = "",
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun startCustomIteration(onSessionReady: (prompt: String) -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val rootfsHome = File(app.filesDir, "runtime/rootfs/root")
                val result = CustomIterationBootstrap.bootstrap(app, rootfsHome)

                if (result.success) {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        workspaceReady = true,
                        workspacePath = result.workspacePath
                    )
                    onSessionReady(result.prompt)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        errorMessage = result.errorMessage
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    errorMessage = e.message ?: "Failed to bootstrap iteration"
                )
            }
        }
    }
}
