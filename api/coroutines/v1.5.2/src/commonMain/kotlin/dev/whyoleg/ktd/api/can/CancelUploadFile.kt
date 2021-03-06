@file:Suppress(
    "unused"
)
@file:UseExperimental(
    BotsOnly::class,
    TestingOnly::class
)

package dev.whyoleg.ktd.api.can

import dev.whyoleg.ktd.*
import dev.whyoleg.ktd.api.*
import dev.whyoleg.ktd.api.TdApi.*

/**
 * Stops the uploading of a file
 * Supported only for files uploaded by using uploadFile
 * For other files the behavior is undefined
 *
 * @fileId - Identifier of the file to stop uploading
 */
suspend fun TelegramClient.cancelUploadFile(
    fileId: Int = 0
): Ok = can(
    CancelUploadFile(
        fileId
    )
)

/**
 * Stops the uploading of a file
 * Supported only for files uploaded by using uploadFile
 * For other files the behavior is undefined
 */
suspend fun TelegramClient.can(
    f: CancelUploadFile
): Ok = exec(f) as Ok
