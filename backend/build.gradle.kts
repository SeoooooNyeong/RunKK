plugins {
	java
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.10.4"
	jacoco
}

group = "com.a207"
version = "0.0.1-SNAPSHOT"
description = "RunKK backend"

// 설계서 D-01: 서버 Java 21 + Spring Boot 3
java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// 모니터링(S15P31A207-78): 관리 포트 /actuator/prometheus를 Prometheus가 읽는다. 버전은 Spring Boot BOM이 정한다.
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	// 실시간 대결 메시지(설계 5.7.2, /ws 하나, STOMP 없이 type 필드로 나눈다)
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	// 회원 JWT(D-19)와 FCM 서비스 계정 서명. 버전은 Spring Boot BOM이 정한다.
	implementation("com.nimbusds:nimbus-jose-jwt")
	implementation("org.flywaydb:flyway-core")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("com.h2database:h2")
	implementation("com.github.codemonstur:embedded-redis:1.4.4")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	// 설계서 14장: 패키지 규칙(ArchUnit 10개), infra 통합 시험(Testcontainers PostgreSQL·Redis, Docker 없으면 건너뜀)
	testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 서버 시계는 Asia/Seoul(RunkkApplication.clock)이다. 시험이 만드는 LocalDateTime.now()도 같은 시간대여야
	// CI 컨테이너(UTC)와 개발 PC(KST)에서 결과가 같다(UTC면 크루런 예약 시각이 과거가 되어 시험 4개가 깨진다)
	systemProperty("user.timezone", "Asia/Seoul")
	finalizedBy(tasks.jacocoTestReport)
}

jacoco {
	toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		csv.required = true
		html.required = true
	}
}

// 코드 컨벤션 4장: 포맷은 Spotless(google-java-format aosp)가 정한다. 커밋 전 ./gradlew spotlessApply, CI는 spotlessCheck(check에 포함).
spotless {
	java {
		target("src/**/*.java")
		googleJavaFormat().aosp().reflowLongStrings()
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
	kotlinGradle {
		target("*.gradle.kts")
		trimTrailingWhitespace()
		endWithNewline()
	}
}
