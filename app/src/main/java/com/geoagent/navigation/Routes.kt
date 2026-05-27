package com.geoagent.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val MAIN = "main"
    const val CHAT_LIST = "chat/list"
    const val CHAT_DETAIL = "chat/detail/{conversationId}"
    const val DOCUMENTS_LIST = "documents/list"
    const val DOCUMENTS_UPLOAD = "documents/upload"
    const val DOCUMENT_DETAIL = "documents/detail?source={source}&collection={collection}"

    fun documentDetail(source: String, collection: String): String {
        val encodedSource = java.net.URLEncoder.encode(source, Charsets.UTF_8.name())
        val encodedCollection = java.net.URLEncoder.encode(collection, Charsets.UTF_8.name())
        return "documents/detail?source=$encodedSource&collection=$encodedCollection"
    }
    const val SETTINGS = "settings"
    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_APPEARANCE = "settings/appearance"

    fun chatDetail(conversationId: Int) = "chat/detail/$conversationId"
}