package com.onezcodes.ochords.services

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.onezcodes.ochords.BuildConfig

/** Sign in with Google on the phone (Credential Manager): yields an ID token the API verifies (`auth/google`). */
object SocialSignIn {
    class Failure(val cancelled: Boolean, message: String) : Exception(message)

    /** Google needs the *web* OAuth client ID (`GOOGLE_WEB_CLIENT_ID` in local.properties): the token's audience. */
    val googleClientID: String? get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim().takeIf { it.isNotEmpty() }
    val googleAvailable: Boolean get() = googleClientID != null

    /** Google's sheet; returns the ID token for the API. */
    suspend fun google(activity: Activity): String {
        val clientID = googleClientID ?: throw Failure(false, "Google sign-in isn’t set up yet. Log in with your email for now.")
        val option = GetGoogleIdOption.Builder().setServerClientId(clientID).setFilterByAuthorizedAccounts(false).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw Failure(true, "")
        } catch (e: GetCredentialException) {
            throw Failure(false, "Google sign-in didn’t work. Try again.")
        }
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw Failure(false, "Google didn’t return a sign-in token. Try again.")
    }
}
