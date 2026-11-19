package com.axoo.features.posts.data

import com.axoo.features.posts.data.dto.CommentResponse
import com.axoo.features.posts.data.dto.CommentsResponse
import com.axoo.features.posts.data.dto.CreateCommentRequest
import com.axoo.features.posts.data.dto.CreatePostRequest
import com.axoo.features.posts.data.dto.NewFeedSeenRequest
import com.axoo.features.posts.data.dto.NewFeedUnreadCountResponse
import com.axoo.features.posts.data.dto.PollVoteRequest
import com.axoo.features.posts.data.dto.PollVoteResponse
import com.axoo.features.posts.data.dto.PostResponse
import com.axoo.features.posts.data.dto.PostsResponse
import com.axoo.features.posts.data.dto.UpdateCommentRequest
import com.axoo.features.posts.data.dto.UpdatePostRequest
import com.axoo.features.posts.data.dto.VoteRequest
import com.axoo.features.posts.data.dto.VoteResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap

interface PostsApi {
    @PUT("posts/{id}/hidden")
    suspend fun hidePost(@Path("id") id: String)

    @GET("posts")
    suspend fun getPosts(@QueryMap params: Map<String, String>): PostsResponse

    @GET("users/{userId}/posts")
    suspend fun getUserPosts(@Path("userId") userId: String, @QueryMap params: Map<String, String>): PostsResponse

    @GET("posts/mine")
    suspend fun getMyPosts(@QueryMap params: Map<String, String>): PostsResponse

    @GET("posts/new/unread-count")
    suspend fun getNewFeedUnreadCount(): NewFeedUnreadCountResponse

    @POST("posts/new/seen")
    suspend fun markNewFeedSeen(@Body request: NewFeedSeenRequest)

    @GET("posts/{id}")
    suspend fun getPost(@Path("id") id: String): PostResponse

    @POST("posts")
    suspend fun postEditor(@Body request: CreatePostRequest): PostResponse

    @PATCH("posts/{id}")
    suspend fun updatePost(
        @Path("id") id: String,
        @Body request: UpdatePostRequest,
    )

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") id: String)

    @PUT("posts/{id}/vote")
    suspend fun votePost(
        @Path("id") id: String,
        @Body request: VoteRequest,
    ): VoteResponse

    @PUT("posts/{id}/poll/vote")
    suspend fun votePoll(
        @Path("id") id: String,
        @Body request: PollVoteRequest,
    ): PollVoteResponse

    @GET("posts/{postId}/comments")
    suspend fun getComments(
        @Path("postId") postId: String,
    ): CommentsResponse

    @POST("posts/{postId}/comments")
    suspend fun createComment(
        @Path("postId") postId: String,
        @Body request: CreateCommentRequest,
    ): CommentResponse

    @PATCH("posts/{postId}/comments/{commentId}")
    suspend fun updateComment(
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Body request: UpdateCommentRequest,
    )

    @DELETE("posts/{postId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
    )

    @PUT("posts/{postId}/comments/{commentId}/vote")
    suspend fun voteComment(
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Body request: VoteRequest,
    ): VoteResponse
}
