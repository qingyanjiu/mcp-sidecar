package com.hx.mcpsidecar;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ComponentScan("com.hx.gd")
@ActiveProfiles("test")
public class BaseTest {
}
