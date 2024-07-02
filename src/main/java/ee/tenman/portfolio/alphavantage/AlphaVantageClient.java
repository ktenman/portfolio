package ee.tenman.portfolio.alphavantage;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Random;

@FeignClient(name = AlphaVantageClient.CLIENT_NAME,
		url = "${alphavantage.url}",
		configuration = AlphaVantageClient.Configuration.class)
public interface AlphaVantageClient {
	
	String CLIENT_NAME = "alphaVantageClient";
	Random RANDOM = new Random();
	
	@GetMapping("/query?function={function}&symbol={symbol}")
	AlphaVantageResponse getMonthlyTimeSeries(@PathVariable String function, @PathVariable String symbol);
	
	@GetMapping("/query?function={function}&keywords={search}&page={page}")
	SearchResponse getSearch(
			@PathVariable String function,
			@PathVariable String search,
			@PathVariable String page
	);
	
	@GetMapping("/query?function={function}&symbol={symbol}")
	String getString(@PathVariable String function, @PathVariable String symbol);
	
	class Configuration {
		
		private final SimplifiedRateLimiter simplifiedRateLimiter = new SimplifiedRateLimiter();
		
		@Bean
		public RequestInterceptor requestInterceptor() {
			final List<String> keys = List.of(
					"LP89F1C09XFYGW1U",
					"MNIF0F2HMV93J8TX",
					"DOSJJM3U7HKZ741I",
					"WIODF0D96B7EKTK2",
					"3919KXD313S1RX7J",
					"0MOK4N559XJ9EIDR"
			);
			
			final String randomKey = keys.get(RANDOM.nextInt(0, keys.size()));
			return template -> this.simplifiedRateLimiter.execute(() -> template.query("apikey", randomKey));
		}
	}
}
