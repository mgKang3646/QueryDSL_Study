package study.querydsl.controller;


import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

@Profile("local") // 프로필이 local인 경우에만 동작하는 클래스
@Component
@RequiredArgsConstructor
public class InitMember {

    private final InitMemberService initMemberService;
    @PostConstruct
    public void init(){
        initMemberService.init();
    }

    @Component
    static class InitMemberService{
        @PersistenceContext
        private EntityManager em;

        @Transactional // @PostConstruct와 @Transactional 은 라이프사이클 부분 문제로 같이 쓰일 수 없으므로 클래스를 분리해주어야 한다.
        public void init(){
            Team teamA = new Team("teamA");
            Team teamB = new Team("teamB");

            em.persist(teamA);
            em.persist(teamB);

            for(int i=0;i<100;i++){
                Team selectedTeam = i%2 ==0 ?teamA:teamB;
                em.persist(new Member("member"+i,i,selectedTeam));
            }
        }
    }

}
