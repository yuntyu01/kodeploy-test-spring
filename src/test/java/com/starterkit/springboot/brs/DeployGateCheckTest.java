package com.starterkit.springboot.brs;

import static org.junit.Assert.fail;

import org.junit.Test;

// 배포 워크플로 확인용 — 테스트가 실패하면 deploy 잡이 건너뛰어지는지 본다. 다음 커밋에서 되돌린다.
public class DeployGateCheckTest {

    @Test
    public void failsOnPurpose() {
        fail("deploy gate check: this test fails on purpose");
    }
}
