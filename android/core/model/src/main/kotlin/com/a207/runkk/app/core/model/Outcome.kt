package com.a207.runkk.app.core.model

/** 포트·UseCase의 결과(5.1.2, 13.2): 성공 값 또는 AppError. */
sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>

    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value
