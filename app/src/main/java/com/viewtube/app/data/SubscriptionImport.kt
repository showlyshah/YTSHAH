package com.viewtube.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads a subscription list exported from:
 *  - Google Takeout  (subscriptions.csv, or the JSON variant)
 *  - NewPipe         (its subscription export .json)
 *
 * To get the Google file: takeout.google.com -> deselect all -> tick "YouTube and YouTube Music"
 * -> "All YouTube data included" -> keep only "subscriptions" -> export.
 */
object SubscriptionImport {

    fun parse(raw: String): List<ChannelItem> {
        val text = raw.trimStart('﻿').trim()
        val channels = when {
            text.startsWith("{") -> fromNewPipe(JSONObject(text))
            text.startsWith("[") -> fromTakeoutJson(JSONArray(text))
            else -> fromCsv(text)
        }
        return channels.filter { it.id.startsWith("UC") }.distinctBy { it.id }
    }

    private fun fromNewPipe(root: JSONObject): List<ChannelItem> {
        val arr = root.optJSONArray("subscriptions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            if (o.optInt("service_id", 0) != 0) return@mapNotNull null
            val id = o.optString("url").substringAfterLast('/')
            ChannelItem(id, channelUrl(id), o.optString("name"), null)
        }
    }

    private fun fromTakeoutJson(arr: JSONArray): List<ChannelItem> = (0 until arr.length()).mapNotNull {
        val snippet = arr.getJSONObject(it).optJSONObject("snippet") ?: return@mapNotNull null
        val id = snippet.optJSONObject("resourceId")?.optString("channelId").orEmpty()
        ChannelItem(id, channelUrl(id), snippet.optString("title"), null)
    }

    private fun fromCsv(text: String): List<ChannelItem> = text.lineSequence().mapNotNull { line ->
        val cells = splitCsv(line)
        if (cells.size < 3) return@mapNotNull null
        val id = cells[0].trim()
        ChannelItem(id, channelUrl(id), cells[2].trim(), null)
    }.toList()

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    out += cell.toString()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        out += cell.toString()
        return out
    }
}
