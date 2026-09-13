package com.jovi.photoai.data.reference

/** Shared by JUnit and the SDK-free host runner; exercises the actual production helpers. */
internal object KnowledgeBundleCoreRegressionCases {
    fun syntax(): Int {
        var count = 0
        fun expect(text: String, expected: KnowledgeBundleJsonProblem? = null) {
            val actual = StrictKnowledgeBundleJson.problem(text)
            check(actual == expected) { "JSON case ${count + 1}: expected $expected, got $actual" }
            count++
        }
        listOf(
            "{}", " \r\n\t{ }\t", "{\"a\":[]}", "{\"a\":{}}", "{\"a\":[true,false,null]}",
            "{\"中文\":\"窗边自然光\"}", "{\"a\":\"{[]}:,\"}",
            "{\"a\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"}",
            "{\"a\":\"\\u0061\"}", "{\"a\":\"\\ud83d\\udcf7\"}", "{\"a\":\"📷\"}",
            "{\"a\":1,\"b\":{\"a\":2}}", "{\"a\":[{\"id\":1},{\"id\":2}]}",
        ).forEach { expect(it) }
        listOf("0", "-0", "1", "-12", "1234567890", "0.5", "-0.5", "1e3", "1E+3", "1e-3", "0.5e+2")
            .forEach { expect("{\"n\":$it}") }
        val malformed = KnowledgeBundleJsonProblem.MALFORMED
        listOf(
            "", " ", "[]", "null", "\"text\"", "{} {}", "{}junk", "{}\u0000", "\uFEFF{}",
            "{/*comment*/\"a\":1}", "{\"a\":1}//comment", "{#comment\n\"a\":1}",
            "{'a':1}", "{a:1}", "{\"a\"=1}", "{\"a\"=>1}", "{\"a\":1;\"b\":2}",
            "{\"a\":1,}", "{\"a\":[1,]}", "{\"a\":[,1]}", "{\"a\":[1,,2]}",
            "{\"a\":True}", "{\"a\":undefined}", "{\"a\":NaN}", "{\"a\":Infinity}",
            "{\"a\":\"\\x41\"}", "{\"a\":\"\\uZZZZ\"}", "{\"a\":\"\\u123\"}",
            "{\"a\":\"line\nfeed\"}", "{\"a\":\"tab\there\"}", "{\"a\":\"unfinished}",
            "{\"a\":}", "{\"a\" 1}", "{\"a\":[}", "{\"a\":1", "{\u00a0\"a\":1}",
        ).forEach { expect(it, malformed) }
        listOf("+1", "01", "-01", ".1", "1.", "1e", "1e+", "--1", "0x10", "1 2", "1e+-2")
            .forEach { expect("{\"n\":$it}", malformed) }
        listOf(
            "{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}",
            "{\"nested\":{\"a\":1,\"a\":2}}", "{\"a\":[{\"x\":1,\"x\":2}]}",
        ).forEach { expect(it, KnowledgeBundleJsonProblem.DUPLICATE_KEY) }
        listOf(
            "{\"a\":\"\\ud800\"}", "{\"a\":\"\\udc00\"}", "{\"a\":\"\\ud800x\"}",
            "{\"a\":\"\\ud800\\ud800\"}", "{\"\\ud800\":1}",
            "{\"a\":\"${0xd800.toChar()}\"}",
        ).forEach { expect(it, KnowledgeBundleJsonProblem.INVALID_UNICODE) }
        expect("{\"a\":" + "[".repeat(31) + "0" + "]".repeat(31) + "}")
        expect("{\"a\":" + "[".repeat(32) + "0" + "]".repeat(32) + "}", KnowledgeBundleJsonProblem.TOO_DEEP)
        expect("{\"a\":" + "[".repeat(10000) + "0" + "]".repeat(10000) + "}", KnowledgeBundleJsonProblem.TOO_DEEP)
        return count
    }

    fun session(): Int {
        var count = 0
        fun expect(value: Boolean) { check(value) { "Session case ${count + 1}" }; count++ }
        val session = KnowledgeBundleImportSession()
        val first = checkNotNull(session.beginRead())
        expect(session.beginApply() == null)
        val second = checkNotNull(session.beginRead())
        expect(!session.finishRead(first))
        expect(session.finishRead(second))
        expect(!session.finishRead(second))
        val third = checkNotNull(session.beginRead())
        expect(session.reset())
        expect(!session.finishRead(third))
        val apply = checkNotNull(session.beginApply())
        expect(session.beginApply() == null)
        expect(session.beginRead() == null)
        expect(!session.reset())
        expect(!session.finishApply(apply - 1))
        expect(session.finishApply(apply))
        expect(!session.finishApply(apply))
        expect(session.reset())
        val finalRead = checkNotNull(session.beginRead())
        expect(!session.finishApply(apply))
        expect(session.finishRead(finalRead))
        return count
    }

    fun mapping(): Int {
        var count = 0
        fun expect(value: Boolean) { check(value) { "Mapping case ${count + 1}" }; count++ }
        val ids = listOf("p1", "p2")
        expect(!isCompleteKnowledgeBundleMapping(emptyList(), emptyMap()))
        expect(!isCompleteKnowledgeBundleMapping(ids, emptyMap()))
        expect(!isCompleteKnowledgeBundleMapping(ids, mapOf("p1" to "l1")))
        expect(!isCompleteKnowledgeBundleMapping(ids, mapOf("p1" to "l1", "p2" to "l1")))
        expect(!isCompleteKnowledgeBundleMapping(ids, mapOf("p1" to "l1", "p2" to " ")))
        expect(!isCompleteKnowledgeBundleMapping(ids, mapOf("p1" to "l1", "unknown" to "l2")))
        expect(!isCompleteKnowledgeBundleMapping(listOf("p1", "p1"), mapOf("p1" to "l1")))
        var bindings = toggleKnowledgeBundleBinding(ids, emptyMap(), "p1", "l1")
        expect(bindings == mapOf("p1" to "l1"))
        expect(toggleKnowledgeBundleBinding(ids, bindings, "p2", "l1") == bindings)
        expect(toggleKnowledgeBundleBinding(ids, bindings, "unknown", "l2") == bindings)
        expect(toggleKnowledgeBundleBinding(ids, bindings, "p2", " ") == bindings)
        bindings = toggleKnowledgeBundleBinding(ids, bindings, "p2", "l2")
        expect(isCompleteKnowledgeBundleMapping(ids, bindings))
        expect(!isCompleteKnowledgeBundleMapping(ids, bindings + ("extra" to "l3")))
        bindings = toggleKnowledgeBundleBinding(ids, bindings, "p1", "l1")
        expect(bindings == mapOf("p2" to "l2"))
        expect(!isCompleteKnowledgeBundleMapping(ids, bindings))
        bindings = toggleKnowledgeBundleBinding(ids, bindings, "p2", "l3")
        expect(bindings == mapOf("p2" to "l3"))
        return count
    }

    fun boundedRead(): Int {
        var count = 0
        fun expect(value: Boolean) { check(value) { "Read case ${count + 1}" }; count++ }
        expect(readBoundedKnowledgeBundleBytes(java.io.ByteArrayInputStream(byteArrayOf()), 4).isEmpty())
        expect(readBoundedKnowledgeBundleBytes(java.io.ByteArrayInputStream(byteArrayOf(1, 2)), 4).contentEquals(byteArrayOf(1, 2)))
        expect(readBoundedKnowledgeBundleBytes(java.io.ByteArrayInputStream(byteArrayOf(1, 2)), 2).size == 2)
        val limitError = runCatching {
            readBoundedKnowledgeBundleBytes(java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2)
        }.exceptionOrNull()
        expect(limitError is KnowledgeBundleDocumentTooLargeException)
        val source = java.io.ByteArrayInputStream(ByteArray(10000))
        expect(runCatching { readBoundedKnowledgeBundleBytes(source, 16) }.exceptionOrNull() is KnowledgeBundleDocumentTooLargeException)
        expect(source.available() == 10000 - 17)
        val zeroProvider = object : java.io.ByteArrayInputStream(byteArrayOf(3, 4)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        expect(readBoundedKnowledgeBundleBytes(zeroProvider, 2).contentEquals(byteArrayOf(3, 4)))
        val marker = IllegalStateException("synthetic cancellation")
        val cancelled = java.io.ByteArrayInputStream(byteArrayOf(1, 2))
        expect(runCatching { readBoundedKnowledgeBundleBytes(cancelled, 2) { throw marker } }.exceptionOrNull() === marker)
        expect(cancelled.available() == 2)
        expect(runCatching { readBoundedKnowledgeBundleBytes(cancelled, 0) }.exceptionOrNull() is IllegalArgumentException)
        return count
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val syntax = syntax()
        val session = session()
        val mapping = mapping()
        val boundedRead = boundedRead()
        println("PASS syntax=$syntax session=$session mapping=$mapping boundedRead=$boundedRead total=${syntax + session + mapping + boundedRead}")
    }
}
