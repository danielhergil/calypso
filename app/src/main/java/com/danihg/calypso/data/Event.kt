package com.danihg.calypso.data

class Event<out T>(private val content: T) {
    private var hasBeenHandled = false
    /** Devuelve el contenido solo la primera vez */
    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) null
        else {
            hasBeenHandled = true
            content
        }
}