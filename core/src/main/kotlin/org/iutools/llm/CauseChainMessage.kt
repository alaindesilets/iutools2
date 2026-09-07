package org.iutools.llm

/*
 * Flattens an exception together with its chain of causes into one
 * readable line.
 *
 * Some clients -- HTTP SDKs especially -- wrap the real failure several
 * layers deep behind a fixed, unhelpful top-level message (e.g. "Request
 * failed"), with the actual reason ("Unable to resolve host ...") only on a
 * nested cause. Walking the chain and joining the distinct messages with
 * ": " is what turns that into something a person can act on. Falls back to
 * the exception's own toString() when nothing in the chain has a message.
 */
fun causeChainMessage(error: Throwable): String =
    generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .distinct()
        .joinToString(": ")
        .ifBlank { error.toString() }
