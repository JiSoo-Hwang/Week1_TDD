package io.hhplus.tdd.integration;

import io.hhplus.tdd.point.PointController;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class PointIntegrationTest {

    @Autowired
    private PointController pointController;

    //포인트 충전 및 조회 통합 테스트
    @Test
    void testChargeAndGetPoints() {
        // Given: 초기 포인트 설정
        Long id = 1L;

        // When: 포인트 충전
        pointController.charge(id, 1000); // 1000 포인트 충전
        pointController.charge(id, 3000); // 3000 포인트 충전

        // Then: 포인트 조회
        UserPoint userPoint = pointController.point(id);

        assertNotNull(userPoint);
        assertEquals(4000, userPoint.point());
    }

    //충전,사용,내역조회
    void testChargeUseAndHistory() {
        // Given:
        Long id = 1L;

        // When: 포인트 충전 및 사용
        pointController.charge(id, 5000);
        pointController.use(id, 200);

        // Then: 포인트 확인
        UserPoint userPoint = pointController.point(id);
        assertEquals(3000, userPoint.point());

        // Then: 내역 확인
        List<PointHistory> history = pointController.history(id);
        assertEquals(2, history.size());
        assertEquals(TransactionType.CHARGE, history.get(0).type());
        assertEquals(TransactionType.USE, history.get(1).type());
    }
}
