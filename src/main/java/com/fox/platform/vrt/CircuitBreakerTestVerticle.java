package com.fox.platform.vrt;

import com.fox.platform.circuitbreaker.FoxCircuitBreakerOptions;
import com.fox.platform.vo.Endpoint;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

public class CircuitBreakerTestVerticle extends AbstractFoxVerticle {
	
	public static final String ADDRESS = "CircuitBreakerTestAddress";
	
	private static final String CIRCUIT_BREAKER_TEST_CONFIG_FIELD = "circuitBreakerTest";
	private static final String CIRCUIT_BREAKER_CONFIG_FIELD = "circuitBreaker";

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private CircuitBreaker circuitBreaker;
	
	private WebClient webClient;
	
	@Override
	public void start(Future<Void> startFuture) throws Exception {
		
		
		
		JsonObject circuitBreakerTestConfig = config().getJsonObject(CIRCUIT_BREAKER_TEST_CONFIG_FIELD, new JsonObject());
		circuitBreaker = createCircuitBreaker(circuitBreakerTestConfig.getJsonObject(CIRCUIT_BREAKER_CONFIG_FIELD, new JsonObject()));
		webClient = createWebClient();
		
		vertx.eventBus().<String>consumer(
				ADDRESS, 
				request -> 
					circuitBreaker.<JsonObject>executeWithFallback(
							future -> calltoEndpoint(future), 
							error -> replyOnCircuitOpen(error)
							).setHandler(handler -> {
								
								if(handler.succeeded()){
									JsonObject response = handler.result();
									request.reply(response);
								} else {
									request.fail(500, handler.cause().getMessage());
								}
							})
				);
		
		super.start(startFuture);
		
	}
	
	
	@Override
	public void configChange(JsonObject newConfig, JsonObject oldConfig){
		
		logger.info(super.deploymentID() + " Config Change ...");
		
		super.configChange(newConfig, oldConfig);
		
		JsonObject newVerticleConfig = newConfig.getJsonObject(CIRCUIT_BREAKER_TEST_CONFIG_FIELD, new JsonObject());
		JsonObject newCircuitBreakerConfig = newVerticleConfig.getJsonObject(CIRCUIT_BREAKER_CONFIG_FIELD, new JsonObject());
		
		JsonObject actualVericleConfig = oldConfig.getJsonObject(CIRCUIT_BREAKER_TEST_CONFIG_FIELD, new JsonObject());
		JsonObject actualCircuitBreakerConfig = actualVericleConfig.getJsonObject(CIRCUIT_BREAKER_CONFIG_FIELD, new JsonObject());
		
		//if circuit breaker config change, create a new instance of circuit breaker
		if(! actualCircuitBreakerConfig.equals(newCircuitBreakerConfig)){
			logger.info(super.deploymentID() + " Circuit Breaker Config Change ...");
			circuitBreaker.close();
			circuitBreaker = createCircuitBreaker(newCircuitBreakerConfig);
		}
				
		
		
	}
	
	private WebClient createWebClient(){	
		
		logger.info(super.deploymentID() + " Create a new WebClient with config");
		return WebClient.create(vertx);		
		
	}
	
	private CircuitBreaker createCircuitBreaker(JsonObject circuitBreakerOptions){	
		
		logger.info(super.deploymentID() + " Create a new Circuit Breaker with config: " + circuitBreakerOptions.encode());
		
		FoxCircuitBreakerOptions options = circuitBreakerOptions.mapTo(FoxCircuitBreakerOptions.class);
		CircuitBreaker newCircuitBreaker = CircuitBreaker.create("MyCircuitBreaker", vertx, options);
		if(options.isForceOpen()){
			newCircuitBreaker.open();
			newCircuitBreaker.halfOpenHandler(handler -> {
				newCircuitBreaker.open();
			});
		}
		return newCircuitBreaker;		
		
	}
	
	
	private void calltoEndpoint(Future<JsonObject> future) {
		
		Endpoint endpoint = config().getJsonObject(CIRCUIT_BREAKER_TEST_CONFIG_FIELD,new JsonObject()).getJsonObject("endpoint",new JsonObject()).mapTo(Endpoint.class);
		
		webClient.get(endpoint.getPort(),endpoint.getHost(), endpoint.getPath())
			.ssl(endpoint.isSsl())
			.send(handler -> {
				if(handler.succeeded()){
					
					JsonObject response = new JsonObject()
							.put("statusCode", handler.result().statusCode())
							.put("isError", false)
							.put("circuitBreakerStatus", circuitBreaker.state());
					future.complete(response);
				} else {
					logger.error(this.deploymentID() + " Error when call to endpoint: " + handler.cause().getMessage(), handler.cause());
					future.fail(handler.cause());
				}
			});
		
	}
	
	private JsonObject replyOnCircuitOpen(Throwable error) {
		JsonObject responseOnOpen = new JsonObject()
				.put("isError", true)
				.put("circuitBreakerStatus", circuitBreaker.state())
				.put("errorMessage", error.getMessage());
		return responseOnOpen;
	}
	
	
	

}
