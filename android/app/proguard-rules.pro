# 출시 빌드(R8 축소·난독화, 리뷰 N-2) 보존 규칙. 라이브러리가 싣는 규칙(consumer rules)으로 충분한 것은 적지 않고,
# 반사·이름 조회로 깨지는 곳만 적는다. 새 반사 사용을 더하면 여기에 규칙을 더하고 assembleProdRelease로 확인한다.

# 비정상 종료 스택을 읽도록 줄 번호를 남긴다(원래 파일 이름은 숨긴다).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization: Retrofit 변환기·실시간 해독기가 serializer()를 형식으로 찾는다(라이브러리 규칙 보강).
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.a207.runkk.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,includedescriptorclasses class com.a207.runkk.**$$serializer { *; }

# --- Retrofit: 서비스 인터페이스의 suspend 함수 제네릭 반환형을 반사로 읽는다(R8 전체 모드).
-keepattributes Signature,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation interface com.a207.runkk.app.api.** { @retrofit2.http.* <methods>; }

# --- Hilt: 생성 구성 요소·진입점은 라이브러리 규칙이 지킨다. ViewModel은 이름으로 찾으므로 이름을 남긴다.
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# --- Room: 생성 구현(…_Impl)을 이름으로 찾는다(라이브러리 규칙 보강).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Kakao SDK v2(로그인, 공식 안내): 응답 모델을 Gson 반사로 읽는다.
-keep class com.kakao.sdk.**.model.* { <fields>; }
-keep class * extends com.google.gson.TypeAdapter
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.**

# --- Kakao 지도 SDK v2(기록 상세 코스, 공식 안내): 네이티브 코드가 클래스를 이름으로 찾는다.
-keep class com.kakao.vectormap.** { *; }
-keep interface com.kakao.vectormap.**
