package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class PointService {
    
   private final UserPointTable userPointTable;
   private final PointHistoryTable pointHistoryTable;
   private final LockManager lockManager;

   public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable, LockManager lockManager){
       this.userPointTable = userPointTable;
       this.lockManager = lockManager;
       this.pointHistoryTable = pointHistoryTable;
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

    //포인트 충전
    public void chargePoints(Long id, long amount){

        //조건 1 : 적립 가능한 포인트 금액 검증
        List<Long> allowedAmounts = List.of(100L,1000L,3000L,5000L,10000L,20000L,30000L);
        if(!allowedAmounts.contains(amount)){
            throw new IllegalArgumentException("허용되지 않는 포인트 금액입니다.");
        }

        ReentrantReadWriteLock lock = lockManager.getLock(id);
        lock.writeLock().lock();
        try{
            //기존 포인트 가져오기
            UserPoint currentPoint = userPointTable.selectById(id);
            long updatedPoint = currentPoint.point() + amount;

            //조건 2 : 최대 보유 가능 포인트 검증
            if(updatedPoint > 100000L){
                throw new IllegalArgumentException("사용자가 보유할 수 있는 최대 포인트를 초과했습니다.");
            }

            //UserPointTable 업데이트
            userPointTable.insertOrUpdate(id,updatedPoint);

            //PointHistoryTable에 내역 저장
            pointHistoryTable.insert(id,amount,TransactionType.CHARGE,System.currentTimeMillis());
        }finally {
            lock.writeLock().unlock();
        }
    }
}
