package com.santiagojorda.mediasync.media

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler

/**
 * Desde API 30 el sistema entrega la URI puntual del ítem modificado en el overload de
 * colección, así que no hace falta re-consultar toda la colección para encontrar qué cambió.
 */
class MediaChangeObserver(
    handler: Handler,
    private val onMediaChanged: (Uri) -> Unit,
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
        uris.forEach(onMediaChanged)
    }
}
