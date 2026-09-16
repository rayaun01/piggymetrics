package com.piggymetrics.turbine;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.netflix.turbine.stream.HystrixStreamAggregator;
import org.springframework.cloud.netflix.turbine.stream.TurbineController;
import org.springframework.cloud.netflix.turbine.stream.TurbineStreamAutoConfiguration;
import org.springframework.cloud.netflix.turbine.stream.TurbineStreamClient;
import org.springframework.cloud.netflix.turbine.stream.TurbineStreamConfiguration;
import org.springframework.cloud.netflix.turbine.stream.TurbineStreamProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.test.context.junit4.SpringRunner;
import rx.subjects.PublishSubject;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TurbineStreamServiceApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	public void contextLoads() {
		Assertions.assertThat(applicationContext.getBeansOfType(TurbineStreamAutoConfiguration.class))
				.isNotEmpty();
		Assertions.assertThat(applicationContext.getBeansOfType(TurbineStreamConfiguration.class))
				.isNotEmpty();
		Assertions.assertThat(applicationContext.getBeansOfType(TurbineController.class))
				.isNotEmpty();
		Assertions.assertThat(applicationContext.getBeansOfType(HystrixStreamAggregator.class))
				.isNotEmpty();
		Assertions.assertThat(applicationContext.getBeansOfType(TurbineStreamProperties.class))
				.isNotEmpty();
		Assertions.assertThat(applicationContext.getBean("hystrixSubject", PublishSubject.class))
				.isNotNull();
		Assertions.assertThat(applicationContext.getBean(TurbineStreamClient.INPUT, SubscribableChannel.class))
				.isNotNull();

		BindingServiceProperties bindingServiceProperties =
				applicationContext.getBean(BindingServiceProperties.class);
		TurbineStreamProperties turbineStreamProperties =
				applicationContext.getBean(TurbineStreamProperties.class);
		String bindingDestination =
				bindingServiceProperties.getBindingDestination(TurbineStreamClient.INPUT);

		Assertions.assertThat(bindingDestination)
				.isNotEmpty()
				.isEqualTo(turbineStreamProperties.getDestination());
	}

}
