package team.themoment.dev_struct

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_LONG

/**
 * 어느 앱 위에서든 마우스 버튼이 눌려 있는지. AWT는 다른 앱에서의 클릭을 모르므로
 * `[NSEvent pressedMouseButtons]`를 직접 부른다(손쉬운 사용 권한이 필요 없다).
 */
object GlobalMouse {
    private val call =
        runCatching {
            val linker = Linker.nativeLinker()
            val objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
            val getClass = linker.downcallHandle(
                objc.find("objc_getClass").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS),
            )
            val register = linker.downcallHandle(
                objc.find("sel_registerName").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS),
            )
            val msgSend = linker.downcallHandle(
                objc.find("objc_msgSend").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS),
            )
            val cls = getClass.invokeWithArguments(Arena.global().allocateFrom("NSEvent")) as MemorySegment
            val sel = register.invokeWithArguments(Arena.global().allocateFrom("pressedMouseButtons")) as MemorySegment
            { msgSend.invokeWithArguments(cls, sel) as Long }
        }.getOrNull()

    fun pressed(): Boolean = call?.let { runCatching { it() != 0L }.getOrNull() } ?: false
}
