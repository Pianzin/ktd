@file:Suppress("unused")

package dev.whyoleg.ktd.api.public

import dev.whyoleg.ktd.*
import dev.whyoleg.ktd.api.TdApi.*

/**
 * Returns a public HTTPS link to a message. Available only for messages in public supergroups and channels
 */
suspend fun TelegramClient.public(
    f: GetPublicMessageLink
): PublicMessageLink = execRaw(f) as PublicMessageLink