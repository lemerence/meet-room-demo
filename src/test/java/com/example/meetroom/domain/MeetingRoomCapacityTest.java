package com.example.meetroom.domain;

import com.example.meetroom.domain.model.MeetingRoom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证参会人数必填、正整数及会议室容量边界的单元测试。 */
class MeetingRoomCapacityTest {
    /** 固定会议室测试样本，容量为六人。 */
    private final MeetingRoom room = new MeetingRoom(1L, "会议室", 6);

    /** 验证正整数且不超过容量的人数可以通过校验。 */
    @ParameterizedTest
    @ValueSource(ints = {1, 6})
    void acceptsPositiveAttendanceWithinCapacity(int count) {
        assertThatCode(() -> room.validateAttendance(count)).doesNotThrowAnyException();
    }

    /** 验证零、负数和超过容量的人数被拒绝。 */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 7})
    void rejectsAttendanceOutsideCapacity(int count) {
        assertThatThrownBy(() -> room.validateAttendance(count)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 验证未提供人数时校验失败。 */
    @Test
    void rejectsMissingAttendance() {
        assertThatThrownBy(() -> room.validateAttendance(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
