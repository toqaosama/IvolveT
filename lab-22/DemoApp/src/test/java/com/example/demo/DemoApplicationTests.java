package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoApplicationTests {

    @Test
    void helloReturnsGreeting() {
        assertThat(new DemoApplication().hello())
                .isEqualTo("Hello from Dockerized Spring Boot!");
    }
}
