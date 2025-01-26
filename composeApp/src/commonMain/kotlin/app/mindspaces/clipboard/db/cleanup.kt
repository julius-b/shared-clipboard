package app.mindspaces.clipboard.db

import co.touchlab.kermit.Logger

// full cleanup is only necessary if server lost data
fun cleanup(db: Database, full: Boolean = false) {
    Logger.withTag("cleanup").i { "cleanup(full=$full)..." }
    db.transaction {
        // TODO cleanup user-data (but most id device-specific not accounts-specific)
        db.accountPropertyQueries.truncate()
        db.authSessionQueries.truncate()
        db.installationQueries.truncateLinks()
        db.accountQueries.truncate()

        // media synced status likely lost as well
        if (full) {
            // clip cannot be re-indexed -> keep sync-status separate
            // keep local-only note
            db.mediaQueries.truncate()
            db.mediaReceiptQueries.truncate()
            db.mediaRequestQueries.truncate()
            db.installationQueries.truncate()
        }
    }
}
