package io.hhplus.tdd.unit;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.*;

import org.apache.catalina.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;


public class PointServiceTest {

    private PointService pointService;
    private UserPointTable mockTable;
    private PointHistoryTable mockPointHistoryTable;
    private LockManager mockLockManager;
    private ReentrantReadWriteLock mockLock;

    @BeforeEach
    void setUp(){
        //기본 기능(조회, 충전, 사용, 내역조회) 외의 기능은 모두 Mock 의존성 주입
        mockTable = Mockito.mock(UserPointTable.class);
        mockPointHistoryTable = Mockito.mock(PointHistoryTable.class);
        mockLockManager = Mockito.mock(LockManager.class);
        mockLock = Mockito.mock(ReentrantReadWriteLock.class);
        pointService = new PointService(mockTable,mockPointHistoryTable, mockLockManager);

        ReentrantReadWriteLock.ReadLock readLock = Mockito.mock(ReentrantReadWriteLock.ReadLock.class);
        ReentrantReadWriteLock.WriteLock writeLock = Mockito.mock(ReentrantReadWriteLock.WriteLock.class);
        when(mockLock.readLock()).thenReturn(readLock);
        when(mockLock.writeLock()).thenReturn(writeLock);
        when(mockLockManager.getLock(anyLong())).thenReturn(mockLock);
    }

    //포인트 조회
    //존재하는 사용자의 포인트 조회
    @Test
    void testGetUserPoint_ExistingUser(){
        //Given
        Long id = 1L;
        UserPoint userPoint = new UserPoint(id, 100, System.currentTimeMillis());
        when(mockTable.selectById(id)).thenReturn(userPoint);

        //When
        UserPoint result = pointService.getUserPoint(id);

        //Then
        assertEquals(100,result.point());//기대값과 실제값이 일치한지 확인
        verify(mockTable,times(1)).selectById(id); // selectById 메서드 호출 확인
        verify(mockLockManager,times(1)).getLock(id); // LockManager 호출 확인
        verify(mockLock.readLock(),times(1)).lock(); // Read lock이 제대로 작동했는지 확인
        verify(mockLock.readLock(),times(1)).unlock(); // Read lock 해제 확인
    }

    //존재하지 않는 사용자의 포인트 조회
    @Test
    void testGetUserPoint_nonExistingUser() {
        // Given
        Long id = 2L; // 존재하지 않는 사용자 ID
        when(mockTable.selectById(id)).thenReturn(UserPoint.empty(id)); // 기본값 반환 설정

        // When
        UserPoint result = pointService.getUserPoint(id);

        // Then
        assertNotNull(result); // 반환값이 null이 아님을 확인
        assertEquals(0, result.point()); // 기본 포인트가 0임을 확인
        assertEquals(id, result.id()); // 반환된 ID가 요청한 ID와 같은지 확인

        verify(mockTable, times(1)).selectById(id); // selectById 메서드 호출 확인
        verify(mockLockManager, times(1)).getLock(id); // LockManager 호출 확인
        verify(mockLock.readLock(), times(1)).lock(); // Read lock이 제대로 작동했는지 확인
        verify(mockLock.readLock(), times(1)).unlock(); // Read lock 해제 확인
    }

    //포인트 적립
    //허용되는 포인트 충전 검증
    @Test
    void testChargePoints_validAmount() {
        // Given
        Long id = 1L;
        long initialAmount = 5000L;
        long chargeAmount = 10000L;

        UserPoint currentPoint = new UserPoint(id, initialAmount, System.currentTimeMillis());
        when(mockTable.selectById(id)).thenReturn(currentPoint);

        // When
        pointService.chargePoints(id, chargeAmount);

        // Then
        verify(mockTable, times(1)).insertOrUpdate(id, initialAmount + chargeAmount);
        verify(mockPointHistoryTable, times(1)).insert(eq(id), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    //허용되지 않는 금액 충전 검증
    @Test
    void testChargePoints_invalidAmount() {
        // Given
        Long id = 1L;
        long chargeAmount = 123L; // 허용되지 않는 금액

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> pointService.chargePoints(id, chargeAmount));

        assertEquals("허용되지 않는 포인트 금액입니다.", exception.getMessage());
        //사용자 포인트 데이터에 영향을 주진 않았는지 검증
        verifyNoInteractions(mockTable);
        //사용자 포인트 충전 및 사용 내역 데이터에 영향을 주진 않았는지 검증
        verifyNoInteractions(mockPointHistoryTable);
    }

    //사용자 보유 가능한 최대 포인트 초과시 검증
    @Test
    void testChargePoints_exceedMaxPoints() {
        // Given
        Long id = 1L;
        long initialAmount = 95000L;
        long chargeAmount = 10000L;

        UserPoint currentPoint = new UserPoint(id, initialAmount, System.currentTimeMillis());
        when(mockTable.selectById(id)).thenReturn(currentPoint);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> pointService.chargePoints(id, chargeAmount));

        assertEquals("사용자가 보유할 수 있는 최대 포인트를 초과했습니다.", exception.getMessage());
        verifyNoInteractions(mockPointHistoryTable);
    }

    //포인트 사용
    //허용되는 포인트 사용 검증
    @Test
    void testUsePoints_validAmount(){
        //Given
        Long id = 1L;
        long initialAmount = 5000L;
        long useAmount = 300L;

        UserPoint currentPoint = new UserPoint(id, initialAmount,System.currentTimeMillis());
        when(mockTable.selectById(id)).thenReturn(currentPoint);

        //When
        pointService.usePoints(id,useAmount);

        //Then
        verify(mockTable,times(1)).insertOrUpdate(id,initialAmount-useAmount);
        verify(mockPointHistoryTable,times(1)).insert(eq(id),eq(useAmount),eq(TransactionType.USE),anyLong());
    }

    //허용되지 않는 포인트 사용 검증
    @Test
    void testUsePoints_invalidAmount(){
        //Given
        Long id = 1L;
        long initialAmount = 5000L;
        long useAmount = 600L;

        UserPoint currentPoint = new UserPoint(id, initialAmount,System.currentTimeMillis());
        when(mockTable.selectById(id)).thenReturn(currentPoint);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> pointService.usePoints(id,useAmount));

        assertEquals("허용되지 않는 포인트 금액입니다.", exception.getMessage());
        verifyNoInteractions(mockPointHistoryTable);
    }

    //보유한 포인트보다 더 많이 사용할 수 없음
    @Test
    void testUsePoints_exceedOwningPoints(){
        //Given
        Long id = 1L;
        long initialAmount = 100L;
        long useAmount = 300L;

        UserPoint currentPoint = new UserPoint(id,initialAmount,System.currentTimeMillis());
        when(mockTable.selectById(id)).thenReturn(currentPoint);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> pointService.usePoints(id,useAmount));

        assertEquals("사용자가 보유한 포인트를 초과해서 사용할 수 없습니다.", exception.getMessage());
        verifyNoInteractions(mockPointHistoryTable);
    }

    //포인트 사용 내역 조회
    //포인트 사용 내역이 있을 때
    @Test
    void testGetUserHistory(){
        //Given
        Long id = 1L;
        PointHistory chargeHistory = new PointHistory(1,id,100,TransactionType.CHARGE,System.currentTimeMillis());
        PointHistory useHistory = new PointHistory(2,id,200,TransactionType.USE,System.currentTimeMillis());
        List<PointHistory> mockHistory = List.of(chargeHistory,useHistory);

        when(mockPointHistoryTable.selectAllByUserId(id)).thenReturn(mockHistory);

        //When
        List<PointHistory> result = pointService.getUserPointHistory(id,0,10);

        //Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(chargeHistory,result.get(0));
        assertEquals(useHistory,result.get(1));

        verify(mockPointHistoryTable,times(1)).selectAllByUserId(id);

    }

    //포인트 사용 내역이 없을 때
    @Test
    void testGetPointHistory_noHistory(){
        // Given
        Long id = 2L;
        when(mockPointHistoryTable.selectAllByUserId(id)).thenReturn(List.of());

        // When
        List<PointHistory> result = pointService.getUserPointHistory(id,0,10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(mockPointHistoryTable, times(1)).selectAllByUserId(id);
    }
}
