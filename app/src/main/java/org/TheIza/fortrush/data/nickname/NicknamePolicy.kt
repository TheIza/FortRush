package org.TheIza.fortrush.data.nickname

import java.text.Normalizer
import java.util.Locale

object NicknamePolicy {
    private val allowedCharacters = Regex("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9_]+$")

    fun validate(input: String): NicknameValidation {
        val nickname = Normalizer.normalize(input, Normalizer.Form.NFC)
        val codePoints = nickname.codePointCount(0, nickname.length)
        if (codePoints < 3) return NicknameValidation.Invalid(NicknameError.TOO_SHORT)
        if (codePoints > 16) return NicknameValidation.Invalid(NicknameError.TOO_LONG)
        if (!allowedCharacters.matches(nickname)) return NicknameValidation.Invalid(NicknameError.INVALID_CHARACTERS)
        return NicknameValidation.Valid(nickname, nickname.lowercase(Locale.ROOT))
    }
}

sealed interface NicknameValidation {
    data class Valid(val nickname: String, val normalized: String) : NicknameValidation
    data class Invalid(val error: NicknameError) : NicknameValidation
}

enum class NicknameError { REQUIRED, TOO_SHORT, TOO_LONG, INVALID_CHARACTERS }
