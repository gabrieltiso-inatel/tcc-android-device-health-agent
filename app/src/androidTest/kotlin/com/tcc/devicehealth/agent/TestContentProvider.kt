package com.tcc.devicehealth.agent

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import androidx.annotation.RequiresApi

@RequiresApi(29)
internal fun testContentResolver(context: Context, authority: String, query: (Uri, Array<out String>?) -> Cursor?): ContentResolver {
    val provider = object : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unexpected insert")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected delete")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Unexpected update")
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = query(uri, projection)
    }
    provider.attachInfo(context, ProviderInfo().apply { this.authority = authority })
    return ContentResolver.wrap(provider)
}
