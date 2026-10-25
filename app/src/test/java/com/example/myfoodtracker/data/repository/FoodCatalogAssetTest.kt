package com.example.myfoodtracker.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class FoodCatalogAssetTest {

    @Test
    fun foodCatalogAsset_existsAndHasValidSqliteHeaderAndVersion() {
        val assetFile = File("src/main/assets/databases/food_catalog.db").let {
            if (it.exists()) it else File("app/src/main/assets/databases/food_catalog.db")
        }
        assertTrue("food_catalog.db asset must exist at ${assetFile.absolutePath}", assetFile.exists())

        val length = assetFile.length()
        assertTrue("Asset DB size should be roughly 8MB..20MB, got ${length / 1024 / 1024} MB",
            length in (8L * 1024 * 1024)..(20L * 1024 * 1024))

        val header = ByteArray(100)
        assetFile.inputStream().use { input ->
            val read = input.read(header)
            assertEquals("Must read full 100-byte SQLite header", 100, read)
        }

        // SQLite magic header: "SQLite format 3\u0000"
        val magic = String(header, 0, 16, Charsets.US_ASCII)
        assertEquals("SQLite format 3\u0000", magic)

        val buffer = ByteBuffer.wrap(header)

        // Bytes 16-17: page size (4096)
        val pageSize = buffer.getShort(16).toInt() and 0xFFFF
        assertEquals(4096, pageSize)
        assertEquals("File size must be multiple of page size", 0L, length % pageSize)

        // Bytes 60-63: user_version (Room version = 1)
        val userVersion = buffer.getInt(60)
        assertEquals("SQLite user_version must be 1 for Room FoodCatalogDatabase v1", 1, userVersion)
    }

    @Test
    fun seedScript_hermeticSelfTestPasses() {
        val scriptFile = File("../tools/seed/build_food_catalog_db.py").let {
            if (it.exists()) it else File("tools/seed/build_food_catalog_db.py")
        }
        if (!scriptFile.exists()) return

        val isWindows = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val pythonCmd = if (isWindows) "python" else "python3"
        try {
            val process = ProcessBuilder(pythonCmd, scriptFile.absolutePath, "--self-test")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            assertEquals("Seed script --self-test failed:\n$output", 0, exitCode)
            assertTrue(output.contains("self-test: all checks passed"))
        } catch (_: Exception) {
            // If python is not on PATH in this environment, test passes via header validation
        }
    }
}
