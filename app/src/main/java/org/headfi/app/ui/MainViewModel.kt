package org.headfi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.headfi.app.data.SessionPreferencesRepository
import org.headfi.app.model.AppPreferences
import org.headfi.app.model.HeadFiSection
import org.headfi.app.model.PollInterval
import org.headfi.app.model.ThemeMode
import org.headfi.app.model.UserSessionState
import org.headfi.app.notifications.HeadFiNotifications
import org.headfi.app.notifications.UnreadPollScheduler

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SessionPreferencesRepository(application.applicationContext)

    val preferences: StateFlow<AppPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    private val _sessionState = MutableStateFlow(
        UserSessionState(hasRememberMeCookie = repository.hasRememberMeCookie())
    )
    val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

    private val _currentUrl = MutableStateFlow("https://www.head-fi.org/")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("Head-Fi.org")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _selectedSection = MutableStateFlow(HeadFiSection.HOME)
    val selectedSection: StateFlow<HeadFiSection> = _selectedSection.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadProgress = MutableStateFlow(0)
    val loadProgress: StateFlow<Int> = _loadProgress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _networkError = MutableStateFlow<String?>(null)
    val networkError: StateFlow<String?> = _networkError.asStateFlow()

    private val _isSessionReady = MutableStateFlow(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    init {
        viewModelScope.launch {
            val restored = repository.restoreCookiesIfNeeded()
            val hasCookie = restored || repository.hasRememberMeCookie()
            _sessionState.update { it.copy(hasRememberMeCookie = hasCookie) }
            _isSessionReady.value = true
        }
    }

    fun onUrlChanged(url: String) {
        _currentUrl.value = url
        _selectedSection.value = HeadFiSection.fromUrl(url)
        viewModelScope.launch {
            repository.saveLastVisitedUrl(url)
            repository.syncAndBackupCookies()
            _sessionState.update {
                it.copy(hasRememberMeCookie = repository.hasRememberMeCookie())
            }
        }
    }

    fun onTitleChanged(title: String) {
        val cleanTitle = title
            .replace(" | Headphone Reviews and Discussion - Head-Fi.org", "")
            .replace("Headphone Reviews and Discussion - Head-Fi.org", "Head-Fi.org")
            .trim()
        _pageTitle.value = cleanTitle.ifBlank { "Head-Fi.org" }
    }

    fun onLoadingStateChanged(loading: Boolean) {
        _isLoading.value = loading
    }

    fun onProgressChanged(progress: Int) {
        _loadProgress.value = progress
    }

    fun onNavigationStateChanged(canBack: Boolean, canForward: Boolean) {
        _canGoBack.value = canBack
        _canGoForward.value = canForward
    }

    fun onNetworkError(error: String?) {
        _networkError.value = error
    }

    fun onXenForoSessionDetected(
        isLoggedIn: Boolean,
        username: String,
        avatarUrl: String,
        unreadAlerts: Int,
        unreadConversations: Int
    ) {
        viewModelScope.launch {
            if (isLoggedIn) {
                repository.syncAndBackupCookies()
                if (username.isNotBlank()) {
                    repository.saveUserIdentity(username, avatarUrl)
                }
            }
            // The user is looking at these badges right now, so don't re-notify
            // for counts they have already seen in the app.
            repository.updateLastNotified(unreadAlerts, unreadConversations)
            if (unreadAlerts == 0) {
                HeadFiNotifications.clear(getApplication(), HeadFiNotifications.ID_ALERTS)
            }
            if (unreadConversations == 0) {
                HeadFiNotifications.clear(getApplication(), HeadFiNotifications.ID_CONVERSATIONS)
            }
            _sessionState.value = UserSessionState(
                isLoggedIn = isLoggedIn,
                username = username,
                avatarUrl = avatarUrl,
                unreadAlerts = unreadAlerts,
                unreadConversations = unreadConversations,
                hasRememberMeCookie = repository.hasRememberMeCookie()
            )
        }
    }

    fun onAppPausedOrStopped() {
        viewModelScope.launch {
            repository.syncAndBackupCookies()
        }
    }

    fun logoutAndClearSession(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearSessionData()
            repository.updateLastNotified(0, 0)
            HeadFiNotifications.clear(getApplication(), HeadFiNotifications.ID_ALERTS)
            HeadFiNotifications.clear(getApplication(), HeadFiNotifications.ID_CONVERSATIONS)
            _sessionState.value = UserSessionState(hasRememberMeCookie = false)
            onComplete()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch { repository.setAmoledBlack(enabled) }
    }

    fun setInjectWebDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setInjectWebDarkTheme(enabled) }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAdBlockerEnabled(enabled) }
    }

    fun setRememberLastPage(enabled: Boolean) {
        viewModelScope.launch { repository.setRememberLastPage(enabled) }
    }

    fun setUnreadNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setUnreadNotificationsEnabled(enabled)
            UnreadPollScheduler.sync(
                context = getApplication(),
                enabled = enabled,
                interval = repository.preferencesFlow.first().pollInterval
            )
        }
    }

    fun setPollInterval(interval: PollInterval) {
        viewModelScope.launch {
            repository.setPollInterval(interval)
            val preferences = repository.preferencesFlow.first()
            if (preferences.unreadNotificationsEnabled) {
                UnreadPollScheduler.sync(
                    context = getApplication(),
                    enabled = true,
                    interval = interval
                )
            }
        }
    }
}
