package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class PointService {
    
   private final UserPointTable userPointTable;
   private final LockManager lockManager;

   public PointService(UserPointTable userPointTable, LockManager lockManager){
       this.userPointTable = userPointTable;
       this.lockManager = lockManager;
   }

    //포인트 조회
    public UserPoint getUserPoint(Long id){
        //사용자별 읽기 락을 가져옴
        ReentrantReadWriteLock lock = lockManager.getLock(id);
        lock.readLock().lock(); //읽기 락 획득
        try{
            return userPointTable.selectById(id);
        }finally{
            lock.readLock().unlock(); //읽기락 해제
        }
    }
}
