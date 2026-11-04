package com.amply.mobile.util

fun nowSec(): Long = System.currentTimeMillis() / 1000L

/** "1 song", "2 songs": count labels read badly with a fixed plural. */
fun plural(count: Int, singular: String, pluralForm: String = singular + "s"): String =
    "$count ${if (count == 1) singular else pluralForm}"
