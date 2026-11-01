package com.maknoon.mizan.core.data.db

import androidx.room.TypeConverter

/**
 * Stores the blocked-package list as newline-separated text. Android package names cannot
 * contain a newline, so the encoding needs no escaping and stays readable in the database.
 */
class NetworkProfileConverters {

    @TypeConverter
    fun packagesToString(packages: List<String>): String = packages.joinToString(separator = "\n")

    @TypeConverter
    fun stringToPackages(value: String): List<String> =
        value.split('\n').filter { it.isNotEmpty() }
}
