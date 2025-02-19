package app.mindspaces.clipboard.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class PathsTest {

    @Test
    fun testMediaName() {
        assertEquals("", "".mediaName)
        assertEquals("name", "name".mediaName)
        assertEquals("", "/".mediaName)
        assertEquals("some file.mp4", "/home/user/Pictures/some file.mp4".mediaName)
    }

    @Test
    fun testFileExt() {
        assertEquals("", "".mediaExt)
        assertEquals("", "name".mediaExt)
        assertEquals("gz", "name.tar.gz".mediaExt)
        assertEquals("", "/".mediaExt)
        assertEquals("mp4", "/home/user/Pictures/some file.mp4".mediaExt)
        assertEquals("jpeg", "/home/dot.dot.dot/some.file.jpeg".mediaExt)
        assertEquals("", "/home/dot.dot.dot/no ext".mediaExt)
    }
}
