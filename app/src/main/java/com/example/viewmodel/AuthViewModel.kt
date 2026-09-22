package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.UserEntity
import com.example.data.model.ActiveDeviceSession
import com.example.data.repository.ActiveDeviceSessionManager
import com.example.data.repository.AuthRepository
import com.example.data.service.EmailOtpService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val currentUser: StateFlow<UserEntity?> = authRepository.currentUser
    val isSessionRestoring: StateFlow<Boolean> = authRepository.isSessionRestoring
    val allUsers: kotlinx.coroutines.flow.Flow<List<UserEntity>> = authRepository.allUsers
    val pendingUsers: kotlinx.coroutines.flow.Flow<List<UserEntity>> = authRepository.pendingUsers
    val activeSessions: StateFlow<List<ActiveDeviceSession>> = ActiveDeviceSessionManager.activeSessions

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _adminActionStatus = MutableStateFlow<String?>(null)
    val adminActionStatus: StateFlow<String?> = _adminActionStatus.asStateFlow()

    // Holds the last generated OTP for testing convenience
    private val _lastSentOtp = MutableStateFlow<String?>(null)
    val lastSentOtp: StateFlow<String?> = _lastSentOtp.asStateFlow()

    suspend fun approveUser(
        userId: String,
        canTakeAllAttendance: Boolean = true,
        canManageStudents: Boolean = true,
        canManageClasses: Boolean = true,
        canViewReports: Boolean = true
    ): Boolean {
        _isLoading.value = true
        val success = authRepository.approveUserRegistration(
            userId = userId,
            canTakeAllAttendance = canTakeAllAttendance,
            canManageStudents = canManageStudents,
            canManageClasses = canManageClasses,
            canViewReports = canViewReports
        )
        _isLoading.value = false
        if (success) {
            _adminActionStatus.value = "User approved successfully"
        }
        return success
    }

    suspend fun rejectUser(userId: String): Boolean {
        _isLoading.value = true
        val success = authRepository.rejectUserRegistration(userId)
        _isLoading.value = false
        if (success) {
            _adminActionStatus.value = "User registration rejected"
        }
        return success
    }

    suspend fun setUserActiveStatus(userId: String, active: Boolean): Boolean {
        _isLoading.value = true
        val success = authRepository.toggleUserActiveStatus(userId, active)
        _isLoading.value = false
        if (success) {
            _adminActionStatus.value = if (active) "User account activated" else "User account deactivated"
        }
        return success
    }

    suspend fun deleteUser(userId: String): Boolean {
        _isLoading.value = true
        val success = authRepository.deleteUserAccount(userId)
        _isLoading.value = false
        return success
    }

    suspend fun purgeAllNonAdminUsers(): Boolean {
        _isLoading.value = true
        val success = authRepository.purgeAllNonAdminUsers()
        _isLoading.value = false
        return success
    }

    suspend fun updateTeacherPermissions(
        user: UserEntity,
        canTakeAllAttendance: Boolean,
        canManageStudents: Boolean,
        canManageClasses: Boolean,
        canViewReports: Boolean,
        collaborationStatus: String = "APPROVED"
    ): UserEntity {
        return authRepository.updateTeacherPermissions(
            user = user,
            canTakeAllAttendance = canTakeAllAttendance,
            canManageStudents = canManageStudents,
            canManageClasses = canManageClasses,
            canViewReports = canViewReports,
            collaborationStatus = collaborationStatus
        )
    }

    suspend fun syncUsersWithCloud() {
        _isLoading.value = true
        authRepository.syncUsersWithCloud()
        _isLoading.value = false
    }

    suspend fun login(usernameOrEmail: String, password: String): Boolean {
        _isLoading.value = true
        _loginError.value = null
        val result = authRepository.login(usernameOrEmail, password)
        _isLoading.value = false

        return if (result.isSuccess) {
            true
        } else {
            _loginError.value = result.exceptionOrNull()?.message ?: "Login failed"
            false
        }
    }

    suspend fun signUp(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): Boolean {
        _isLoading.value = true
        _loginError.value = null

        val result = authRepository.signUp(fullName, email, phone, password)
        _isLoading.value = false

        return if (result.isSuccess) {
            _lastSentOtp.value = null
            true
        } else {
            _loginError.value = result.exceptionOrNull()?.message ?: "Registration failed"
            false
        }
    }

    suspend fun requestSignUpOtp(email: String, fullName: String): String? {
        _isLoading.value = true
        _loginError.value = null

        val trimmedEmail = email.trim()
        if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
            _loginError.value = "Please enter a valid email address (e.g. name@domain.com)"
            _isLoading.value = false
            return null
        }

        // Generate 6-digit OTP
        val otpCode = EmailOtpService.generateOtp(trimmedEmail)
        _lastSentOtp.value = otpCode

        // Send via Email Service
        EmailOtpService.sendOtpEmail(trimmedEmail, otpCode, fullName)

        _isLoading.value = false
        return otpCode
    }

    suspend fun verifyOtpAndSignUp(
        fullName: String,
        email: String,
        phone: String,
        password: String,
        enteredOtp: String
    ): Boolean {
        _isLoading.value = true
        _loginError.value = null

        val trimmedEmail = email.trim()
        val isValidOtp = EmailOtpService.verifyOtp(trimmedEmail, enteredOtp)

        if (!isValidOtp) {
            _loginError.value = "Invalid or expired 6-digit OTP code. Please check and try again."
            _isLoading.value = false
            return false
        }

        val result = authRepository.signUp(fullName, trimmedEmail, phone, password)
        _isLoading.value = false

        return if (result.isSuccess) {
            _lastSentOtp.value = null
            true
        } else {
            _loginError.value = result.exceptionOrNull()?.message ?: "Registration failed"
            false
        }
    }

    suspend fun refreshCurrentUser() {
        authRepository.refreshCurrentUser()
    }

    fun startListeningSessions(context: android.content.Context) {
        ActiveDeviceSessionManager.startListeningAllSessions(context)
    }

    fun cleanupDuplicateSessions(context: android.content.Context, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            ActiveDeviceSessionManager.cleanupDuplicateAndStaleSessions(context) { count ->
                _isLoading.value = false
                if (count > 0) {
                    _adminActionStatus.value = "Cleaned up $count duplicate/stale session(s)"
                }
                onComplete?.invoke(count)
            }
        }
    }

    suspend fun deactivateDeviceSession(context: android.content.Context, session: ActiveDeviceSession): Boolean {
        _isLoading.value = true
        var result = false
        ActiveDeviceSessionManager.deactivateSession(context, session) { success ->
            result = success
        }
        _isLoading.value = false
        if (result) {
            _adminActionStatus.value = "Session for ${session.userName} deactivated"
        }
        return result
    }

    suspend fun deleteDeviceSessionAndUser(context: android.content.Context, session: ActiveDeviceSession): Boolean {
        _isLoading.value = true
        var result = false
        ActiveDeviceSessionManager.deleteSessionAndUser(context, session) { success ->
            result = success
        }
        _isLoading.value = false
        if (result) {
            _adminActionStatus.value = "Access for ${session.userName} deleted"
        }
        return result
    }

    fun clearAdminActionStatus() {
        _adminActionStatus.value = null
    }

    fun logout() {
        authRepository.logout()
    }

    fun clearError() {
        _loginError.value = null
    }
}

