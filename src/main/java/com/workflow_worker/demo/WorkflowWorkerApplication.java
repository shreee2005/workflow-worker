package com.workflow_worker.demo;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRabbit
public class WorkflowWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkflowWorkerApplication.class, args);
	}

}
