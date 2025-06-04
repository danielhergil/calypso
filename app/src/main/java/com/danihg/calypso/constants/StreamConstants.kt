package com.danihg.calypso.constants

// ACCIONES que usará el Service para arrancar/detener grabación y streaming
const val ACTION_START_RECORD  = "com.danihg.calypso.START_RECORD"
const val ACTION_STOP_RECORD   = "com.danihg.calypso.STOP_RECORD"
const val ACTION_START_STREAM  = "com.danihg.calypso.START_STREAM"
const val ACTION_STOP_STREAM   = "com.danihg.calypso.STOP_STREAM"

// KEYS para los extras (ruta de fichero temporal, URL RTMP, etc.)
const val EXTRA_PATH           = "extra_path"
const val EXTRA_URL            = "extra_url"