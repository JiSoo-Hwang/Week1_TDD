package io.hhplus.tdd.integration;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.LockManager;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

//Service 안의 기능 간 통합 테스트
public class PointUnitIntegrationTest {
    private final PointService pointService = new PointService(new UserPointTable(), new PointHistoryTable(), new LockManager());

    //멀티스레드 동작과 결과 검증
    @Test
    void testConcurrentPointUpdates() throws InterruptedException {
        // Given: 사용자 ID와 초기 포인트 설정
        Long id = 1L;
        pointService.chargePoints(id, 10000); // 초기 포인트 10000 충전

        // 스레드에서 실행할 작업 정의
        Runnable chargeTask = () -> pointService.chargePoints(id, 1000); // 1000 포인트 충전
        Runnable useTask = () -> pointService.usePoints(id, 100);        // 100 포인트 사용

        // When: 10개의 충전과 10개의 사용 작업을 병렬로 실행
        ExecutorService executor = Executors.newFixedThreadPool(5); // 5개의 스레드 풀 생성
        for (int i = 0; i < 10; i++) {
            executor.execute(chargeTask); // 충전 작업 실행
            executor.execute(useTask);   // 사용 작업 실행
        }

        executor.shutdown();                // 모든 작업 제출 완료
        executor.awaitTermination(1, TimeUnit.MINUTES); // 작업이 끝날 때까지 대기

        // Then: 최종 포인트 검증
        UserPoint finalPoint = pointService.getUserPoint(id);
        assertNotNull(finalPoint);
        assertEquals(10000 + (1000 * 10) - (100 * 10), finalPoint.point()); // 충전 10번, 사용 10번 반복
    }

    //충전과 사용 작업이 동시에 실행될 때, 최종 포인트의 데이터 무결성 검증
    @Test
    void testConcurrentChargeAndUse() throws InterruptedException {
        // Given: 초기 포인트 설정
        Long id = 1L;
        pointService.chargePoints(id, 10_000); // 10,000 충전

        // When: 충전과 사용을 병렬로 실행
        Runnable chargeTask = () -> pointService.chargePoints(id, 1_000); // 1,000 충전
        Runnable useTask = () -> pointService.usePoints(id, 300);         // 300 사용
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            executor.execute(chargeTask);
            executor.execute(useTask);
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // Then: 최종 포인트 검증
        UserPoint finalPoint = pointService.getUserPoint(id);
        assertNotNull(finalPoint);
        int expected = 10_000 + (1_000 * 10) - (300 * 10); // 초기값 + 충전 - 사용
        assertEquals(expected, finalPoint.point());
    }

    //포인트 내역이 갱신 시간 기준으로 올바르게 정렬되어 반환되는지 테스트
    @Test
    void testPointHistorySorting() {
        // Given: 사용자 포인트 내역 추가
        Long id = 1L;
        pointService.chargePoints(id, 1_000);
        pointService.usePoints(id, 300);
        pointService.chargePoints(id, 3_000);

        // When: 포인트 내역 조회
        List<PointHistory> histories = pointService.getUserPointHistory(id,0,10);

        // Then: 내역이 최신순으로 정렬되어 있는지 검증
        assertNotNull(histories);
        assertTrue(histories.size() > 1);
        for (int i = 1; i < histories.size(); i++) {
            assertTrue(histories.get(i - 1).updateMillis() >= histories.get(i).updateMillis());
        }
    }

    //여러 사용자 동시 작업 검증
    @Test
    void testConcurrentMultipleUsers() throws InterruptedException {
        // Given: 여러 사용자 초기화
        Long id1 = 1L;
        Long id2 = 2L;
        pointService.chargePoints(id1, 5_000);
        pointService.chargePoints(id2, 10_000);

        // When: 사용자별 병렬 작업 실행
        Runnable user1Task = () -> pointService.chargePoints(id1, 1_000);
        Runnable user2Task = () -> pointService.usePoints(id2, 200);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 10; i++) {
            executor.execute(user1Task);
            executor.execute(user2Task);
        }
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        // Then: 각 사용자의 포인트 독립성 확인
        UserPoint finalPoint1 = pointService.getUserPoint(id1);
        UserPoint finalPoint2 = pointService.getUserPoint(id2);
        assertEquals(5_000 + (1_000 * 10), finalPoint1.point());
        assertEquals(10_000 - (200 * 10), finalPoint2.point());
    }


}
