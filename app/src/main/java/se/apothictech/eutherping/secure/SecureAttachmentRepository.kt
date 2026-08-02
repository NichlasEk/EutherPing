package se.apothictech.eutherping.secure

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PreparedSecureAttachment(
    val wireBody: String,
    val descriptor: SecureAttachmentDescriptor,
)

object SecureAttachmentRepository {
    private const val SERVER_PORT = 39_841
    private const val MAX_FILE_BYTES = 268_435_456L
    private const val SERVER_PREFS = "eutherping_attachment_server_v1"
    private const val DOWNLOAD_PREFS = "eutherping_attachment_downloads_v1"
    private const val BUFFER_SIZE = 64 * 1024
    private const val OFFER_LIFETIME_MS = 24 * 60 * 60 * 1000L
    private val random = SecureRandom()
    private val serverRunning = AtomicBoolean(false)

    fun clearTransientPlaintext(context: Context) {
        File(context.cacheDir, "secure_attachment_view").listFiles()?.forEach(File::delete)
    }

    fun ensureServerStarted(context: Context): Result<Unit> = runCatching {
        if (serverRunning.get()) return@runCatching
        synchronized(this) {
            if (serverRunning.get()) return@synchronized
            val server = ServerSocket(SERVER_PORT).apply { reuseAddress = true }
            serverRunning.set(true)
            Thread({ serveLoop(context.applicationContext, server) }, "EutherPing-attachment-server").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun prepareOutgoing(context: Context, address: String, uri: Uri): Result<PreparedSecureAttachment> =
        runCatching {
            check(
                ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) ==
                    PackageManager.PERMISSION_GRANTED,
            ) {
                "Local network access is blocked. On GrapheneOS, open Settings → Apps → EutherPing → " +
                    "Permissions and enable Network, then keep both phones on the same Wi-Fi."
            }
            val peer = SecureRepository.peer(context, address)
            check(peer.canEncrypt) {
                "Verify the vessel before sending a file"
            }
            ensureServerStarted(context).getOrThrow()
            val id = UUID.randomUUID().toString()
            val metadata = readMetadata(context, uri)
            require(metadata.size == null || metadata.size in 0..MAX_FILE_BYTES) {
                "Secure attachments are limited to 256 MB in this beta"
            }
            val contentKey = ByteArray(32).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val outgoingDirectory = File(context.filesDir, "secure_attachments/outgoing").apply { mkdirs() }
            val temporary = File(outgoingDirectory, "$id.partial")
            val encrypted = File(outgoingDirectory, "$id.enc")
            val plaintextDigest = MessageDigest.getInstance("SHA-256")
            var plaintextSize = 0L
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(contentKey, "AES"),
                        GCMParameterSpec(128, nonce),
                    )
                    updateAAD(id.toByteArray(Charsets.UTF_8))
                }
                checkNotNull(context.contentResolver.openInputStream(uri)).use { rawInput ->
                    BufferedInputStream(rawInput, BUFFER_SIZE).use { input ->
                        CipherOutputStream(
                            BufferedOutputStream(temporary.outputStream(), BUFFER_SIZE),
                            cipher,
                        ).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                plaintextSize += count
                                require(plaintextSize <= MAX_FILE_BYTES) {
                                    "Secure attachments are limited to 256 MB in this beta"
                                }
                                plaintextDigest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                check(temporary.renameTo(encrypted)) { "Could not finalize encrypted attachment" }
            } catch (error: Throwable) {
                temporary.delete()
                encrypted.delete()
                throw error
            }
            val token = randomBytes(24).toUrlBase64()
            registerTransfer(
                context,
                id,
                token,
                encrypted,
                checkNotNull(peer.signingPublicKey),
            )
            val descriptor = SecureAttachmentDescriptor(
                id = id,
                name = metadata.name,
                mimeType = metadata.mimeType,
                plaintextSize = plaintextSize,
                plaintextSha256 = plaintextDigest.digest().toHex(),
                ciphertextSize = encrypted.length(),
                ciphertextSha256 = sha256(encrypted),
                contentKey = contentKey,
                nonce = nonce,
                downloadUrl = "http://${localIpv4Address(context)}:$SERVER_PORT/eutherping/$id/$token",
                incoming = false,
            )
            val wireBody = SecureRepository.encryptAttachmentOffer(context, address, descriptor)
                .getOrElse { error ->
                    unregisterTransfer(context, id)
                    encrypted.delete()
                    throw error
                }
            PreparedSecureAttachment(wireBody, descriptor)
        }.recoverCatching { error -> throw explainAttachmentFailure(error) }

    internal fun explainAttachmentFailure(error: Throwable): Throwable {
        val networkDenied = generateSequence(error) { it.cause }.any { cause ->
            (cause is SocketException || cause.javaClass.name == "android.system.ErrnoException") &&
                cause.message.orEmpty().let { message ->
                    message.contains("EPERM", ignoreCase = true) ||
                        message.contains("Operation not permitted", ignoreCase = true)
                }
        }
        return if (networkDenied) {
            IllegalStateException(
                "Local network access is blocked. On GrapheneOS, open Settings → Apps → EutherPing → " +
                    "Permissions and enable Network, then keep both phones on the same Wi-Fi.",
                error,
            )
        } else {
            error
        }
    }

    fun downloadIncoming(
        context: Context,
        address: String,
        descriptor: SecureAttachmentDescriptor,
    ): Result<File> = runCatching {
        check(descriptor.incoming) { "Only received attachments can be downloaded" }
        check(SecureRepository.peer(context, address).state == SecurePeerState.VERIFIED) {
            "Verify the vessel before downloading"
        }
        val existing = downloadedCiphertext(context, descriptor.id)
        if (existing?.isFile == true && existing.length() == descriptor.ciphertextSize &&
            sha256(existing) == descriptor.ciphertextSha256
        ) return@runCatching existing

        val url = java.net.URL(descriptor.downloadUrl)
        require(url.protocol == "http") { "Secure attachment transport must use local HTTP" }
        require(url.port == SERVER_PORT) { "Secure attachment endpoint uses an unexpected port" }
        require(url.userInfo == null && url.query == null && url.ref == null) {
            "Secure attachment endpoint is malformed"
        }
        val pathParts = url.path.split('/').filter(String::isNotBlank)
        require(pathParts.size == 3 && pathParts[0] == "eutherping" && pathParts[1] == descriptor.id) {
            "Secure attachment endpoint does not match its signed offer"
        }
        val token = pathParts[2]
        require(token.matches(Regex("[A-Za-z0-9_-]{32}"))) { "Secure attachment token is malformed" }
        require(url.host.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))) {
            "Secure attachment endpoint must use a numeric local address"
        }
        val resolved = InetAddress.getByName(url.host)
        require(resolved.isSiteLocalAddress || resolved.isLinkLocalAddress || resolved.isLoopbackAddress) {
            "Attachment endpoint is not on the local network"
        }
        val incomingDirectory = File(context.filesDir, "secure_attachments/incoming").apply { mkdirs() }
        val partial = File(incomingDirectory, "${descriptor.id}.partial")
        val encrypted = File(incomingDirectory, "${descriptor.id}.enc")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty(
                "X-EutherPing-Proof",
                SecureRepository.signAttachmentRequest(
                    context,
                    descriptor.id,
                    token,
                ),
            )
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Direct Wi-Fi vessel is unavailable (${connection.responseCode})"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            DigestInputStream(BufferedInputStream(connection.inputStream, BUFFER_SIZE), digest).use { input ->
                BufferedOutputStream(partial.outputStream(), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(received <= descriptor.ciphertextSize) { "Attachment exceeded signed size" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(received == descriptor.ciphertextSize) { "Attachment was incomplete" }
            check(digest.digest().toHex() == descriptor.ciphertextSha256) {
                "Encrypted attachment hash mismatch"
            }
            verifyPlaintext(descriptor, partial)
            if (encrypted.exists()) encrypted.delete()
            check(partial.renameTo(encrypted)) { "Could not store encrypted attachment" }
            context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
                .edit().putString(descriptor.id, encrypted.absolutePath).apply()
            encrypted
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun downloadedCiphertext(context: Context, id: String): File? =
        context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .getString(id, null)?.let(::File)?.takeIf(File::isFile)

    fun openDownloaded(context: Context, descriptor: SecureAttachmentDescriptor): Result<Unit> = runCatching {
        val encrypted = checkNotNull(downloadedCiphertext(context, descriptor.id)) {
            "Download the attachment first"
        }
        val openDirectory = File(context.cacheDir, "secure_attachment_view").apply { mkdirs() }
        openDirectory.listFiles()?.forEach(File::delete)
        val plaintext = File(openDirectory, "${descriptor.id}-${descriptor.name}")
        decryptToFile(descriptor, encrypted, plaintext)
        Handler(Looper.getMainLooper()).postDelayed({ plaintext.delete() }, 10 * 60 * 1000L)
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.securefiles",
            plaintext,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, descriptor.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun verifyPlaintext(descriptor: SecureAttachmentDescriptor, encrypted: File) {
        val verification = File(encrypted.parentFile, "${descriptor.id}.verify")
        try {
            decryptToFile(descriptor, encrypted, verification)
        } finally {
            verification.delete()
        }
    }

    private fun decryptToFile(
        descriptor: SecureAttachmentDescriptor,
        encrypted: File,
        plaintext: File,
    ) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(descriptor.contentKey, "AES"),
                GCMParameterSpec(128, descriptor.nonce),
            )
            updateAAD(descriptor.id.toByteArray(Charsets.UTF_8))
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            CipherInputStream(BufferedInputStream(encrypted.inputStream(), BUFFER_SIZE), cipher).use { input ->
                BufferedOutputStream(plaintext.outputStream(), BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= descriptor.plaintextSize) { "Decrypted attachment exceeded signed size" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(size == descriptor.plaintextSize) { "Decrypted attachment size mismatch" }
            check(digest.digest().toHex() == descriptor.plaintextSha256) {
                "Decrypted attachment hash mismatch"
            }
        } catch (error: Throwable) {
            plaintext.delete()
            throw error
        }
    }

    private fun serveLoop(context: Context, server: ServerSocket) {
        while (!server.isClosed) {
            try {
                val socket = server.accept()
                Thread({ serveOne(context, socket) }, "EutherPing-attachment-client").apply {
                    isDaemon = true
                    start()
                }
            } catch (_: SocketException) {
                break
            }
        }
        serverRunning.set(false)
    }

    private fun serveOne(context: Context, socket: Socket) {
        socket.use { client ->
            client.soTimeout = 8_000
            val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
            val request = reader.readLine().orEmpty().split(' ')
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }
            if (request.size < 2 || request[0] != "GET") return respond(client, 405, "Method Not Allowed")
            val parts = request[1].substringBefore('?').split('/').filter(String::isNotBlank)
            if (parts.size != 3 || parts[0] != "eutherping") return respond(client, 404, "Not Found")
            val id = parts[1]
            val token = parts[2]
            val raw = context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE)
                .getString(id, null) ?: return respond(client, 404, "Not Found")
            val registration = runCatching { JSONObject(raw) }.getOrNull()
                ?: return respond(client, 404, "Not Found")
            val expectedToken = registration.optString("token").toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(expectedToken, token.toByteArray(Charsets.UTF_8))) {
                return respond(client, 403, "Forbidden")
            }
            if (registration.optLong("expires", 0L) < System.currentTimeMillis()) {
                File(registration.optString("path")).delete()
                unregisterTransfer(context, id)
                return respond(client, 410, "Gone")
            }
            val signingPublicKey = runCatching {
                registration.optString("signing").fromUrlBase64()
            }.getOrNull() ?: return respond(client, 403, "Forbidden")
            val proof = headers["x-eutherping-proof"].orEmpty()
            if (!SecureRepository.verifyAttachmentRequest(signingPublicKey, id, token, proof)) {
                return respond(client, 403, "Forbidden")
            }
            val file = File(registration.optString("path"))
            if (!file.isFile) return respond(client, 410, "Gone")
            val output = BufferedOutputStream(client.getOutputStream(), BUFFER_SIZE)
            output.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: ${file.length()}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
            )
            file.inputStream().use { it.copyTo(output, BUFFER_SIZE) }
            output.flush()
        }
    }

    private fun respond(socket: Socket, status: Int, reason: String) {
        val body = "$status $reason"
        socket.getOutputStream().buffered().use { output ->
            output.write(
                ("HTTP/1.1 $status $reason\r\nContent-Type: text/plain\r\n" +
                    "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body")
                    .toByteArray(Charsets.US_ASCII),
            )
        }
    }

    private fun registerTransfer(
        context: Context,
        id: String,
        token: String,
        file: File,
        recipientSigningPublicKey: ByteArray,
    ) {
        val json = JSONObject()
            .put("token", token)
            .put("path", file.absolutePath)
            .put("signing", recipientSigningPublicKey.toUrlBase64())
            .put("expires", System.currentTimeMillis() + OFFER_LIFETIME_MS)
        context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE)
            .edit().putString(id, json.toString()).apply()
    }

    private fun unregisterTransfer(context: Context, id: String) {
        context.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE).edit().remove(id).apply()
    }

    private fun localIpv4Address(context: Context): String {
        if (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator")) {
            return "10.0.2.2"
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.allNetworks.asSequence()
            .filter { network ->
                connectivity.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            .flatMap { network ->
                connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().asSequence()
            }
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
            ?: error("No local Wi-Fi address is available")
    }

    private data class Metadata(val name: String, val mimeType: String, val size: Long?)

    private fun readMetadata(context: Context, uri: Uri): Metadata {
        if (uri.scheme == "file") {
            val file = File(checkNotNull(uri.path))
            require(file.isFile) { "Attachment file is unavailable" }
            return Metadata(
                name = file.name.take(180).ifBlank { "secure-attachment.bin" },
                mimeType = java.net.URLConnection.guessContentTypeFromName(file.name)
                    ?: "application/octet-stream",
                size = file.length(),
            )
        }
        var displayName: String? = null
        var size: Long? = null
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val safeName = displayName.orEmpty()
            .substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "_")
            .trim().take(180)
            .ifBlank { "secure-attachment.bin" }
        return Metadata(
            name = safeName,
            mimeType = context.contentResolver.getType(uri)?.take(120) ?: "application/octet-stream",
            size = size,
        )
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun ByteArray.toUrlBase64(): String =
        android.util.Base64.encodeToString(
            this,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
        )

    private fun String.fromUrlBase64(): ByteArray = android.util.Base64.decode(
        this,
        android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(BufferedInputStream(input, BUFFER_SIZE), digest).use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (stream.read(buffer) >= 0) Unit
        }
        digest.digest().toHex()
    }
}
