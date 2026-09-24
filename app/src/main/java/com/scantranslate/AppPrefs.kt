package com.scantranslate

import android.content.Context
import android.graphics.Color

object AppPrefs {
    private const val FILE = "scan_translate"
    private const val SOURCE = "source"
    private const val TARGET = "target"
    private const val ENGINE = "engine"
    private const val KEY = "deepl_key"
    private const val FONT = "font_size"
    private const val COLOR = "font_color"
    private const val INTERVAL = "ocr_interval"
    private const val DURATION = "display_duration"
    private const val BOX_X = "box_x"
    private const val BOX_Y = "box_y"
    private const val BOX_W = "box_w"
    private const val BOX_H = "box_h"

    fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun source(context: Context) = prefs(context).getString(SOURCE, "en") ?: "en"
    fun target(context: Context) = prefs(context).getString(TARGET, "zh") ?: "zh"
    fun engine(context: Context) = prefs(context).getString(ENGINE, "system") ?: "system"
    fun deeplKey(context: Context) = prefs(context).getString(KEY, "") ?: ""
    fun fontSize(context: Context) = prefs(context).getInt(FONT, 20)
    fun fontColor(context: Context) = prefs(context).getInt(COLOR, Color.WHITE)
    fun interval(context: Context) = prefs(context).getLong(INTERVAL, 800L).coerceIn(10L, 1000L)
    fun duration(context: Context) = prefs(context).getLong(DURATION, 3500L)
    fun boxX(context: Context) = prefs(context).getInt(BOX_X, 80)
    fun boxY(context: Context) = prefs(context).getInt(BOX_Y, 360)
    fun boxW(context: Context) = prefs(context).getInt(BOX_W, 720)
    fun boxH(context: Context) = prefs(context).getInt(BOX_H, 180)

    fun saveBox(context: Context, x: Int, y: Int, w: Int, h: Int) = prefs(context).edit()
        .putInt(BOX_X, x).putInt(BOX_Y, y).putInt(BOX_W, w).putInt(BOX_H, h).apply()
}
