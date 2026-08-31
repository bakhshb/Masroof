package com.baraa.masroof.application.update

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object ReleaseAssets {
    fun tagForVersionName(versionName: String): String = "v$versionName"

    fun findApkAssetUrl(release: JsonObject, apkFileName: String): String? {
        val assets = release["assets"]?.jsonArray ?: JsonArray(emptyList())
        return assets.firstOrNull { asset ->
            asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull == apkFileName
        }?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
    }
}
