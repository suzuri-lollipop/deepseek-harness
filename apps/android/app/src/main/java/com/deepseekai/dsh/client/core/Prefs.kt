package com.deepseekai.dsh.client.core

import android.content.Context

/** Persists the server URL the user last entered. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("dsh-client", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = sp.edit().putString(KEY_URL, value).apply()

    companion object {
        private const val KEY_URL = "server_url"

        /** The tailnet entrypoint documented in start-web-tailscale.bat. */
        const val DEFAULT_URL = "https://server-4.tail82719.ts.net"
    }
}
