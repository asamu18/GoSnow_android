package com.gosnow.app.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo

class AuthRepository(private val supabaseClient: SupabaseClient) {

    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> {
        return runCatching {
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
        }.map { }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> {
        return runCatching {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }.map { }
    }

    suspend fun signOut(): Result<Unit> {
        return runCatching {
            supabaseClient.auth.signOut()
        }
    }

    suspend fun currentUser(): Result<UserInfo?> = runCatching {
        supabaseClient.auth.currentUserOrNull()
    }

    suspend fun hasActiveSession(): Result<Boolean> = runCatching {
        supabaseClient.auth.currentSessionOrNull() != null
    }
}
