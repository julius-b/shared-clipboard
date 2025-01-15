package app.mindspaces.clipboard.api

import app.mindspaces.clipboard.db.AuthSession
import io.ktor.resources.Resource

fun ApiAuthSession.toEntity() = AuthSession(
    id,
    accountId,
    installationId,
    ioid,
    secretUpdateId,
    refreshToken,
    accessToken,
    createdAt,
    deletedAt
)

@Resource("/auth_sessions")
class AuthSessions {
    @Resource("{id}")
    class Id(
        val parent: AuthSessions = AuthSessions(), val id: SUUID
    )

    @Resource("refresh")
    class Refresh(val parent: AuthSessions = AuthSessions())
}
