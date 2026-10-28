package archfixture;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** ArchUnit 음성 시험 전용(규칙 9·10 위반). 서버 기본 패키지 밖에 두어 엔티티 탐색(@EntityScan com.a207.runkk)에 걸리지 않는다. */
@Entity
public class ArchFixtureEntityWithSetter {
    @Id private Long id;
    public String name;

    protected ArchFixtureEntityWithSetter() {}

    public void setName(String name) {
        this.name = name;
    }
}
