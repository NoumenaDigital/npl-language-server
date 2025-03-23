package com.noumenadigital.npl.lang.server.util

import com.noumenadigital.npl.lang.server.SystemExitHandler

class SafeSystemExitHandler : SystemExitHandler {
    var exitCalled = false
    var statusCode = -1

    override fun exit(status: Int) {
        exitCalled = true
        statusCode = status
    }
}
