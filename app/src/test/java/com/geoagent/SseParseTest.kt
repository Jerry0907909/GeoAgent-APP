package com.geoagent

import com.geoagent.data.api.dto.ChatEvent
import com.geoagent.data.api.dto.SearchEvent
import com.geoagent.data.api.dto.parseChatEvent
import com.geoagent.data.api.dto.parseSearchEvent
import org.junit.Assert.*
import org.junit.Test

class SseParseTest {

    @Test
    fun `parse chat content event`() {
        val event = parseChatEvent("""{"type":"content","content":"地质构造"}""")
        assertTrue(event is ChatEvent.Content)
        assertEquals("地质构造", (event as ChatEvent.Content).content)
    }

    @Test
    fun `parse chat info event`() {
        val event = parseChatEvent("""{"type":"info","conversation_id":42}""")
        assertTrue(event is ChatEvent.Info)
        assertEquals(42, (event as ChatEvent.Info).conversation_id)
    }

    @Test
    fun `parse chat info event with null id`() {
        val event = parseChatEvent("""{"type":"info","conversation_id":null}""")
        assertTrue(event is ChatEvent.Info)
        assertNull((event as ChatEvent.Info).conversation_id)
    }

    @Test
    fun `parse chat done event`() {
        val event = parseChatEvent("""{"type":"done","message":"回答完成"}""")
        assertTrue(event is ChatEvent.Done)
        assertEquals("回答完成", (event as ChatEvent.Done).message)
    }

    @Test
    fun `parse chat error event`() {
        val event = parseChatEvent("""{"type":"error","message":"服务不可用"}""")
        assertTrue(event is ChatEvent.Error)
        assertEquals("服务不可用", (event as ChatEvent.Error).message)
    }

    @Test
    fun `parse chat unknown type`() {
        val event = parseChatEvent("""{"type":"unknown"}""")
        assertTrue(event is ChatEvent.Error)
    }

    @Test
    fun `parse chat malformed json`() {
        val event = parseChatEvent("not even json")
        assertTrue(event is ChatEvent.Error)
        assertTrue((event as ChatEvent.Error).message.contains("Failed to parse"))
    }

    @Test
    fun `parse chat sources event`() {
        val json = """{"type":"sources","sources":[{"content":"test content","source":"test source","type":"document","relevance_score":0.95}]}"""
        val event = parseChatEvent(json)
        assertTrue(event is ChatEvent.Sources)
        assertEquals(1, (event as ChatEvent.Sources).sources.size)
        assertEquals("test source", event.sources[0].source)
    }

    @Test
    fun `parse knowledge base source document id`() {
        val json = """{"type":"sources","sources":[{"content":"doc chunk","source":"report.docx","type":"knowledge_base","document_id":"doc-1","document_name":"report.docx"}]}"""
        val event = parseChatEvent(json)

        assertTrue(event is ChatEvent.Sources)
        val source = (event as ChatEvent.Sources).sources.single()
        assertEquals("knowledge_base", source.type)
        assertEquals("doc-1", source.document_id)
        assertEquals("report.docx", source.document_name)
    }

    @Test
    fun `parse search plan event`() {
        val event = parseSearchEvent("""{"type":"plan","queries":["q1","q2"]}""")
        assertTrue(event is SearchEvent.Plan)
        assertEquals(2, (event as SearchEvent.Plan).queries.size)
    }

    @Test
    fun `parse search answer event`() {
        val event = parseSearchEvent("""{"type":"answer","content":"answer text"}""")
        assertTrue(event is SearchEvent.Answer)
        assertEquals("answer text", (event as SearchEvent.Answer).content)
    }

    @Test
    fun `parse search done event`() {
        val event = parseSearchEvent("""{"type":"done"}""")
        assertTrue(event is SearchEvent.Done)
    }
}
