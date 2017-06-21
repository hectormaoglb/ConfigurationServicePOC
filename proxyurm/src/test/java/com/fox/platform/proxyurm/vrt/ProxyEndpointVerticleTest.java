package com.fox.platform.proxyurm.vrt;

import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ProxyEndpointVerticleTest {
	
	private int port;
	private Vertx vertx;
	
	@Before
	public void setUp(TestContext context) {
		
		
		try{
			ServerSocket socket = new ServerSocket(0);
			port = socket.getLocalPort();
			socket.close();
		} catch(Exception ex){
			port = 8080;
		}

		DeploymentOptions options = new DeploymentOptions()
		    .setConfig(new JsonObject().put("http.port", port)
		    );
		
		vertx = Vertx.vertx();
		vertx.deployVerticle(ProxyEndpointVerticle.class.getName(),options, context.asyncAssertSuccess());
	}

	@After
	public void tearDown(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
	}
	
	@Test
	public void testRootEndpoint(TestContext context) {
		final Async async = context.async();

		vertx.createHttpClient().getNow(port, "localhost", "/", response -> {
			context.assertEquals(response.statusCode(),404);
			async.complete();
		});
	}
	
	@Test
	public void testHasAccessEndpoint(TestContext context) {
		final Async async = context.async();

		vertx.createHttpClient().getNow(port, "localhost", ProxyEndpointVerticle.BASE_PATH + ProxyEndpointVerticle.HAS_ACCESS, response -> {
			context.assertEquals(response.statusCode(),501);
			async.complete();
		});
	}
	
}
