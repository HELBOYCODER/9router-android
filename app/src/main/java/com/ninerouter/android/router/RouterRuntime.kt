package com.ninerouter.android.router

import android.content.Context
import android.content.res.AssetManager
import com.ninerouter.android.util.AppLogger
import java.io.File
import java.io.FileOutputStream

/**
 * Extracts the embedded Node.js runtime + bundled 9Router standalone build from the
 * APK assets into the app's private storage on first use.
 *
 * Expected asset layout under `assets/9router-runtime/`:
 *   bin/node                      -> Node.js executable for android-arm64 (or lib/)
 *   lib/<shared-libraries>        -> shared libraries Node needs (glibc/Bionic deps)
 *   bundle/...                    -> the `.next/standalone` build:
 *                                     custom-server.js, server.js, node_modules/,
 *                                     .next/static, public/
 *
 * `scripts/package_runtime.sh` (host-side) assembles this layout before `assemble*`.
 */
object RouterRuntime {

    private const val TAG = "RouterRuntime"
    private const val ASSET_ROOT = "9router-runtime"

    fun runtimeRoot(context: Context): File = File(context.filesDir, "9router/runtime")
    fun bundleDir(context: Context): File = File(runtimeRoot(context), "bundle")
    fun binDir(context: Context): File = File(runtimeRoot(context), "bin")
    fun nodeExecutable(context: Context): File = File(binDir(context), "node")
    fun libDir(context: Context): File = File(runtimeRoot(context), "lib")

    /** True once the node executable + bundle are on disk. */
    fun isPresent(context: Context): Boolean =
        nodeExecutable(context).exists() && File(bundleDir(context), "custom-server.js").exists()

    /**
     * Copy the runtime from assets if it isn't extracted yet. Returns true when the
     * runtime is available. Safe to call repeatedly (idempotent, skips when present).
     */
    fun extractIfNeeded(context: Context): Boolean {
        if (isPresent(context)) return true
        return runCatching {
            val am: AssetManager = context.assets
            val root = runtimeRoot(context)
            if (root.exists()) root.deleteRecursively()
            root.mkdirs()
            copyAssetTree(am, ASSET_ROOT, root)
            // Node needs +x on itself.
            nodeExecutable(context).setExecutable(true, true)
            AppLogger.i(TAG, "Embedded 9Router runtime extracted to ${root.absolutePath}")
            true
        }.getOrElse {
            AppLogger.e(TAG, "Failed to extract embedded runtime: ${it.message}")
            false
        }
    }

    private fun copyAssetTree(am: AssetManager, assetPath: String, dest: File) {
        val children = am.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // It's a file.
            dest.parentFile?.mkdirs()
            am.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } else {
            dest.mkdirs()
            for (child in children) {
                copyAssetTree(am, "$assetPath/$child", File(dest, child))
            }
        }
    }
}
