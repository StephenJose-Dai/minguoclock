package club.daishenghui.minguo

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** 完全替代模式下的锁屏数字密码（只存加盐哈希） */
object Security {
    fun hasPin(context: Context) = Prefs(context).pinHash.isNotEmpty()

    fun setPin(context: Context, pin: String) {
        val p = Prefs(context)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p.pinSalt = salt
        p.pinHash = hash(salt, pin)
        p.pinLength = pin.length
    }

    fun clearPin(context: Context) {
        val p = Prefs(context)
        p.pinHash = ""
        p.pinSalt = ""
        p.pinLength = 0
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
