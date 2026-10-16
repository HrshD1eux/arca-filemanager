package com.Hrshd1eux.filemanager.feature.viewer.unified

import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewType
import com.Hrshd1eux.filemanager.feature.viewer.unified.model.PreviewTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTypeResolverTest {

    @Test
    fun `resolves image files correctly`() {
        assertEquals(PreviewType.IMAGE, PreviewTypeResolver.resolve("photo.jpg"))
        assertEquals(PreviewType.IMAGE, PreviewTypeResolver.resolve("picture.PNG"))
        assertEquals(PreviewType.IMAGE, PreviewTypeResolver.resolve("graphic.webp"))
        assertEquals(PreviewType.IMAGE, PreviewTypeResolver.resolve("animation.gif"))
        assertEquals(PreviewType.IMAGE, PreviewTypeResolver.resolve("raw_photo", "image/jpeg"))
    }

    @Test
    fun `resolves video files correctly`() {
        assertEquals(PreviewType.VIDEO, PreviewTypeResolver.resolve("clip.mp4"))
        assertEquals(PreviewType.VIDEO, PreviewTypeResolver.resolve("movie.MKV"))
        assertEquals(PreviewType.VIDEO, PreviewTypeResolver.resolve("stream.webm"))
        assertEquals(PreviewType.VIDEO, PreviewTypeResolver.resolve("unknown_video", "video/mp4"))
    }

    @Test
    fun `resolves audio files correctly`() {
        assertEquals(PreviewType.AUDIO, PreviewTypeResolver.resolve("song.mp3"))
        assertEquals(PreviewType.AUDIO, PreviewTypeResolver.resolve("track.FLAC"))
        assertEquals(PreviewType.AUDIO, PreviewTypeResolver.resolve("voice.ogg"))
        assertEquals(PreviewType.AUDIO, PreviewTypeResolver.resolve("unknown_audio", "audio/mpeg"))
    }

    @Test
    fun `resolves pdf documents correctly`() {
        assertEquals(PreviewType.PDF, PreviewTypeResolver.resolve("document.pdf"))
        assertEquals(PreviewType.PDF, PreviewTypeResolver.resolve("MANUAL.PDF"))
        assertEquals(PreviewType.PDF, PreviewTypeResolver.resolve("blob_pdf", "application/pdf"))
    }

    @Test
    fun `resolves text and code files correctly`() {
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("notes.txt"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("README.md"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("config.json"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("data.xml"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("Main.kt"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("script.py"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("log.log"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("unknown_text", "text/plain"))
        assertEquals(PreviewType.TEXT, PreviewTypeResolver.resolve("api_response", "application/json"))
    }

    @Test
    fun `resolves unsupported file types correctly`() {
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("app.apk"))
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("firmware.bin"))
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("disk.iso"))
        assertEquals(PreviewType.UNSUPPORTED, PreviewTypeResolver.resolve("unknown", "application/octet-stream"))
    }
}
