package com.a207.runkk.app.ui.runimal

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * 러니멀 유형 값(서버 RunimalType enum 이름, 예: MORNING_SPARROW)을 동물 그림과 한국어 이름으로 바꾸는 대응표.
 * 서버가 GET /runimal의 type·allTypes·card.type에 enum 이름을 보낸다. 모르는 값이면 [UNKNOWN](물음표 그림)이다.
 */
enum class RunimalArt(@DrawableRes val drawableRes: Int, @StringRes val nameRes: Int) {
    MORNING_SPARROW(R.drawable.runimal_morning_sparrow, R.string.runimal_name_morning_sparrow),
    WHEEL_HAMSTER(R.drawable.runimal_wheel_hamster, R.string.runimal_name_wheel_hamster),
    ALLEY_SQUIRREL(R.drawable.runimal_alley_squirrel, R.string.runimal_name_alley_squirrel),
    STROLLING_CAT(R.drawable.runimal_strolling_cat, R.string.runimal_name_strolling_cat),
    COMMUTER_TURTLE(R.drawable.runimal_commuter_turtle, R.string.runimal_name_commuter_turtle),
    STEADY_CAMEL(R.drawable.runimal_steady_camel, R.string.runimal_name_steady_camel),
    SKY_EAGLE(R.drawable.runimal_sky_eagle, R.string.runimal_name_sky_eagle),
    LONE_WOLF(R.drawable.runimal_lone_wolf, R.string.runimal_name_lone_wolf),
    WALKING_PUPPY(R.drawable.runimal_walking_puppy, R.string.runimal_name_walking_puppy),
    PLAYGROUND_MONKEY(R.drawable.runimal_playground_monkey, R.string.runimal_name_playground_monkey),
    EXPLORER_OTTER(R.drawable.runimal_explorer_otter, R.string.runimal_name_explorer_otter),
    PICNIC_RABBIT(R.drawable.runimal_picnic_rabbit, R.string.runimal_name_picnic_rabbit),
    PILLAR_ELEPHANT(R.drawable.runimal_pillar_elephant, R.string.runimal_name_pillar_elephant),
    PENGUIN_SQUAD(R.drawable.runimal_penguin_squad, R.string.runimal_name_penguin_squad),
    LEADER_BIRD(R.drawable.runimal_leader_bird, R.string.runimal_name_leader_bird),
    WILD_HORSE(R.drawable.runimal_wild_horse, R.string.runimal_name_wild_horse),

    /** 모르는 유형 값(서버에 새 유형이 생긴 경우 등). */
    UNKNOWN(R.drawable.runimal_unknown, R.string.runimal_name_unknown);

    companion object {
        /** 유형 값의 그림. null·빈 값·모르는 값은 [UNKNOWN]. */
        fun of(type: String?): RunimalArt = entries.firstOrNull { it != UNKNOWN && it.name == type } ?: UNKNOWN
    }
}
