package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.BackgroundChoice
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.Shop
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 아바타·상점 포트를 REST로 구현한다(6.1.14 api.avatar). 셀카 바이트는 요청 본문으로만 보내고 기기·로그에 남기지 않는다(D-23, D-59).
 * 앱이 모르는 착용 부위는 건너뛴다.
 */
internal class AvatarRepositoryImpl
@Inject
constructor(
    private val api: AvatarApi,
    private val faces: FaceImageSource
) : AvatarRepository {
    override suspend fun avatar(): Outcome<Avatar> = ApiErrorMapper.call { api.avatar().toDomain() }

    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = faces.faceLayers(faceKey)

    /** 변환 실패는 200 + created=false라 Rejected(failureReason)로 바꿔 화면이 사유를 보이게 한다. */
    override suspend fun createFromSelfie(jpeg: ByteArray): Outcome<Avatar> {
        val reply =
            when (
                val result =
                    ApiErrorMapper.call {
                        val part = MultipartBody.Part.createFormData(SELFIE_PART, SELFIE_FILE, jpeg.toRequestBody(JPEG))
                        api.createFromSelfie(part)
                    }
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (reply.created == false) {
            return Outcome.Failure(AppError.Rejected(reply.failureReason ?: SELFIE_FAILED))
        }
        return Outcome.Success(reply.toDomain())
    }

    override suspend fun chooseDefault(characterId: String): Outcome<Avatar> =
        ApiErrorMapper.call { api.chooseDefault(DefaultCharacterRequest(characterId)).toDomain() }

    override suspend fun equip(changes: Map<ItemSlot, Long?>): Outcome<Avatar> =
        ApiErrorMapper.call { api.equip(EquipRequest(changes.mapKeys { it.key.name })).toDomain() }

    override suspend fun shop(slot: ItemSlot?): Outcome<Shop> = ApiErrorMapper.call { api.shop(slot?.name).toDomain() }

    override suspend fun purchase(itemId: Long): Outcome<Int> = ApiErrorMapper.call {
        api.purchase(PurchaseRequest(itemId)).balance
    }

    override suspend fun backgrounds(): Outcome<AvatarBackgrounds> = ApiErrorMapper.call {
        api.backgrounds().toDomain()
    }

    /** 해금하지 않은 배경은 Rejected(BACKGROUND_LOCKED)다. */
    override suspend fun chooseBackground(key: String?): Outcome<Unit> = ApiErrorMapper.call {
        api.chooseBackground(BackgroundRequest(key))
        Unit
    }

    /** 러닝 카드 배경(GET /avatar/backgrounds의 고른 키와 그 항목의 단계, D-185). 목록에 없는 키면 단계는 빈 문자열이다. */
    override suspend fun backgroundChoice(): Outcome<BackgroundChoice?> = ApiErrorMapper.call {
        val backgrounds = api.backgrounds().toDomain()
        backgrounds.selectedKey?.let { key ->
            BackgroundChoice(key, backgrounds.items.firstOrNull { it.key == key }?.stage.orEmpty())
        }
    }

    private companion object {
        const val SELFIE_PART = "selfie"
        const val SELFIE_FILE = "selfie.jpg"
        const val SELFIE_FAILED = "SELFIE_FAILED"
        val JPEG = "image/jpeg".toMediaType()
    }
}

internal fun AvatarResponse.toDomain(): Avatar = Avatar(
    faceKey = faceKey,
    defaultCharacterId = defaultCharacterId,
    bodyShape = bodyShape,
    skinTone = skinTone,
    equipped = equipped.bySlot(),
    balance = balance,
    canCreateSelfieToday = canCreateSelfieToday,
    rejected = rejected.bySlot(),
    backgroundKey = backgroundKey
)

internal fun BackgroundsResponse.toDomain(): AvatarBackgrounds = AvatarBackgrounds(
    selectedKey = selectedKey,
    items =
        backgrounds.map {
            AvatarBackgrounds.Item(
                key = it.key,
                name = it.name,
                stage = it.stage,
                distance = Distance(it.distanceMeters),
                isUnlocked = it.unlocked,
                unlockedAt = it.unlockedAt?.let(ServerTime::parse)
            )
        }
)

internal fun ShopResponse.toDomain(): Shop = Shop(
    items =
        items.mapNotNull { item ->
            enumByName<ItemSlot>(item.slot)?.let {
                Shop.Item(item.itemId, it, item.name, item.price, item.owned, item.onSale)
            }
        },
    balance = balance
)

private fun <V> Map<String, V>.bySlot(): Map<ItemSlot, V> =
    entries.mapNotNull { (slot, value) -> enumByName<ItemSlot>(slot)?.let { it to value } }.toMap()
