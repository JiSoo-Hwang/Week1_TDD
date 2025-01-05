package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
        if(!AllowedAmount.getAllowedValues().contains(amount)){
            throw new IllegalArgumentException("허용되지 않는 포인트 금액입니다.");
        }

        ReentrantReadWriteLock lock = lockManager.getLock(id);
        lock.writeLock().lock();
        try{
            //기존 포인트 가져오기
            UserPoint currentPoint = userPointTable.selectById(id);
            
            //포인트 충전
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

    //포인트 사용
    public void usePoints(Long id, long amount){
        //조건 1. 한 번에 100, 200, 300 포인트만 사용 가능 (네이버 쿠키 방식 차용)
        if(!AllowedAmount.getAllowedValues().contains(amount)){
            throw new IllegalArgumentException("허용되지 않는 포인트 금액입니다.");
        }

        ReentrantReadWriteLock lock = lockManager.getLock(id);
        lock.writeLock().lock();
        try{
            UserPoint currentPoint = userPointTable.selectById(id);

            //조건 2. 보유한 포인트보다 더 많이 사용할 수 없음
            if(currentPoint.point()<amount){
                throw new IllegalArgumentException("사용자가 보유한 포인트를 초과해서 사용할 수 없습니다.");
            }
            //포인트 사용
            long updatedPoint = currentPoint.point()-amount;

            //UserPointTable 업데이트
            userPointTable.insertOrUpdate(id,updatedPoint);

            //PointHistoryTable 업데이트
            pointHistoryTable.insert(id,amount,TransactionType.USE,System.currentTimeMillis());
        }finally {
            lock.writeLock().unlock();
        }

    }

    //포인트 사용 내역 조회
    public List<PointHistory>getUserPointHistory(Long id, int startIndex, int pageSize){
        return pointHistoryTable.selectAllByUserId(id).stream()
                .sorted(Comparator.comparing(PointHistory::updateMillis).reversed()) //최신순 정렬
                .skip(startIndex) //조회되는 데이터가 많을 때 페이징 처리 : 시작 인덱스 건너뜀
                .limit(pageSize) // 한 페이지 크기 제한
                .toList(); //결과를 리스트로 변환
    }

}
