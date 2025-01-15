package app.mindspaces.clipboard.utils

import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

val AlphaNumCharset = ('a'..'z') + ('A'..'Z') + ('0'..'9')
val NumCharset = ('0'..'9')

fun getRandomString(length: Int, charset: List<Char>) =
    List(length) { charset.random() }.joinToString("")

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }
}

object ByteArraySerializer : KSerializer<ByteArray> {
    // For serial name ByteArray there already exist ByteArraySerializer
    override val descriptor = PrimitiveSerialDescriptor("ByteArray2", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteArray {
        return decoder.decodeString().decodeBase64Bytes()
    }

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(value.encodeBase64())
    }
}
