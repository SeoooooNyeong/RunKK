# 설계 → Java 구현 지침

`sw-design-oo` 8단계에서 읽는다. 설계서의 클래스를 코드로 옮길 때의 변환 규칙과 예다. 클래스는 **객체지향 언어**로 구현한다. 절차적 언어로 기술하지 않는다.

## 1. 변환 규칙

| 설계 요소 | Java |
|-----------|------|
| 클래스 | `class 이름 { }` |
| 추상 클래스 | `abstract class 이름 { abstract 반환형 메소드(); }` |
| 인터페이스 | `interface 이름 { 반환형 메소드(); }` |
| 속성 | `private 형 이름;` |
| 연산 | `public 반환형 이름(매개변수) { }` |
| 생성자 | `이름(필수 속성들) { this.x = x; }` |
| 상속 | `class 자식 extends 부모` |
| 인터페이스 구현 | `class 이름 implements 인터페이스` |
| 다형성 | 상위 타입 변수 + 하위 객체, 메소드 오버라이딩 |
| 연관 (1:N) | `private List<상대> 상대들;` |
| 연관 (N:1) | `private 상대 상대;` |
| 합성 | 전체 클래스 안에서 `new`로 생성, 외부에 참조를 내주지 않음 |
| 열거 상태 | `enum 상태 { … }` |

## 2. 클래스 기본 형태

```java
class Student {
    // 속성: 모두 private
    private String studentId;
    private String name;
    private String deptCode;
    private int year;
    private StudentStatus status;

    // 생성자: 필수 속성을 한 번에 초기화
    Student(String studentId, String name, String deptCode, int year) {
        this.studentId = studentId;   // this.필드 = 매개변수
        this.name = name;
        this.deptCode = deptCode;
        this.year = year;
        this.status = StudentStatus.ENROLLED;
    }

    // 연산: 속성을 읽거나 바꾼다
    public String getStudentId() { return studentId; }
    public String getName() { return name; }

    public void changeStatus(StudentStatus newStatus, String reason) {
        if (newStatus == null) throw new IllegalArgumentException("상태 필요");
        this.status = newStatus;
        // 이력 기록은 StudentHistory로
    }
}
```

### 생성자 (Constructor)

객체를 만들 때 자동으로 실행되어 필드 초기값을 한 번에 넣는다. 매번 `s.name = "..."`로 넣는 것보다 안전하다. 이름은 클래스와 같고 반환형이 없다.

```java
Student s = new Student("2026100001", "김철민", "CSE", 1);
```

### this

**현재 객체 자신.** 생성자·메소드의 매개변수 이름이 필드와 같을 때 `this.필드`로 구분한다. 국민_03이 실행 중이면 this는 국민_03, 국민_04가 실행 중이면 this는 국민_04다.

## 3. 캡슐화: private + 검증하는 setter

속성은 private으로 숨기고, 바꿔야 하는 것만 검증이 들어간 메소드로 연다.

```java
class Grade {
    private int score;
    private Letter letter;

    public void updateScore(int newScore, Professor by, String reason) {
        if (newScore < 0 || newScore > 100)
            throw new IllegalArgumentException("점수는 0~100");
        history.add(new GradeHistory(this.score, by, reason));
        this.score = newScore;
    }

    public int getScore() { return score; }
    // setScore(int)는 만들지 않는다. 검증 없는 setter는 캡슐화를 깬다
}
```

규칙:
- 모든 속성에 getter/setter를 기계적으로 만들지 않는다. 외부가 정말 읽어야 하는 것만 getter, 정말 바꿔야 하는 것만 검증 포함 메소드.
- setter 이름은 `setX`보다 업무 동사(`updateScore`, `confirm`, `reject`)가 낫다. 의도가 드러나고 검증 위치가 명확하다.
- 잘못된 값(음수 점수, 범위 밖 주민번호)은 메소드 안에서 거부한다.

접근 제한자:

| 제한자 | 범위 | 용도 |
|--------|------|------|
| `private` | 클래스 안 | 모든 속성, 내부 보조 메소드 |
| `protected` | 같은 패키지 + 하위 클래스 | 하위 클래스가 써야 하는 것 |
| `public` | 어디서나 | 인터페이스(계약) 메소드 |
| (없음) | 같은 패키지 | 패키지 내부 협력 |

## 4. 상속: extends

```java
abstract class Person {
    private String userId;
    private String name;

    Person(String userId, String name) {
        this.userId = userId;
        this.name = name;
    }
    public String getName() { return name; }
}

class Student extends Person {
    private String studentId;
    private int year;

    Student(String userId, String name, String studentId, int year) {
        super(userId, name);          // 부모 생성자 호출
        this.studentId = studentId;
        this.year = year;
    }
}

class Professor extends Person {
    private String professorId;
    Professor(String userId, String name, String professorId) {
        super(userId, name);
        this.professorId = professorId;
    }
}
```

- 자식은 부모의 모든 필드·메소드를 물려받고 자기 것을 더한다.
- **단일 상속.** `class A extends B, C`는 불가. 두 부모가 같은 메소드를 가지면 누구를 따를지 모호하기 때문. 여러 타입이 필요하면 인터페이스를 `implements`한다.
- 상속은 한 단계씩. 할아버지를 건너뛰지 않는다.
- is-a가 아닌데 코드만 재사용하려고 상속하지 않는다. 그럴 땐 필드로 가진다(연관).

## 5. 다형성: 오버라이딩과 인터페이스

```java
interface GradePolicy {
    Letter toLetter(int score, GradeTable table);
}

class AbsolutePolicy implements GradePolicy {
    @Override
    public Letter toLetter(int score, GradeTable table) {
        return table.lookup(score);      // 하한 기준표 조회
    }
}

class RelativePolicy implements GradePolicy {
    @Override
    public Letter toLetter(int score, GradeTable table) {
        return table.byRank(score);      // 분포 기준
    }
}

// 사용하는 쪽은 어떤 정책인지 모른다
class GradeService {
    private GradePolicy policy;
    GradeService(GradePolicy policy) { this.policy = policy; }

    Letter grade(int score, GradeTable table) {
        return policy.toLetter(score, table);   // 같은 메시지, 다른 동작
    }
}
```

`if (course.isRelative()) … else …` 분기가 세 곳 이상 반복되면 이렇게 바꾼다. 새 정책(패스/페일)을 추가해도 GradeService는 바뀌지 않는다.

## 6. 추상 클래스 vs 인터페이스

| 구분 | 추상 클래스 | 인터페이스 |
|------|------------|-----------|
| 쓰임 | 공통 **상태와 일부 구현**을 공유하는 상위 개념 (Person) | 공통 **행위 계약**만 (GradePolicy, Notifier) |
| 상속 | 하나만 (`extends`) | 여러 개 (`implements A, B`) |
| 인스턴스 | 직접 생성 불가 | 직접 생성 불가 |
| 필드 | 가질 수 있음 | 상수만 |

"~의 한 종류"이고 공통 필드가 있으면 추상 클래스, "~할 수 있다"는 능력이면 인터페이스.

## 7. 연관의 구현

```java
class Course {
    private String courseCode;
    private int capacity;
    private int appliedCount;
    private List<Enrollment> enrollments = new ArrayList<>();   // 1:N

    public boolean hasSeat() { return appliedCount < capacity; }
    public void addEnrollment(Enrollment e) {
        enrollments.add(e);
        if (e.isConfirmed()) appliedCount++;
    }
}

class Enrollment {
    private Student student;      // N:1
    private Course course;        // N:1
    private EnrollmentStatus status;

    public void confirm() {
        if (status != EnrollmentStatus.PENDING) throw new IllegalStateException();
        status = EnrollmentStatus.CONFIRMED;
    }
    public boolean isConfirmed() { return status == EnrollmentStatus.CONFIRMED; }
}
```

- 상태 변화는 상태도의 전이와 1:1로 메소드가 된다. `confirm()`, `reject()`, `cancel()`.
- 다른 클래스가 `enrollment.status = CONFIRMED`로 직접 쓰지 못하게 한다(내용 결합 방지). 반드시 `confirm()`을 거친다.

## 8. 패키지

```
kr.ac.oo.academic   Student, StudentHistory, StudentService
kr.ac.oo.grade      Grade, GradeHistory, Appeal, GradeService, GradePolicy, AbsolutePolicy, RelativePolicy
kr.ac.oo.enrollment Course, Enrollment, EnrollmentService
kr.ac.oo.admin      Setting, Notice, SettingService, NoticeService
kr.ac.oo.repo       StudentRepository, GradeRepository, EnrollmentRepository, SettingRepository, NoticeRepository, LegacyStudentGateway
kr.ac.oo.web        각 Controller/Screen
```

- 패키지 밖에 공개할 클래스만 `public class`. 나머지는 패키지 전용.
- 패키지 간 import 방향이 설계서의 패키지 의존도와 같아야 한다. 다르면 설계 위반이다.

## 9. 개발 환경 (Eclipse 기준)

1. JDK 설치 → Eclipse 설치.
2. `File → New → Java Project`, 이름은 프로젝트명.
3. `src`에 패키지 생성 → 패키지에 `New → Class`. 실행 진입점에만 `public static void main(String[] args)` 체크.
4. `Ctrl + F11`로 실행, 콘솔에서 결과 확인.
5. 클래스 → 실행 클래스 → 생성자 → this → 상속 → 캡슐화 순으로 점진적으로 확장한다.

## 10. 점검표

- [ ] 모든 필드가 private
- [ ] 생성자로 필수 필드를 초기화하고 `this`로 구분
- [ ] 검증 없는 setter가 없다
- [ ] 상속은 is-a에만, 깊이 4 이하, 단일 상속
- [ ] 타입 분기 반복이 다형성으로 바뀌었다
- [ ] 상태 전이마다 대응 메소드가 있고 외부에서 상태 필드를 직접 쓰지 못한다
- [ ] import 방향이 패키지 의존도와 일치
- [ ] 클래스·메소드 이름이 설계서와 같다
