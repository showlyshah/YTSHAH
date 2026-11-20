package com.viewtube.app.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * A transparent Media3 data-source adapter.
 *
 * Requests are passed through unchanged: no synthetic range parameters, client impersonation,
 * or special handling intended to work around source throttling or access controls.
 */
@UnstableApi
class YtDataSource(private val upstream: DataSource) : DataSource {
    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long = upstream.open(dataSpec)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)
    override fun getUri() = upstream.uri
    override fun getResponseHeaders() = upstream.responseHeaders
    override fun close() = upstream.close()
}
