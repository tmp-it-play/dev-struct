package team.themoment.dev_struct

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrganizerTest {
    @Test
    fun `GitHub remote 파싱`() {
        assertEquals(
            RepoRef("it-play", "kmp-playground"),
            parseGitHubRemote("git@github.com:it-play/kmp-playground.git"),
        )
        assertEquals(RepoRef("InnoAscend", "NETAMOD"), parseGitHubRemote("https://github.com/InnoAscend/NETAMOD"))
        assertEquals(RepoRef("a", "b"), parseGitHubRemote("ssh://git@github.com/a/b.git/"))
        assertNull(parseGitHubRemote("https://gitlab.com/a/b.git"))
    }

    @Test
    fun `remote 교체 시 프로토콜과 git 접미사 유지`() {
        val to = RepoRef("new-owner", "new-name")
        assertEquals("git@github.com:new-owner/new-name.git", rewriteGitHubRemote("git@github.com:old/repo.git", to))
        assertEquals("https://github.com/new-owner/new-name", rewriteGitHubRemote("https://github.com/old/repo", to))
    }

    @Test
    fun `fork는 OpenSource, 나머지는 owner 폴더에 레포명으로`() {
        val root = Path.of("/r")
        assertEquals(
            Path.of("/r/OpenSource/mockk"),
            targetPath(root, GitHubRepo(1, RepoRef("me", "mockk"), fork = true)),
        )
        assertEquals(
            Path.of("/r/it-play/kmp-playground"),
            targetPath(root, GitHubRepo(2, RepoRef("it-play", "kmp-playground"), fork = false)),
        )
    }

    @Test
    fun `JetBrains 경로는 모든 표기를 경계 단위로 교체`() {
        val xml =
            """
            <entry key="${'$'}USER_HOME${'$'}/P/a/foo">
            <RecentProjectMetaInfo frameTitle="foo [~/P/a/foo] – x" />
            <entry key="/h/P/a/foo/sub" />
            <entry key="${'$'}USER_HOME${'$'}/P/a/foobar" value="true" />
            """.trimIndent()
        val expected =
            """
            <entry key="${'$'}USER_HOME${'$'}/P/b/foo">
            <RecentProjectMetaInfo frameTitle="foo [~/P/b/foo] – x" />
            <entry key="/h/P/b/foo/sub" />
            <entry key="${'$'}USER_HOME${'$'}/P/a/foobar" value="true" />
            """.trimIndent()
        assertEquals(expected, replaceProjectPath(xml, "/h/P/a/foo", "/h/P/b/foo", "/h"))
    }
}

class UpdaterTest {
    @Test
    fun `버전 비교`() {
        kotlin.test.assertTrue(isNewer("v1.0.1", "1.0.0"))
        kotlin.test.assertTrue(isNewer("v1.10.0", "1.9.9"))
        kotlin.test.assertTrue(isNewer("v2", "1.9.9"))
        kotlin.test.assertFalse(isNewer("v1.0.0", "1.0.0"))
        kotlin.test.assertFalse(isNewer("v0.9.0", "1.0.0"))
    }
}
