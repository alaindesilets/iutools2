package org.iutools.lib

/**
 * Minimal thread-safe LRU cache, replacing Caffeine. Caffeine's static
 * initializer calls java.lang.System.getLogger(), a JDK 9+ Platform Logging
 * API method that Android's java.lang.System does not implement — it
 * crashes with NoSuchMethodError at class-load time on a real Android
 * device/emulator (compiles fine, only fails at runtime).
 *
 * Uses java.util.LinkedHashMap's access-order mode — fine on every current
 * target (Android and Desktop are both JVM). Only relevant if iOS is ever
 * added (not planned): it would need a from-scratch implementation.
 */
class SimpleLruCache<K, V>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun getIfPresent(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    @Synchronized
    fun invalidate(key: K) {
        map.remove(key)
    }
}
