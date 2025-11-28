package com.lalilu.lcanvas

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform