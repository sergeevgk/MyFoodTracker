package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.BackupErrorReason
import com.example.myfoodtracker.domain.model.BackupFacts
import com.example.myfoodtracker.domain.model.BackupValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM tests for the validation decision table — no MockK, no Robolectric.
 *
 * Every check is exercised on its own, plus the *order* of the checks, because
 * the user-visible reason is the first failure and a wrong order hides the real
 * problem (a text file must be "not a backup", not a version mismatch).
 */
class BackupDatabaseUseCaseTest {

    private val useCase = BackupDatabaseUseCase()

    // --- File name -------------------------------------------------------------------

    @Test
    fun backupFileName_usesIsoLocalDateAndDbSuffix() {
        assertEquals(
            "myfoodtracker_backup_2026-10-21.db",
            useCase.backupFileName(LocalDate.of(2026, 10, 21))
        )
    }

    @Test
    fun backupFileName_hasNoSpacesAndIsLowercase() {
        val fileName = useCase.backupFileName(LocalDate.of(2026, 1, 2))

        assertTrue(fileName, fileName.none { it.isWhitespace() })
        assertEquals(fileName, fileName.lowercase())
        assertTrue(fileName, fileName.endsWith(".db"))
    }

    // --- Happy path ------------------------------------------------------------------

    @Test
    fun validFacts_returnValid() {
        assertEquals(BackupValidation.Valid, useCase(validFacts()))
    }

    // --- Check 1: opens as SQLite and carries the meta table --------------------------

    @Test
    fun unreadableFile_returnsNotADatabase() {
        assertInvalid(
            BackupErrorReason.NOT_A_DATABASE,
            validFacts().copy(fileReadable = false)
        )
    }

    @Test
    fun missingMetaTable_returnsNotADatabase() {
        assertInvalid(
            BackupErrorReason.NOT_A_DATABASE,
            validFacts().copy(metaTablePresent = false)
        )
    }

    @Test
    fun notADatabase_winsOverWrongFormatVersion() {
        assertInvalid(
            BackupErrorReason.NOT_A_DATABASE,
            validFacts().copy(
                fileReadable = false,
                metaTablePresent = false,
                formatVersion = 99
            )
        )
    }

    // --- Check 2: integrity_check -----------------------------------------------------

    @Test
    fun failedIntegrityCheck_returnsIntegrityCheckFailed() {
        assertInvalid(
            BackupErrorReason.INTEGRITY_CHECK_FAILED,
            validFacts().copy(integrityOk = false)
        )
    }

    // --- Check 3: backup format version ----------------------------------------------

    @Test
    fun wrongFormatVersion_returnsUnsupportedBackupVersion() {
        assertInvalid(
            BackupErrorReason.UNSUPPORTED_BACKUP_VERSION,
            validFacts().copy(formatVersion = 2)
        )
    }

    @Test
    fun missingFormatVersion_returnsUnsupportedBackupVersion() {
        assertInvalid(
            BackupErrorReason.UNSUPPORTED_BACKUP_VERSION,
            validFacts().copy(formatVersion = null)
        )
    }

    // --- Check 4: self-consistency of the declared schema version ---------------------

    @Test
    fun declaredUserVersionNotMatchingActual_returnsIncompleteBackup() {
        assertInvalid(
            BackupErrorReason.INCOMPLETE_BACKUP,
            validFacts().copy(declaredUserVersion = 3)
        )
    }

    @Test
    fun missingDeclaredUserVersion_returnsIncompleteBackup() {
        assertInvalid(
            BackupErrorReason.INCOMPLETE_BACKUP,
            validFacts().copy(declaredUserVersion = null)
        )
    }

    // --- Check 5: schema version matches the running app ------------------------------

    @Test
    fun actualUserVersionNotMatchingApp_returnsSchemaVersionMismatch() {
        assertInvalid(
            BackupErrorReason.SCHEMA_VERSION_MISMATCH,
            validFacts().copy(
                declaredUserVersion = 3,
                actualUserVersion = 3
            )
        )
    }

    @Test
    fun schemaVersionMismatch_winsOverIdentityHashMismatch() {
        assertInvalid(
            BackupErrorReason.SCHEMA_VERSION_MISMATCH,
            validFacts().copy(
                declaredUserVersion = 3,
                actualUserVersion = 3,
                candidateIdentityHash = "other"
            )
        )
    }

    // --- Check 6: required tables ------------------------------------------------------

    @Test
    fun missingTables_returnsMissingTables() {
        assertInvalid(
            BackupErrorReason.MISSING_TABLES,
            validFacts().copy(missingTables = listOf("water_logs"))
        )
    }

    // --- Check 7: Room identity hash ---------------------------------------------------

    @Test
    fun identityHashMismatch_returnsSchemaMismatch() {
        assertInvalid(
            BackupErrorReason.SCHEMA_MISMATCH,
            validFacts().copy(candidateIdentityHash = "different")
        )
    }

    @Test
    fun missingCandidateIdentityHash_returnsSchemaMismatch() {
        assertInvalid(
            BackupErrorReason.SCHEMA_MISMATCH,
            validFacts().copy(candidateIdentityHash = null)
        )
    }

    @Test
    fun missingLiveDatabase_skipsIdentityHashCheck() {
        assertEquals(
            BackupValidation.Valid,
            useCase(validFacts().copy(liveIdentityHash = null))
        )
    }

    @Test
    fun missingLiveDatabase_stillChecksTheSchemaVersion() {
        assertInvalid(
            BackupErrorReason.SCHEMA_VERSION_MISMATCH,
            validFacts().copy(
                liveIdentityHash = null,
                declaredUserVersion = 3,
                actualUserVersion = 3
            )
        )
    }

    // --- Check 8: custom-food count ----------------------------------------------------

    @Test
    fun customFoodCountMismatch_returnsIncompleteBackup() {
        assertInvalid(
            BackupErrorReason.INCOMPLETE_BACKUP,
            validFacts().copy(declaredCustomFoodCount = 5)
        )
    }

    @Test
    fun missingDeclaredCustomFoodCount_returnsIncompleteBackup() {
        assertInvalid(
            BackupErrorReason.INCOMPLETE_BACKUP,
            validFacts().copy(declaredCustomFoodCount = null)
        )
    }

    @Test
    fun identityHashCheck_winsOverCustomFoodCountCheck() {
        assertInvalid(
            BackupErrorReason.SCHEMA_MISMATCH,
            validFacts().copy(
                candidateIdentityHash = "different",
                declaredCustomFoodCount = 5
            )
        )
    }

    @Test
    fun backupWithoutCustomFoods_isValid() {
        assertEquals(
            BackupValidation.Valid,
            useCase(
                validFacts().copy(
                    declaredCustomFoodCount = 0,
                    actualCustomFoodCount = 0
                )
            )
        )
    }

    // --- Helpers ---------------------------------------------------------------------

    private fun validFacts() = BackupFacts(
        fileReadable = true,
        metaTablePresent = true,
        integrityOk = true,
        formatVersion = BackupDatabaseUseCase.FORMAT_VERSION,
        declaredUserVersion = BackupDatabaseUseCase.EXPECTED_USER_VERSION,
        actualUserVersion = BackupDatabaseUseCase.EXPECTED_USER_VERSION,
        expectedUserVersion = BackupDatabaseUseCase.EXPECTED_USER_VERSION,
        missingTables = emptyList(),
        candidateIdentityHash = "identity-hash",
        liveIdentityHash = "identity-hash",
        declaredCustomFoodCount = 2,
        actualCustomFoodCount = 2
    )

    private fun assertInvalid(expected: BackupErrorReason, facts: BackupFacts) {
        val result = useCase(facts)
        assertTrue("Expected Invalid but was $result", result is BackupValidation.Invalid)
        assertEquals(expected, (result as BackupValidation.Invalid).reason)
    }
}
