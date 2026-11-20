package com.music.bitchord.ui

/** Main-thread action ownership. A late callback must never finish a newer sheet's work. */
class ReorderActionGate {
    class Ticket internal constructor(val id: Long, val listenerGeneration: Long)
    private var nextId = 0L
    private var current: Ticket? = null

    fun begin(listenerGeneration: Long): Ticket =
        Ticket(++nextId, listenerGeneration).also { current = it }

    fun isCurrent(ticket: Ticket, listenerGeneration: Long): Boolean =
        current == ticket && ticket.listenerGeneration == listenerGeneration

    fun close() { current = null }

    /** False when idle or when a newer action already belongs to this listener. */
    fun invalidateForListener(listenerGeneration: Long): Boolean {
        val active = current ?: return false
        if (active.listenerGeneration == listenerGeneration) return false
        current = null
        return true
    }
}
