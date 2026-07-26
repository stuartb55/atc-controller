package com.stuart.atccontroller

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stuart.atccontroller.data.PersistenceCoordinator
import com.stuart.atccontroller.data.PlayerPreferencesRepository
import com.stuart.atccontroller.ui.LiveGameViewModel
import com.stuart.atccontroller.ui.PreferencesGamePersistence
import com.stuart.atccontroller.ui.SystemGameClock
import kotlinx.coroutines.Dispatchers

/**
 * Application composition root. Android construction, storage, dispatchers, and wall clock are
 * supplied here so the state holder's collaborators remain replaceable in tests.
 */
class AtcApplication : Application() {
    internal val container: AtcAppContainer by lazy { AtcAppContainer(this) }
}

internal class AtcAppContainer(application: Application) {
    private val persistence = PreferencesGamePersistence(
        PlayerPreferencesRepository(application),
    )

    val gameViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            LiveGameViewModel(
                application = application,
                savedStateHandle = createSavedStateHandle(),
                preferences = persistence,
                persistenceCoordinator = PersistenceCoordinator(Dispatchers.IO),
                clock = SystemGameClock,
                computationDispatcher = Dispatchers.Default,
                simulationDispatcher = Dispatchers.Default,
            )
        }
    }
}
