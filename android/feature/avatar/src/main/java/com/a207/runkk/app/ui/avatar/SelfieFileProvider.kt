package com.a207.runkk.app.ui.avatar

import androidx.core.content.FileProvider

/**
 * 셀카 찍기 임시 파일 주소(실기기 DV3-3). 공유 카드의 FileProvider와 매니페스트에서 이름이 겹치지 않게 따로 둔다.
 */
class SelfieFileProvider : FileProvider(R.xml.avatar_selfie_paths) {
    internal companion object {
        /** 매니페스트 authorities와 같다. */
        fun authority(packageName: String): String = "$packageName.runkk.selfie"
    }
}
