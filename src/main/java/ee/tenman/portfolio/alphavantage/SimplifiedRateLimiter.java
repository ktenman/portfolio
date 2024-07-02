package ee.tenman.portfolio.alphavantage;

import com.google.common.util.concurrent.RateLimiter;

public class SimplifiedRateLimiter {
	private static final double RATE = 29.0 / 60.0; // 29 requests per minute
	private final RateLimiter rateLimiter;
	
	public SimplifiedRateLimiter() {
		this.rateLimiter = RateLimiter.create(RATE);
	}
	
	public void execute(Runnable task) {
		rateLimiter.acquire(); // This will block if necessary to ensure the rate
		task.run();
	}
}
