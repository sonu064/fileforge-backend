package in.bushansirgur.cloudshareapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CloudshareapiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudshareapiApplication.class, args);
	}

}
