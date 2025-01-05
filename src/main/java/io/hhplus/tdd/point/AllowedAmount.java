package io.hhplus.tdd.point;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum AllowedAmount {
    POINT_100(100L),
    POINT_200(200L),
    POINT_300(100L),
    POINT_1000(1000L),
    POINT_3000(3000L),
    POINT_5000(5000L),
    POINT_10000(10000L),
    POINT_20000(20000L),
    POINT_30000(30000L);

    private final long value;

    AllowedAmount(long value){
        this.value = value;
    }

    public long getValue(){
        return value;
    }

    //Enum의 모든 값을 리스트로 반환
    public static List<Long>getAllowedValues(){
        return Arrays.stream(values())
                .map(AllowedAmount::getValue)
                .collect(Collectors.toList());
    }

}
