package com.a207.runkk.app.api.avatar

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.FaceImageSource
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.BackgroundChoice
import com.a207.runkk.app.domain.avatar.ItemSlot
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.Shop
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 아바타·상점 포트를 REST로 구현한다(6.1.14 api.avatar). 셀카 바이트는 요청 본문으로만 보내고 기기·로그에 남기지 않는다(D-23, D-59).
 * 셀카 얼굴은 서버가 비동기로 만든다(S15P31A207-276): 접수한 작업 ID만 [pending]에 남겨 이어 조회한다.
 * 앱이 모르는 착용 부위는 건너뛴다.
 */
internal class AvatarRepositoryImpl
@Inject
constructor(
    private val api: AvatarApi,
    private val faces: FaceImageSource,
    private val pending: PendingSelfieStore
) : AvatarRepository {
    override suspend fun avatar(): Outcome<Avatar> = ApiErrorMapper.call { api.avatar().toDomain() }

    override suspend fun faceLayers(faceKey: String?): Outcome<List<FaceLayer>> = faces.faceLayers(faceKey)

    override suspend fun headJson(faceKey: String?): Outcome<String?> = faces.headJson(faceKey)

    override suspend fun runner(): Outcome<AvatarRunner> = ApiErrorMapper.call { api.runner().toDomain() }

    /** 접수하면 작업 ID를 남긴다. 접수 응답이 이미 실패면 남기지 않는다. */
    override suspend fun submitSelfie(jpeg: ByteArray): Outcome<SelfieJob> {
        val result = ApiErrorMapper.call {
            val part = MultipartBody.Part.createFormData(SELFIE_PART, SELFIE_FILE, jpeg.toRequestBody(JPEG))
            api.submitSelfie(part).toDomain()
        }
        if (result is Outcome.Success) {
            if (result.value.status == SelfieJob.Status.FAILED) pending.clear() else pending.save(result.value.jobId)
        }
        return result
    }

    override suspend fun selfieJob(jobId: String): Outcome<SelfieJob> {
        val result = ApiErrorMapper.call { api.selfieJob(jobId).toDomain() }
        val isOver = when (result) {
            is Outcome.Success -> result.value.status == SelfieJob.Status.FAILED
            is Outcome.Failure -> result.error is AppError.Rejected
        }
        if (isOver) forget(jobId)
        return result
    }

    /** 결과 본문은 읽지 않고 닫는다(서버가 응답 전에 얼굴을 바꾼다). 거부(작업 없음·준비 안 됨 등)면 남긴 작업도 지운다. */
    override suspend fun finishSelfie(jobId: String): Outcome<Avatar> {
        val done = ApiErrorMapper.call { api.selfieResult(jobId).close() }
        if (done is Outcome.Failure) {
            if (done.error is AppError.Rejected) forget(jobId)
            return done
        }
        forget(jobId)
        return avatar()
    }

    /**
     * 기기에 남긴 작업이 없으면(앱 데이터를 지웠거나 다른 기기에서 접수) 서버의 진행 중 작업(GET /avatar/selfie/jobs/current)을 읽어
     * 남긴다. 서버가 그 작업을 끝내기 전까지 새 셀카가 SELFIE_IN_PROGRESS로 막히므로 이어 조회해 끝내야 한다.
     */
    override suspend fun pendingSelfieJobId(): String? {
        pending.load()?.let { return it }
        val reply = ApiErrorMapper.call { api.currentSelfieJob() }.getOrNull() ?: return null
        val job = reply.body()?.takeIf { reply.isSuccessful }?.toDomain() ?: return null
        if (job.status == SelfieJob.Status.FAILED) return null
        pending.save(job.jobId)
        return job.jobId
    }

    /** 남긴 작업이 [jobId]일 때만 지운다(그사이 새로 접수한 작업은 그대로). */
    private fun forget(jobId: String) {
        if (pending.load() == jobId) pending.clear()
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

/** 모르는 상태는 아직 진행 중으로 본다(앱이 조회를 이어 가고 서버가 끝을 알린다). */
internal fun SelfieJobResponse.toDomain(): SelfieJob = SelfieJob(
    jobId = jobId,
    status = enumByName<SelfieJob.Status>(status) ?: SelfieJob.Status.RUNNING,
    failureCode = error?.code
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
                unlockedAt = it.unlockedAt?.let(ServerTime::parse),
                remaining = Distance(it.remainingMeters)
            )
        },
    totalDistance = Distance(totalMeters)
)

internal fun RunnerResponse.toDomain(): AvatarRunner = AvatarRunner(
    bodyShape = bodyShape,
    bodyVariant = bodyVariant,
    skinTone = skinTone,
    typicalStartHour = typicalStartHour,
    recentRunCount = recentRunCount,
    recentDistance = Distance(recentDistanceMeters),
    runAmount = runAmount,
    restDaysBeforeDecline = restDaysBeforeDecline
)

internal fun ShopResponse.toDomain(): Shop = Shop(
    items =
        items.mapNotNull { item ->
            enumByName<ItemSlot>(item.slot)?.let {
                Shop.Item(
                    item.itemId,
                    it,
                    item.name,
                    item.price,
                    item.owned,
                    item.onSale,
                    imageKey = item.assetKey,
                    isLocked = item.locked,
                    unlockLabel = item.unlockCondition
                )
            }
        },
    balance = balance
)

private fun <V> Map<String, V>.bySlot(): Map<ItemSlot, V> =
    entries.mapNotNull { (slot, value) -> enumByName<ItemSlot>(slot)?.let { it to value } }.toMap()
