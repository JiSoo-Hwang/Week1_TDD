package io.hhplus.tdd.unit;

import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.LockManager;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;


public class PointServiceTest {

    private PointService pointService;
    private UserPointTable mockTable;
    private LockManager mockLockManager;
    private ReentrantReadWriteLock mockLock;

    @BeforeEach
    void setUp(){
        //기본 기능(조회, 충전, 사용, 내역조회) 외의 기능은 모두 Mock 의존성 주입
        mockTable = Mockito.mock(UserPointTable.class);
        mockLockManager = Mockito.mock(LockManager.class);
        mockLock = Mockito.mock(ReentrantReadWriteLock.class);
        pointService = new PointService(mockTable,mockLockManager);

        ReentrantReadWriteLock.ReadLock readLock = Mockito.mock(ReentrantReadWriteLock.ReadLock.class);
        ReentrantReadWriteLock.WriteLock writeLock = Mockito.mock(ReentrantReadWriteLock.WriteLock.class);
        when(mockLock.readLock()).thenReturn(readLock);
        when(mockLock.writeLock()).thenReturn(writeLock);
        when(mockLockManager.getLock(anyLong())).thenReturn(mockLock);
    }

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
}
