package io.nekohasekai.sagernet.doh

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DnsPadding {

    private const val OPT_RESOURCE_PADDING_CODE = 12
    private const val PADDING_BLOCK_SIZE = 128 // RFC8467 recommendation

    // Simplified DNS message structure for padding calculation
    // This is a very basic representation and might need to be expanded
    // if more complex DNS message parsing is required.
    private fun getDnsMessageLength(rawMsg: ByteArray): Int {
        // In a real scenario, you would parse the DNS message to get its actual length
        // For now, we'll assume the rawMsg length is sufficient for padding calculation
        return rawMsg.size
    }

    // Computes the number of padding bytes needed.
    private fun computePaddingSize(msgLen: Int, blockSize: Int): Int {
        // We'll always be adding a new padding header inside the OPT RR's data.
        // In Go, this was kOptPaddingHeaderLen, which is 4 bytes (2 for code, 2 for length).
        val extraPadding = 4

        val padSize = blockSize - (msgLen + extraPadding) % blockSize
        return padSize % blockSize
    }

    // Adds EDNS padding to a raw DNS message.
    fun addEdnsPadding(rawMsg: ByteArray): ByteArray {
        // This is a simplified implementation. A full implementation would need
        // to parse the DNS message, check for existing OPT RRs, and add padding
        // accordingly, similar to the Go `dnsmessage` package.

        val currentMsgLen = getDnsMessageLength(rawMsg)
        val paddingBytesNeeded = computePaddingSize(currentMsgLen, PADDING_BLOCK_SIZE)

        if (paddingBytesNeeded == 0) {
            return rawMsg // No padding needed
        }

        // For simplicity, we'll just append the padding bytes.
        // In a real DNS message, this padding would be part of an EDNS(0) OPT record.
        val paddedMsg = ByteBuffer.allocate(rawMsg.size + paddingBytesNeeded)
        paddedMsg.put(rawMsg)
        for (i in 0 until paddingBytesNeeded) {
            paddedMsg.put(0) // Fill with zeros
        }

        return paddedMsg.array()
    }
}


