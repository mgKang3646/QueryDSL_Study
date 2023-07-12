package study.querydsl.repository;


import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;

import java.util.List;
import java.util.Optional;

import static study.querydsl.entity.QMember.*;

@Repository
@RequiredArgsConstructor
public class MemberJpaRepository {

    private final EntityManager em; // 스프링은 엔티티매니저에 프록시 가짜를 주입한다. 트랜잭션단위로 바인딩되도록 한다.그래서 멀티스레드 환경에서도 작업이 가능하다.
    private final JPAQueryFactory queryFactory;

    public void save(Member member){
        em.persist(member);
    }

    public Optional<Member> findById(Long id){
        Member findMember = em.find(Member.class,id);
        return Optional.ofNullable(findMember);
    }

    public List<Member> findAll(){
        return em.createQuery("SELECT m FROM Member m",Member.class).getResultList();
    }

    public List<Member> findAll_QueryDSL(){
        return queryFactory
                .selectFrom(member)
                .fetch();
    }

    public List<Member> findByUsername(String username){
        return em.createQuery("SELECT m FROM Member m WHERE m.username = :username",Member.class)
                .setParameter("username",username)
                .getResultList();
    }

    public List<Member> findByUsername_QueryDsl(String username){
        return queryFactory
                .selectFrom(member)
                .where(member.username.eq(username)) // Setparameter 없어도 자동으로 파라미터 등록을 함
                .fetch();
    }
}
