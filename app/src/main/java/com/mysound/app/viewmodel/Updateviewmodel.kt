package com.mysound.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mysound.app.data.update.UpdateInfo
import com.mysound.app.data.update.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

// Les étapes de la popup de mise à jour
sealed interface UpdateState {
    data object Hidden : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val info: UpdateInfo, val message: String) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UpdateRepository(application)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Hidden)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        // Une seule vérification par démarrage de l'appli (le ViewModel survit aux rotations)
        viewModelScope.launch {
            repository.cleanupOldApks()
            repository.checkForUpdate()?.let { _state.value = UpdateState.Available(it) }
        }
    }

    private fun currentInfo(): UpdateInfo? = when (val s = _state.value) {
        is UpdateState.Available -> s.info
        is UpdateState.NeedPermission -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        is UpdateState.Error -> s.info
        UpdateState.Hidden -> null
    }

    /** Bouton "Mettre à jour" / "Réessayer" */
    fun startUpdate() {
        val info = currentInfo() ?: return
        if (_state.value is UpdateState.Downloading) return

        if (!repository.canInstallPackages()) {
            _state.value = UpdateState.NeedPermission(info)
            return
        }

        _state.value = UpdateState.Downloading(info, 0f)
        downloadJob = viewModelScope.launch {
            try {
                val file = repository.downloadApk(info) { progress ->
                    _state.value = UpdateState.Downloading(info, progress)
                }
                if (repository.hasSameSignature(file) == false) {
                    file.delete()
                    _state.value = UpdateState.Error(
                        info,
                        "Cette version n'est pas signée avec la même clé que ton appli installée, " +
                                "Android refuserait la mise à jour. Désinstalle MySound puis installe " +
                                "la nouvelle version manuellement (tu perdras tes données)."
                    )
                    return@launch
                }
                _state.value = UpdateState.ReadyToInstall(info, file)
                repository.installApk(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Error(info, e.message ?: "Le téléchargement a échoué.")
            }
        }
    }

    /** Bouton "Installer" (si l'écran d'installation d'Android a été fermé) */
    fun installNow() {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        if (!repository.canInstallPackages()) {
            _state.value = UpdateState.NeedPermission(s.info)
            return
        }
        repository.installApk(s.file)
    }

    fun openInstallSettings() = repository.openInstallPermissionSettings()

    /** Appelé quand l'appli revient au premier plan : reprend après l'autorisation d'installer. */
    fun onAppResumed() {
        if (_state.value is UpdateState.NeedPermission && repository.canInstallPackages()) {
            startUpdate()
        }
    }

    /** "Plus tard" / "Annuler" / "Fermer" */
    fun dismiss() {
        downloadJob?.cancel()
        _state.value = UpdateState.Hidden
    }
}