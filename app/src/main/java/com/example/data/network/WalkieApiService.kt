package com.example.data.network

import com.example.data.model.ActivityLogEntry
import com.example.data.model.AddFriendRequest
import com.example.data.model.AuthResponse
import com.example.data.model.ChannelItem
import com.example.data.model.ChannelTokenRequest
import com.example.data.model.CreateChannelRequest
import com.example.data.model.CreateGroupRequest
import com.example.data.model.FriendItem
import com.example.data.model.GenericOkResponse
import com.example.data.model.GroupDetail
import com.example.data.model.GroupSummary
import com.example.data.model.HealthResponse
import com.example.data.model.InviteCodeResponse
import com.example.data.model.JoinGroupRequest
import com.example.data.model.LiveKitTokenResponse
import com.example.data.model.LoginRequest
import com.example.data.model.RegisterRequest
import com.example.data.model.UpdateChannelRequest
import com.example.data.model.UpdateGroupRequest
import com.example.data.model.UpdateProfileRequest
import com.example.data.model.UpdateRoleRequest
import com.example.data.model.UserProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface WalkieApiService {

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<GenericOkResponse>

    @GET("api/v1/users/me")
    suspend fun getMe(): Response<UserProfile>

    @PATCH("api/v1/users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserProfile>

    @GET("api/v1/friends")
    suspend fun getFriends(): Response<List<FriendItem>>

    @POST("api/v1/friends/add")
    suspend fun addFriend(@Body request: AddFriendRequest): Response<FriendItem>

    @DELETE("api/v1/friends/{friendId}")
    suspend fun removeFriend(@Path("friendId") friendId: String): Response<GenericOkResponse>

    @POST("api/v1/friends/{friendId}/ping")
    suspend fun pingFriend(@Path("friendId") friendId: String): Response<GenericOkResponse>

    @GET("api/v1/groups")
    suspend fun getGroups(): Response<List<GroupSummary>>

    @POST("api/v1/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<GroupSummary>

    @POST("api/v1/groups/join")
    suspend fun joinGroup(@Body request: JoinGroupRequest): Response<GroupSummary>

    @GET("api/v1/groups/{groupId}")
    suspend fun getGroupDetail(@Path("groupId") groupId: String): Response<GroupDetail>

    @PATCH("api/v1/groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: String,
        @Body request: UpdateGroupRequest
    ): Response<GroupDetail>

    @POST("api/v1/groups/{groupId}/invite/rotate")
    suspend fun rotateInviteCode(@Path("groupId") groupId: String): Response<InviteCodeResponse>

    @POST("api/v1/groups/{groupId}/invite/revoke")
    suspend fun revokeInviteCode(@Path("groupId") groupId: String): Response<InviteCodeResponse>

    @POST("api/v1/groups/{groupId}/members/{userId}/role")
    suspend fun updateMemberRole(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String,
        @Body request: UpdateRoleRequest
    ): Response<GenericOkResponse>

    @POST("api/v1/groups/{groupId}/members/{userId}/kick")
    suspend fun kickMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String
    ): Response<GenericOkResponse>

    @POST("api/v1/groups/{groupId}/members/{userId}/ban")
    suspend fun banMember(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String
    ): Response<GenericOkResponse>

    @POST("api/v1/groups/{groupId}/channels")
    suspend fun createChannel(
        @Path("groupId") groupId: String,
        @Body request: CreateChannelRequest
    ): Response<ChannelItem>

    @PATCH("api/v1/groups/{groupId}/channels/{channelId}")
    suspend fun updateChannel(
        @Path("groupId") groupId: String,
        @Path("channelId") channelId: String,
        @Body request: UpdateChannelRequest
    ): Response<ChannelItem>

    @GET("api/v1/groups/{groupId}/activity")
    suspend fun getGroupActivity(@Path("groupId") groupId: String): Response<List<ActivityLogEntry>>

    @POST("api/v1/channels/{channelId}/token")
    suspend fun getChannelToken(
        @Path("channelId") channelId: String,
        @Body request: ChannelTokenRequest
    ): Response<LiveKitTokenResponse>

    @POST("api/v1/channels/{channelId}/ping-wake")
    suspend fun wakeChannelMembers(@Path("channelId") channelId: String): Response<GenericOkResponse>
}

object ApiClientFactory {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun createOkHttpClient(tokenProvider: () -> String?): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val token = tokenProvider()
                val req = if (!token.isNullOrBlank()) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    original
                }
                chain.proceed(req)
            }
            .addInterceptor(logging)
            .build()
    }

    fun createService(baseUrl: String, client: OkHttpClient): WalkieApiService {
        val normalized = baseUrl.trim().trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WalkieApiService::class.java)
    }
}
