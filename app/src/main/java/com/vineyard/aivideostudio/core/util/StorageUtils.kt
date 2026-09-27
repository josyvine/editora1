package com.vineyard.aivideostudio.core.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageUtils {
    fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val path: File = context.filesDir
            val stat = StatFs(path.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    fun hasSufficientSpace(context: Context, requiredBytes: Long): Boolean {
        val available = getAvailableStorageBytes(context)
        return available == -1L || available > requiredBytes
    }
}
