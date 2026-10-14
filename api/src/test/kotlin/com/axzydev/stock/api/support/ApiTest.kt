package com.axzydev.stock.api.support

import com.axzydev.stock.api.TestcontainersConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import

// Base of the end-to-end tests: the API runs on a random port against a real PostgreSQL, and the tests call it over HTTP.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
abstract class ApiTest {

	@LocalServerPort
	var port: Int = 0

	val api: Api get() = Api(port)
}
