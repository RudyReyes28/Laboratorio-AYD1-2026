package com.clase.pruebasintegracion;

import org.springframework.boot.SpringApplication;

public class TestPruebasIntegracionApplication {

	public static void main(String[] args) {
		SpringApplication.from(PruebasIntegracionApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
