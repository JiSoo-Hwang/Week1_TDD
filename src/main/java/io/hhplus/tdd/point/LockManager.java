package io.hhplus.tdd.point;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//동시성 제어를 담당하는 별도 컴포넌트
@Component
public class LockManager {
    // 사용자별로 읽기/쓰기 락을 관리
    private final ConcurrentHashMap<Long, ReentrantReadWriteLock> locks = new ConcurrentHashMap<>();

    public ReentrantReadWriteLock getLock(Long id){
        //키가 없을 때 값을 생성해서 추가해줌
        return locks.computeIfAbsent(id,key -> new ReentrantReadWriteLock());
    }
}
