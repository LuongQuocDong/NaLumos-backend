package vn.fs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NaLumosBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(NaLumosBackendApplication.class, args);
	}

}
