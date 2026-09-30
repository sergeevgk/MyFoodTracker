package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.repository.SessionRepository

class LogoutUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke() {
        sessionRepository.clearSession()
    }
}
