package org.TheIza.fortrush.data.nickname

import org.junit.Assert.assertEquals
import org.junit.Test

class NicknamePolicyTest {
    @Test
    fun sharedValidationVectorsMatchAndroidPolicy() {
        val stream = javaClass.getResourceAsStream("/nickname-vectors.tsv")
            ?: error("Shared nickname validation vectors were not found")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val columns = line.split('\t')
                val input = columns[0]
                val expectedValid = columns[1].toBoolean()
                val result = NicknamePolicy.validate(input)
                assertEquals("Validity for $input", expectedValid, result is NicknameValidation.Valid)
                if (result is NicknameValidation.Valid) assertEquals("Normalization for $input", columns[2], result.normalized)
                else if (result is NicknameValidation.Invalid) assertEquals("Error for $input", columns[2], when (result.error) {
                    NicknameError.TOO_SHORT -> "NICKNAME_TOO_SHORT"
                    NicknameError.TOO_LONG -> "NICKNAME_TOO_LONG"
                    NicknameError.INVALID_CHARACTERS -> "NICKNAME_INVALID_CHARACTERS"
                    NicknameError.REQUIRED -> "NICKNAME_REQUIRED"
                })
            }
        }
    }
}
