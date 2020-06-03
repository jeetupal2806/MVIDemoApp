package com.dehaat.assignment.repository.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.switchMap
import com.dehaat.assignment.api.auth.OpenApiAuthService
import com.dehaat.assignment.api.auth.network_responses.LoginResponse
import com.dehaat.assignment.api.auth.network_responses.RegistrationResponse
import com.dehaat.assignment.models.AuthToken
import com.dehaat.assignment.persistence.AccountPropertiesDao
import com.dehaat.assignment.persistence.AuthTokenDao
import com.dehaat.assignment.repository.NetworkBoundResource
import com.dehaat.assignment.session.SessionManager
import com.dehaat.assignment.ui.DataState
import com.dehaat.assignment.ui.Response
import com.dehaat.assignment.ui.ResponseType
import com.dehaat.assignment.ui.auth.state.AuthViewState
import com.dehaat.assignment.ui.auth.state.LoginFields
import com.dehaat.assignment.ui.auth.state.RegistrationFields
import com.dehaat.assignment.util.ApiEmptyResponse
import com.dehaat.assignment.util.ApiErrorResponse
import com.dehaat.assignment.util.ApiSuccessResponse
import com.dehaat.assignment.util.ErrorHandling.Companion.ERROR_UNKNOWN
import com.dehaat.assignment.util.ErrorHandling.Companion.GENERIC_AUTH_ERROR
import com.dehaat.assignment.util.GenericApiResponse
import kotlinx.coroutines.Job
import javax.inject.Inject

class AuthRepository
@Inject
constructor(
    val authTokenDao: AuthTokenDao,
    val accountPropertiesDao: AccountPropertiesDao,
    val openApiAuthService: OpenApiAuthService,
    val sessionManager: SessionManager
) {
    private val TAG: String = "AppDebug"
    private var repositoryJob: Job? = null

    fun attemptLogin(email: String, password: String): LiveData<DataState<AuthViewState>> {

        val loginFieldErrors = LoginFields(email, password).isValidForLogin()
        if (!loginFieldErrors.equals(LoginFields.LoginError.none())) {
            return returnErrorResponse(loginFieldErrors, ResponseType.Dialog())
        }

        return object : NetworkBoundResource<LoginResponse, AuthViewState>(
            sessionManager.isConnectedToTheInternet()
        ) {
            override suspend fun handleApiSuccessResponse(response: ApiSuccessResponse<LoginResponse>) {
                Log.d(TAG, "handleApiSuccessResponse: ${response}")

                // Incorrect login credentials counts as a 200 response from server, so need to handle that
                if (response.body.response.equals(GENERIC_AUTH_ERROR)) {
                    return onErrorReturn(response.body.errorMessage, true, false)
                }

                onCompleteJob(
                    dataState = DataState.data(
                        AuthViewState(
                            authToken = AuthToken(response.body.pk, response.body.token)
                        )
                    )
                )
            }

            override fun createCall(): LiveData<GenericApiResponse<LoginResponse>> {
                return openApiAuthService.login(email, password)
            }

            override fun setJob(job: Job) {
                repositoryJob?.cancel()   // cancel the previous job if not null and assign a new job.
                repositoryJob = job
            }

        }.asLiveData()
    }

    private fun returnErrorResponse(
        errorMessage: String,
        dialog: ResponseType.Dialog
    ): LiveData<DataState<AuthViewState>> {
        Log.d(TAG, "returnErrorResponse: ${errorMessage}")

        return object : LiveData<DataState<AuthViewState>>() {
            override fun onActive() {
                super.onActive()
                value = DataState.error(
                    response = Response(
                        errorMessage,
                        dialog
                    )
                )
            }
        }
    }

    // call this from inside VM.
    fun cancelActiveJobs(){
        Log.d(TAG, "AuthRepository: Cancelling on-going jobs...")
        repositoryJob?.cancel()
    }


    fun attemptRegistration(
        email: String,
        username: String,
        password: String,
        confirmPassword: String
    ): LiveData<DataState<AuthViewState>> {
        val registrationFieldErrors = RegistrationFields(email, username, password, confirmPassword).isValidForRegistration()
        if(!registrationFieldErrors.equals(RegistrationFields.RegistrationError.none())){
            return returnErrorResponse(registrationFieldErrors, ResponseType.Dialog())
        }

        return object : NetworkBoundResource<RegistrationResponse, AuthViewState>(
            sessionManager.isConnectedToTheInternet()
        ){
            override suspend fun handleApiSuccessResponse(response: ApiSuccessResponse<RegistrationResponse>) {
                Log.d(TAG, "handleApiSuccessResponse: ${response}")
                // Incorrect login credentials counts as a 200 response from server, so need to handle that
                if (response.body.response.equals(GENERIC_AUTH_ERROR)) {
                    return onErrorReturn(response.body.errorMessage, true, false)
                }

                onCompleteJob( DataState.data(
                    AuthViewState(
                        authToken = AuthToken(response.body.pk, response.body.token)
                    )
                ))
            }

            override fun createCall(): LiveData<GenericApiResponse<RegistrationResponse>> {
               return openApiAuthService.register(email, username, password, confirmPassword)
            }

            override fun setJob(job: Job) {
                repositoryJob?.cancel()
                repositoryJob = job
            }

        }.asLiveData()
    }

}







