.PHONY: build-dist clean clean-test docs run-cli test show-docs

# Make all environment variables available to child processes
.EXPORT_ALL_VARIABLES:

GRADLEW   ?= ./gradlew
NAME      := event-stream
CLI_BUILD := $(PWD)/cli/build
LIB_BUILD := $(PWD)/lib/build
HTML_DOCS := $(LIB_BUILD)/dokka/html

all: clean build

clean:
	$(GRADLEW) clean

clean-test:
	$(GRADLEW) cleanTest

build:
	$(GRADLEW) build

install: build-dist

cli/build/install/$(NAME)/bin/$(NAME):
	$(GRADLEW) installDist

build-dist: cli/build/install/$(NAME)/bin/$(NAME)

run-local:  build-dist
	@echo "*** PORT 26657 is expected to be open on localhost! ***"
	AWS_REGION=us-east-1 ENVIRONMENT=local $(CLI_BUILD)/install/$(NAME)/bin/$(NAME) $(ARGS)

run:
	./gradlew compileKotlin installDist && ./build/install/event-stream/bin/event-stream --event-stream.websocket.uri=ws://rpc-0.test.provenance.io:26657 --event-stream.rpc.uri=http://rpc-1.test.provenance.io:26657 --event-stream.height.from=1 --event-stream.height.to=100

test: clean-test
	$(GRADLEW) test -i

docs:
	$(GRADLEW) lib:dokkaHtml

show-docs: docs
	open $(HTML_DOCS)/index.html
