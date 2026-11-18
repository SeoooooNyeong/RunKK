package com.a207.runkk.app.core.avatar

import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.FileAssetLoader

/** 가져온 Rive 파일과 그 파일의 내장 그림 자리를 잡은 로더(플레이어 하나가 함께 쓴다). */
internal class ImportedRiv(val file: File, val loader: FileAssetLoader)
