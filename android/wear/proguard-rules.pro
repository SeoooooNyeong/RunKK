# 워치 출시 빌드(R8 축소·난독화, W51) 보존 규칙. 폰 app/proguard-rules.pro에서 워치가 쓰는 라이브러리 몫만 옮겼다.
# 라이브러리가 싣는 규칙(consumer rules: OkHttp·Room·WorkManager·DataStore·Tink·Hilt·Health Services·Play 서비스)으로 충분한 것은
# 적지 않고, 반사·이름 조회로 깨지는 곳만 적는다. 매니페스트 구성 요소(PhoneMessageService 등)는 AGP가 지킨다.
# 새 반사 사용을 더하면 여기에 규칙을 더하고 :wear:bundleProdRelease(또는 minifyProdReleaseWithR8)로 확인한다.

# 비정상 종료 스택을 읽도록 줄 번호를 남긴다(원래 파일 이름은 숨긴다).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization: wear-contract 메시지, :data REST DTO, watch.cache 파일을 serializer()로 찾는다(라이브러리 규칙 보강).
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.a207.runkk.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,includedescriptorclasses class com.a207.runkk.**$$serializer { *; }

# --- Retrofit(:data REST): 서비스 인터페이스의 suspend 함수 제네릭 반환형을 반사로 읽는다(R8 전체 모드).
-keepattributes Signature,Exceptions,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation interface com.a207.runkk.app.api.** { @retrofit2.http.* <methods>; }

# --- Hilt: 생성 구성 요소·진입점은 라이브러리 규칙이 지킨다. ViewModel은 이름으로 찾으므로 이름을 남긴다.
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# --- Room(:data 보관함): 생성 구현(…_Impl)을 이름으로 찾는다(라이브러리 규칙 보강).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
