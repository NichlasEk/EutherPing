package se.apothictech.eutherping.secure

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object BluetoothAttachmentTransport {
    private const val SERVICE_NAME = "EutherPing Secure Attachment"
    private const val CONNECT_TIMEOUT_MS = 8_000L
    private const val MAX_REQUEST_BYTES = 16 * 1024
    private const val BUFFER_SIZE = 64 * 1024
    private val serviceUuid = UUID.fromString("a81cc6f8-31ea-4e25-969d-a58b84ffaf20")
    private val serverRunning = AtomicBoolean(false)

    val runtimePermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

    fun hasPermission(context: Context): Boolean = runtimePermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @SuppressLint("MissingPermission")
    fun status(context: Context): String {
        if (!hasPermission(context)) return "ACCESS REQUIRED"
        val adapter = adapter(context) ?: return "UNAVAILABLE"
        if (!adapter.isEnabled) return "DISABLED"
        return if (adapter.bondedDevices.isEmpty()) "PAIR A PHONE" else "READY"
    }

    @SuppressLint("MissingPermission")
    fun localDeviceName(context: Context): String? {
        if (!hasPermission(context)) return null
        return adapter(context)?.takeIf(BluetoothAdapter::isEnabled)?.name?.take(120)
    }

    @SuppressLint("MissingPermission")
    fun ensureServerStarted(context: Context): Result<Unit> = runCatching {
        check(hasPermission(context)) { "Bluetooth access is not granted" }
        if (serverRunning.get()) return@runCatching
        val adapter = checkNotNull(adapter(context)) { "Bluetooth is unavailable on this phone" }
        check(adapter.isEnabled) { "Enable Bluetooth on both phones" }
        check(adapter.bondedDevices.isNotEmpty()) {
            "Pair both phones in Android Bluetooth settings first"
        }
        synchronized(this) {
            if (serverRunning.get()) return@synchronized
            val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, serviceUuid)
            serverRunning.set(true)
            Thread(
                {
                    try {
                        while (true) {
                            val socket = server.accept()
                            Thread(
                                { serveOne(context.applicationContext, socket) },
                                "EutherPing-bluetooth-client",
                            ).apply {
                                isDaemon = true
                                start()
                            }
                        }
                    } catch (_: Throwable) {
                        serverRunning.set(false)
                        runCatching { server.close() }
                    }
                },
                "EutherPing-bluetooth-server",
            ).apply {
                isDaemon = true
                start()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun download(
        context: Context,
        address: String,
        descriptor: SecureAttachmentDescriptor,
        partial: File,
    ): Result<Unit> = runCatching {
        check(descriptor.bluetoothAvailable) { "This offer does not include Bluetooth" }
        check(hasPermission(context)) {
            "Grant Nearby devices access, then pair both phones in Android Bluetooth settings"
        }
        val adapter = checkNotNull(adapter(context)) { "Bluetooth is unavailable on this phone" }
        check(adapter.isEnabled) { "Enable Bluetooth on both phones" }
        val devices = adapter.bondedDevices.sortedWith(
            compareByDescending<android.bluetooth.BluetoothDevice> {
                descriptor.bluetoothName != null && it.name == descriptor.bluetoothName
            }.thenBy { it.name.orEmpty() },
        )
        check(devices.isNotEmpty()) { "Pair both phones in Android Bluetooth settings first" }
        val proof = SecureRepository.signAttachmentRequest(
            context,
            descriptor.id,
            descriptor.transportToken,
        )
        var lastError: Throwable? = null
        for (device in devices) {
            partial.delete()
            val attempt = runCatching {
                val socket = device.createRfcommSocketToServiceRecord(serviceUuid)
                connectWithTimeout(socket)
                socket.use { connected ->
                    val output = BufferedOutputStream(connected.outputStream, BUFFER_SIZE)
                    output.write(
                        (JSONObject()
                            .put("id", descriptor.id)
                            .put("token", descriptor.transportToken)
                            .put("proof", proof)
                            .toString() + "\n").toByteArray(Charsets.UTF_8),
                    )
                    output.flush()
                    val input = BufferedInputStream(connected.inputStream, BUFFER_SIZE)
                    val response = readLine(input)
                    check(response == "OK ${descriptor.ciphertextSize}") {
                        if (response.startsWith("ERR ")) response.removePrefix("ERR ")
                        else "Bluetooth vessel returned an invalid response"
                    }
                    BufferedOutputStream(partial.outputStream(), BUFFER_SIZE).use { fileOutput ->
                        var received = 0L
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (received < descriptor.ciphertextSize) {
                            val count = input.read(
                                buffer,
                                0,
                                minOf(buffer.size.toLong(), descriptor.ciphertextSize - received).toInt(),
                            )
                            check(count >= 0) { "Bluetooth attachment was incomplete" }
                            fileOutput.write(buffer, 0, count)
                            received += count
                        }
                    }
                }
            }
            if (attempt.isSuccess) return@runCatching
            lastError = attempt.exceptionOrNull()
        }
        throw IllegalStateException(
            "No paired Bluetooth vessel accepted the attachment. Keep EutherPing open on the sender.",
            lastError,
        )
    }

    @SuppressLint("MissingPermission")
    private fun serveOne(context: Context, socket: BluetoothSocket) {
        runCatching {
            socket.use { client ->
                val request = JSONObject(readLine(client.inputStream))
                val id = request.getString("id")
                val token = request.getString("token")
                val proof = request.getString("proof")
                val file = SecureAttachmentRepository.authorizedTransfer(context, id, token, proof)
                val output = BufferedOutputStream(client.outputStream, BUFFER_SIZE)
                if (file == null) {
                    output.write("ERR Unauthorized or expired attachment\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                    return@use
                }
                output.write("OK ${file.length()}\n".toByteArray(Charsets.UTF_8))
                file.inputStream().use { it.copyTo(output, BUFFER_SIZE) }
                output.flush()
            }
        }
    }

    private fun connectWithTimeout(socket: BluetoothSocket) {
        val connected = AtomicBoolean(false)
        Thread {
            try {
                Thread.sleep(CONNECT_TIMEOUT_MS)
                if (!connected.get()) socket.close()
            } catch (_: Throwable) {
                Unit
            }
        }.apply {
            isDaemon = true
            start()
        }
        try {
            socket.connect()
            connected.set(true)
        } catch (error: Throwable) {
            throw if (error is SocketException) {
                IllegalStateException("Bluetooth connection timed out or was refused", error)
            } else {
                error
            }
        }
    }

    private fun readLine(input: InputStream): String {
        val bytes = ArrayList<Byte>()
        while (bytes.size < MAX_REQUEST_BYTES) {
            val value = input.read()
            check(value >= 0) { "Bluetooth vessel closed before responding" }
            if (value == '\n'.code) return bytes.toByteArray().toString(Charsets.UTF_8).trimEnd('\r')
            bytes += value.toByte()
        }
        error("Bluetooth request exceeded the size limit")
    }

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
}
