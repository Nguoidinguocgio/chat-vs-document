# Tương đương ops/scripts/*.ps1 cho Linux/macOS/Git Bash.
SHELL := /bin/bash
ENV := set -a; [ -f .env ] && . ./.env; set +a;

.PHONY: up down api web test lint create-user

up:
	$(ENV) docker compose -f ops/docker-compose.yml up -d --wait db

down:
	docker compose -f ops/docker-compose.yml down

api:
	$(ENV) cd api && ./mvnw -B spring-boot:run

web:
	cd web && ([ -d node_modules ] || npm ci) && npm run dev

test:
	cd web && npm test
	cd api && ./mvnw -B verify

lint:
	cd web && npm run lint && npm run typecheck
	cd api && ./mvnw -B -q spotless:check checkstyle:check

# make create-user EMAIL=a@congty.vn NAME="Nguyễn Văn A" ROLE=member PASSWORD=... [TEMP=1]
create-user:
	$(ENV) cd api && ./mvnw -B -q -DskipTests package && \
	  CLI_NAME="$(NAME)" CLI_PASSWORD="$(PASSWORD)" java -jar target/api.jar --spring.profiles.active=cli \
	  create-user --email $(EMAIL) --role $(or $(ROLE),member) $(if $(TEMP),--temp,)
