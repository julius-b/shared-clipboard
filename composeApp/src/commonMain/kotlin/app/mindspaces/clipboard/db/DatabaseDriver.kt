package app.mindspaces.clipboard.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.uuid.Uuid

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): Database {
    Logger.withTag("createDatabase").d { "init (driver: $driverFactory)" }

    val driver = driverFactory.createDriver()
    val database = Database(
        driver,
        Account.Adapter(
            UUIDAdapter,
            EnumColumnAdapter(),
            EnumColumnAdapter(),
            UUIDAdapter,
            UUIDAdapter,
            dateAdapter,
            dateAdapter
        ),
        AccountProperty.Adapter(
            UUIDAdapter,
            UUIDAdapter,
            UUIDAdapter,
            EnumColumnAdapter(),
            dateAdapter,
            dateAdapter
        ),
        AuthSession.Adapter(
            UUIDAdapter,
            UUIDAdapter,
            UUIDAdapter,
            UUIDAdapter,
            UUIDAdapter,
            dateAdapter,
            dateAdapter
        ),
        Clip.Adapter(UUIDAdapter, dateAdapter),
        DataNotification.Adapter(UUIDAdapter, EnumColumnAdapter()),
        Installation.Adapter(UUIDAdapter, EnumColumnAdapter(), dateAdapter),
        InstallationLink.Adapter(UUIDAdapter, UUIDAdapter, UUIDAdapter, dateAdapter, dateAdapter),
        Media.Adapter(
            UUIDAdapter,
            EnumColumnAdapter(),
            EnumColumnAdapter(),
            UUIDAdapter,
            dateAdapter,
            dateAdapter
        ),
        MediaReceipt.Adapter(UUIDAdapter),
        MediaRequest.Adapter(UUIDAdapter, UUIDAdapter, dateAdapter),
        Note.Adapter(UUIDAdapter, UUIDAdapter, dateAdapter, dateAdapter)
    )

    return database
}

val UUIDAdapter = object : ColumnAdapter<UUID, String> {
    override fun decode(databaseValue: String) = UUID.fromString(databaseValue)

    override fun encode(value: UUID) = value.toString()
}

val UuidAdapter = object : ColumnAdapter<Uuid, String> {
    override fun decode(databaseValue: String) = Uuid.parse(databaseValue)

    override fun encode(value: Uuid) = value.toString()
}

val ubyteArrayAdapter = object : ColumnAdapter<UByteArray, ByteArray> {
    override fun decode(databaseValue: ByteArray) = databaseValue.asUByteArray()

    override fun encode(value: UByteArray) = value.asByteArray()
}

val dateAdapter = object : ColumnAdapter<Instant, String> {
    override fun decode(databaseValue: String): Instant {
        //println("[?] dateAdapter > parsing databaseValue: $databaseValue")
        //return ZonedDateTime.parse(databaseValue)
        return Instant.parse(databaseValue)
    }

    override fun encode(value: Instant): String {
        //println("[?] dateAdapter > parsing date: $value")
        //val str = value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val str = value.toString()
        //println("[?] dateAdapter > formatted: $str")
        return str
    }
}
