package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    val role: String,
    val content: List<OpenAiContentPart>
)

@JsonClass(generateAdapter = true)
data class OpenAiContentPart(
    val type: String, // "text" or "image_url"
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: OpenAiImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiImageUrl(
    val url: String // data:image/png;base64,...
)

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float? = null,
    @Json(name = "max_tokens") val maxTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>? = null,
    val error: OpenAiErrorDetails? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    val message: OpenAiChoiceMessage? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChoiceMessage(
    val role: String? = null,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiErrorDetails(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

interface OpenAiApiService {
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAiChatRequest
    ): OpenAiChatResponse
}

object OpenAiRetrofitClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "ScreenChatWorkspace/Android")
                .build()
            chain.proceed(request)
        }
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: OpenAiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/") // Placeholder, strictly overridden via @Url
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(OpenAiApiService::class.java)
    }
}
