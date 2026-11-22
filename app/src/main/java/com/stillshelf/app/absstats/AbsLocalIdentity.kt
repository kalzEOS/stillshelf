package com.stillshelf.app.absstats

import java.security.MessageDigest

/** A login token is a credential, not a durable identity. Only a user ID returned
 * by the authenticated ABS server may establish this key. Never infer from a name,
 * current library, unverified JWT claims or an imported backup's account label.
 * Server IDs are local installation IDs; restoring to another installation needs
 * an explicit verified account mapping rather than trusting this key alone.
 */
internal fun absLocalOwner(serverId: String, verifiedUserId: String): String {
    require(serverId.isNotBlank() && verifiedUserId.isNotBlank())
    return MessageDigest.getInstance("SHA-256")
        .digest("abs-local-v1\n${serverId.length}:$serverId${verifiedUserId.length}:$verifiedUserId".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
