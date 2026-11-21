package com.kvs.exam.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeatingServiceTest {

    @Test
    void aSeatSetByHandWinsOverTheRollNumber() {
        assertThat(SeatingService.effectiveSeat("B-14", 7)).isEqualTo("B-14");
    }

    @Test
    void withoutASeatTheRollNumberIsTheSeat() {
        assertThat(SeatingService.effectiveSeat(null, 7)).isEqualTo("7");
        assertThat(SeatingService.effectiveSeat("  ", 7)).isEqualTo("7");
        assertThat(SeatingService.effectiveSeat(null, null)).isNull();
    }
}
