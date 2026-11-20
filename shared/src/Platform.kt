package team.themoment.dev_struct

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
