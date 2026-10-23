package dev.cttv.ui

import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.rules.ExternalResource

class TestVideoServer : ExternalResource() {
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    lateinit var url: String
        private set

    override fun before() {
        val video = InstrumentationRegistry.getInstrumentation().context.resources
            .openRawResource(dev.cttv.test.R.raw.player_fixture).use { it.readBytes() }
        server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        url = "http://127.0.0.1:${server.localPort}/fixture.mp4"
        worker = thread(name = "CTTV-test-video", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { client ->
                        client.soTimeout = 2_000
                        val reader = client.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                        client.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${video.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(video)
                            flush()
                        }
                    }
                } catch (_: IOException) {
                    // A player may close its request when the screen is disposed.
                }
            }
        }
    }

    override fun after() {
        server.close()
        worker.join(3_000)
    }
}
