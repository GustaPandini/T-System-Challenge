.PHONY: test run pricing-up pricing-down

test:
	mvn test

run:
	mvn spring-boot:run

pricing-up:
	docker compose up -d pricing-api

pricing-down:
	docker compose down
