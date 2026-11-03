package app.minguo.calendar

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 完全替代模式下的锁屏密码（只存加盐哈希）。
 * 两种类型：数字密码（4–8 位）、图案（连接 4 个以上的点，记为点序号 0–8 组成的字符串）。
 */
object Security {
    const val TYPE_PIN = 0
    const val TYPE_PATTERN = 1

    fun hasPin(context: Context) = Prefs(context).pinHash.isNotEmpty()

    fun type(context: Context) = Prefs(context).pinType

    fun setPin(context: Context, pin: String, type: Int = TYPE_PIN) {
        val p = Prefs(context)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p.pinSalt = salt
        p.pinHash = hash(salt, pin)
        p.pinLength = pin.length
        p.pinType = type
    }

    fun clearPin(context: Context) {
        val p = Prefs(context)
        p.pinHash = ""
        p.pinSalt = ""
        p.pinLength = 0
        p.pinType = TYPE_PIN
    }

    fun check(context: Context, pin: String): Boolean {
        val p = Prefs(context)
        if (p.pinHash.isEmpty()) return true
        return MessageDigest.isEqual(hash(p.pinSalt, pin).toByteArray(), p.pinHash.toByteArray())
    }

    private fun hash(salt: String, pin: String): String {
        var bytes = (salt + pin).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        repeat(10_000) { bytes = md.digest(bytes) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
