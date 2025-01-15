package app.mindspaces.clipboard.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.mindspaces.clipboard.db.Database
import app.mindspaces.clipboard.db.Note
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID

@Inject
@SingleIn(AppScope::class)
class NoteRepository(db: Database, private val client: HttpClient) {
    private val log = Logger.withTag("NoteRepo")

    private val noteQueries = db.noteQueries

    // .onStart { delay(5.seconds) }
    fun list() = noteQueries.list().asFlow().mapToList(Dispatchers.IO)

    fun save(text: String): Note {
        val note = Note(UUID.randomUUID(), null, text.trim(), Clock.System.now(), null)
        noteQueries.insert(note)
        log.d { "save - new: $note" }
        return note
    }
}
