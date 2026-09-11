package com.piggymetrics.account.client;

import com.piggymetrics.account.domain.Account;
import com.piggymetrics.account.test.OutputCaptureExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.hamcrest.Matchers.containsString;

/**
 * @author cdov
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
        "feign.circuitbreaker.enabled=true"
})
public class StatisticsServiceClientFallbackTest {
    @Autowired
    private StatisticsServiceClient statisticsServiceClient;

    @RegisterExtension
    public final OutputCaptureExtension outputCapture = new OutputCaptureExtension();

    @BeforeEach
    public void setup() {
        outputCapture.reset();
    }

    @Test
    public void testUpdateStatisticsWithFailFallback(){
        statisticsServiceClient.updateStatistics("test", new Account());

        outputCapture.expect(containsString("Error during update statistics for account: test"));

    }

}
