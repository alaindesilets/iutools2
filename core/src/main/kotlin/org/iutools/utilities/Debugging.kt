package org.iutools.utilities

/**
 * Stand-in for the original Swing-based debug console. In the original code
 * `actif` is hardcoded false and never flipped at runtime, so `mess(...)`
 * was already a no-op in practice; the AWT/Swing UI is dropped entirely
 * since it's KMP-hostile and dead weight.
 */
object Debugging {
    @JvmStatic
    fun mess(origine: String, no: Int, s: String) {
        // no-op (see class doc)
    }
}
