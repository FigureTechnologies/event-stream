.PHONY: build-dist clean clean-test run-local test

# Make all environment variables available to child processes
.EXPORT_ALL_VARIABLES:

NAME            := provenance-event-stream
BUILD           := $(PWD)/build
GRADLEW         := ./gradlew

all: run-local

clean:
	$(GRADLEW) clean

clean-test:
	$(GRADLEW) cleanTest

build-dist:
	$(GRADLEW) installDist

run-local:  build-dist
	AWS_REGION=us-east-1 ENVIRONMENT=local $(BUILD)/install/$(NAME)/bin/$(NAME) $(ARGS)

run:
	./gradlew compileKotlin installDist && /home/pstory/src/event-stream/build/install/provenance-event-stream/bin/provenance-event-stream --event-stream.websocket.uri=ws://rpc-0.test.provenance.io:26657 --event-stream.rpc.uri=http://rpc-1.test.provenance.io:26657 --event-stream.height.from=1 --event-stream.height.to=100

test: clean-test
	$(GRADLEW) test -i
