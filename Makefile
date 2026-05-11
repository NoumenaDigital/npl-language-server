.PHONY: build test clean

# Build the fat jar with all dependencies
build:
	mvn package -Pconfig-gen -DskipTests

# Run tests
test:
	mvn test

# Clean and build
clean:
	mvn clean

# Build with tests
build-with-tests:
	mvn package -Pconfig-gen

