package team.themoment.dev_struct

import kotlinx.coroutines.channels.Channel
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Path

class MacNotifications(bundle: Path) {
    val opens = Channel<Page>(Channel.CONFLATED)

    private val linker = Linker.nativeLinker()
    private val library = SymbolLookup.libraryLookup(
        bundle.resolve("Contents/Frameworks/libDevStructNotifications.dylib"),
        Arena.global(),
    )
    private val post = linker.downcallHandle(
        library.find("postNotification").orElseThrow(),
        FunctionDescriptor.ofVoid(ADDRESS, ADDRESS),
    )

    init {
        val signature = FunctionDescriptor.ofVoid(ADDRESS)
        val handler = MethodHandles.lookup().findVirtual(
            javaClass,
            "onOpen",
            MethodType.methodType(Void.TYPE, MemorySegment::class.java),
        ).bindTo(this)
        val install = linker.downcallHandle(library.find("installNotificationHandler").orElseThrow(), signature)
        install.invoke(linker.upcallStub(handler, signature, Arena.global()))
    }

    @Suppress("unused") // Called by the notification center on a native thread.
    private fun onOpen(page: MemorySegment) {
        val name = page.reinterpret(Long.MAX_VALUE).getString(0)
        Page.entries.firstOrNull { it.name == name }?.let { opens.trySend(it) }
    }

    fun send(message: String, page: Page) {
        Arena.ofConfined().use { arena ->
            post.invoke(arena.allocateFrom(message), arena.allocateFrom(page.name))
        }
    }
}
