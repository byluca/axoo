package com.axoo.features.posts.data

import com.axoo.features.posts.data.dto.*
import com.axoo.features.posts.domain.RemoteMediaType
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class KlipyRepositoryTest {
    private fun file(name: String, width: Int = 320, height: Int = 240) =
        KlipyFile("https://static.klipy.com/$name?delivery=original", width, height)

    private fun response(type: String = "gif", hasNext: Boolean = true) = KlipyResponse(true,
        KlipyPage(listOf(KlipyItem("ciao-amici", "Ciao", type, mapOf(
            "hd" to mapOf("gif" to file("original.gif")),
            "sm" to mapOf("gif" to file("preview.gif", 200, 150), "webp" to file("preview.webp")),
            "xs" to mapOf("jpg" to file("still.jpg")),
        ))), currentPage = 2, hasNext = hasNext))

    @Test fun mappingKeepsDeliveryURLsDimensionsAndSlugAndUsesProviderPagination() {
        val page = response().toRemoteMediaPage(RemoteMediaType.Gif, "  ciao!  ")
        val media = page.items.single()
        assertEquals("klipy", media.provider)
        assertEquals("ciao-amici", media.id)
        assertEquals("  ciao!  ", media.searchQuery)
        assertEquals(file("preview.gif").url, media.mediaUrl)
        assertEquals(file("preview.webp").url, media.previewUrl)
        assertEquals(file("preview.gif").url, media.previewFallbackUrl)
        assertEquals(file("still.jpg").url, media.loadingPreviewUrl)
        assertEquals(200, media.width)
        assertEquals(150, media.height)
        assertEquals(3, page.nextOffset)
        assertNull(response(hasNext = false).toRemoteMediaPage(RemoteMediaType.Gif).nextOffset)
    }

    @Test fun stickerAndResultsKeepTheirOriginalOrderIncludingRepeatedIDs() {
        val first = response("sticker")
        val page = first.copy(data = first.data.copy(data = first.data.data + first.data.data))
            .toRemoteMediaPage(RemoteMediaType.GiphySticker)
        assertEquals(2, page.items.size)
        assertTrue(page.items.all { it.type == RemoteMediaType.GiphySticker })
    }

    @Test fun memesKeepKlipyPNGOriginalAndSearchAndUseUnfilteredCollection() = runTest {
        val response = KlipyResponse(true, KlipyPage(listOf(KlipyItem("meme", "Meme", "static_meme", mapOf(
            "hd" to mapOf("png" to file("original.png")),
            "sm" to mapOf("webp" to file("small.webp"))
        ))), currentPage = 1, hasNext = true))
        val api = mockk<KlipyApi>()
        coEvery { api.list(any(), any(), any(), any()) } returns response
        val page = KlipyRepository(api, "anonymous", "test-key").loadMemes("ciao", 0)
        assertEquals(file("original.png").url, page.items.single().mediaUrl)
        assertEquals("klipy", page.items.single().provider)
        assertEquals("ciao", page.items.single().searchQuery)
        assertEquals(2, page.nextOffset)
        coVerify { api.list("test-key", "static-memes", "search", match {
            it["q"] == "ciao" && "format_filter" !in it
        }) }
    }

    @Test fun unsupportedAdsAndUntrustedMediaFailTheWholePage() {
        assertThrows(IOException::class.java) { response("ad").toRemoteMediaPage(RemoteMediaType.Gif) }
        val original = response()
        val bad = original.data.data.single().copy(file = mapOf("hd" to mapOf("gif" to
            file("image.gif").copy(url = "https://static.klipy.com.evil.example/image.gif"))))
        assertThrows(IOException::class.java) {
            original.copy(data = original.data.copy(data = listOf(bad))).toRemoteMediaPage(RemoteMediaType.Gif)
        }
        assertThrows(IOException::class.java) { original.copy(result = false).toRemoteMediaPage(RemoteMediaType.Gif) }
    }

    @Test fun decoderAcceptsNumericProviderIDsAndIgnoresUnneededMetadata() {
        val decoded = Moshi.Builder().build().adapter(KlipyResponse::class.java).fromJson("""
            {"result":true,"data":{"current_page":1,"has_next":false,"data":[
              {"id":6914457558367152,"slug":"hello","title":"Hello","type":"gif","file":{
                "hd":{"gif":{"url":"https://static.klipy.com/hello.gif","width":100,"height":80,"size":512}}
              }}]}}
        """)!!
        assertEquals("hello", decoded.toRemoteMediaPage(RemoteMediaType.Gif).items.single().id)
    }

    @Test fun directRequestsUseStableAnonymousIDFixedPageSizeAndUnmodifiedSearch() = runTest {
        val api = mockk<KlipyApi>()
        coEvery { api.list(any(), any(), any(), any()) } answers {
            response(if (secondArg<String>() == "stickers") "sticker" else "gif")
        }
        val repository = KlipyRepository(api, "anonymous-id", "test-key")
        repository.load("  ciao!  ", 0)
        repository.loadStickers("", 3)
        coVerify { api.list("test-key", "gifs", "search", match {
            it["page"] == "1" && it["per_page"] == "30" && it["q"] == "  ciao!  " &&
                it["customer_id"] == "anonymous-id" && it["content_filter"] == "high" &&
                it["format_filter"] == "gif,webp,jpg"
        }) }
        coVerify { api.list("test-key", "stickers", "trending", match {
            it["page"] == "3" && "q" !in it && it["format_filter"] == "gif,webp"
        }) }
    }
}
